package com.dms.service;

import com.dms.constants.WorkflowConstants;
import com.dms.dao.AuditLogRepository;
import com.dms.dao.DocumentRepository;
import com.dms.dao.ErpConnectionRepository;
import com.dms.dao.NotificationRepository;
import com.dms.dao.DashboardCountsRepository;
import com.dms.dao.UserRepository;
import com.dms.dao.WorkflowInstanceRepository;
import com.dms.dao.WorkflowTaskRepository;
import com.dms.dto.DashboardSummary;
import com.dms.models.Documents;
import com.dms.models.User;
import com.dms.models.WorkflowInstance;
import com.dms.models.WorkflowTask;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the dashboard in a fixed number of queries.
 *
 * The rule followed throughout: counting happens in the database, and a list is
 * only loaded when its rows are actually shown. Nothing here scales with the
 * number of workflows in the system.
 */
@Service
public class DashboardService {

    /** How near a due date has to be before the panel raises it. */
    private static final int SLA_WINDOW_DAYS = 2;

    private static final Collection<String> RUNNING_WORKFLOW_STATUSES =
            List.of("ACTIVE", "PENDING", "PENDING_APPROVAL", "IN_PROGRESS");

    private static final Collection<String> OPEN_TASK_STATUSES =
            List.of(WorkflowConstants.TASK_ACTIVE, WorkflowConstants.TASK_PENDING);

    private final DashboardCountsRepository dashboardCountsRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowTaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final ErpConnectionRepository erpConnectionRepository;

    public DashboardService(UserRepository userRepository,
                            DocumentRepository documentRepository,
                            WorkflowInstanceRepository instanceRepository,
                            WorkflowTaskRepository taskRepository,
                            NotificationRepository notificationRepository,
                            AuditLogRepository auditLogRepository,
                            ErpConnectionRepository erpConnectionRepository,
                            DashboardCountsRepository dashboardCountsRepository) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.erpConnectionRepository = erpConnectionRepository;
        this.dashboardCountsRepository = dashboardCountsRepository;
    }

    public DashboardSummary summarise(User caller) {
        UUID userId = caller.getUserId();
        String roleName = caller.getRole() != null ? String.valueOf(caller.getRole().getName()) : "";

        // A task may be addressed to a person or to a role, so both spellings
        // count as "waiting on me" - the same rule the My Tasks screen uses.
        List<String> assignees = List.of(String.valueOf(userId), roleName);

        // All twelve figures in a single round trip. Asked for one at a time
        // they were a dozen separate trips to a database that is not on this
        // machine, which is where the panel spent nearly all of its time.
        DashboardCountsRepository.DashboardCounts counts = dashboardCountsRepository.loadAll(
                userId,
                String.valueOf(userId),
                upper(RUNNING_WORKFLOW_STATUSES),
                upper(OPEN_TASK_STATUSES),
                upper(assignees));

        return new DashboardSummary(
                value(counts == null ? null : counts.getUserCount()),
                value(counts == null ? null : counts.getActiveDocuments()),
                value(counts == null ? null : counts.getMyDocuments()),
                value(counts == null ? null : counts.getDeletedDocuments()),
                value(counts == null ? null : counts.getRunningWorkflows()),
                value(counts == null ? null : counts.getApprovedWorkflows()),
                value(counts == null ? null : counts.getMyWorkflows()),
                value(counts == null ? null : counts.getMyOpenTasks()),
                value(counts == null ? null : counts.getUnreadNotifications()),
                value(counts == null ? null : counts.getErpConnections()),
                value(counts == null ? null : counts.getAuditEntries()),
                value(counts == null ? null : counts.getFailedAuditEntries()),
                slaAlerts(userId, roleName));
    }

    private static long value(Long count) {
        return count == null ? 0L : count;
    }

    /** The query compares in upper case, so the values it is given must match. */
    private static Collection<String> upper(Collection<String> values) {
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(v -> v.toUpperCase())
                .toList();
    }

    /**
     * Approvals falling due, for this caller only.
     *
     * Four queries in total, whatever the size of the system: the workflows in
     * the date window, their tasks, the documents those workflows point at, and
     * the people who raised them.
     */
    private List<DashboardSummary.SlaAlert> slaAlerts(UUID userId, String roleName) {
        LocalDate today = LocalDate.now();

        List<WorkflowInstance> dueSoon = instanceRepository.findByStatusIgnoreCaseInAndDueDateBetween(
                RUNNING_WORKFLOW_STATUSES,
                today.minusDays(SLA_WINDOW_DAYS),
                today.plusDays(SLA_WINDOW_DAYS));

        if (dueSoon.isEmpty()) {
            return List.of();
        }

        Map<Long, WorkflowInstance> byInstanceId = dueSoon.stream()
                .collect(Collectors.toMap(WorkflowInstance::getId, w -> w, (a, b) -> a));

        // One query for every task across every one of those workflows. This
        // single line is what replaced the per-workflow request loop.
        List<WorkflowTask> tasks = taskRepository.findByInstanceIdIn(byInstanceId.keySet()).stream()
                .filter(t -> isOpen(t.getStatus()))
                .filter(t -> isAssignedTo(t.getUserId(), userId, roleName))
                .toList();

        if (tasks.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> titles = documentTitles(tasks, byInstanceId);
        Map<String, String> creators = creatorLabels(tasks, byInstanceId);

        List<DashboardSummary.SlaAlert> alerts = new ArrayList<>();
        for (WorkflowTask task : tasks) {
            WorkflowInstance workflow = byInstanceId.get(task.getInstanceId());
            long daysRemaining = ChronoUnit.DAYS.between(today, workflow.getDueDate());
            UUID documentId = parseUuid(workflow.getDocumentId());

            alerts.add(new DashboardSummary.SlaAlert(
                    task.getId(),
                    workflow.getDocumentId(),
                    documentId != null ? titles.getOrDefault(documentId, "Untitled Document") : "Untitled Document",
                    workflow.getWorkflowName(),
                    workflow.getPriority(),
                    creators.getOrDefault(String.valueOf(workflow.getCreatedByUserId()), "Unknown"),
                    workflow.getDueDate(),
                    daysRemaining,
                    daysRemaining < 0));
        }

        // Most urgent first, so the panel reads top-down.
        alerts.sort((a, b) -> Long.compare(a.daysRemaining(), b.daysRemaining()));
        return alerts;
    }

    private Map<UUID, String> documentTitles(List<WorkflowTask> tasks, Map<Long, WorkflowInstance> byInstanceId) {
        Set<UUID> documentIds = tasks.stream()
                .map(t -> parseUuid(byInstanceId.get(t.getInstanceId()).getDocumentId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (documentIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> titles = new HashMap<>();
        for (Documents doc : documentRepository.findAllByIdIn(documentIds)) {
            titles.put(doc.getDocument_id(), doc.getTitle());
        }
        return titles;
    }

    private Map<String, String> creatorLabels(List<WorkflowTask> tasks, Map<Long, WorkflowInstance> byInstanceId) {
        Set<UUID> creatorIds = tasks.stream()
                .map(t -> parseUuid(byInstanceId.get(t.getInstanceId()).getCreatedByUserId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (creatorIds.isEmpty()) {
            return Map.of();
        }

        Map<String, String> labels = new HashMap<>();
        for (User user : userRepository.findAllById(creatorIds)) {
            String role = user.getRole() != null ? String.valueOf(user.getRole().getName()) : "";
            labels.put(String.valueOf(user.getUserId()),
                    role.isBlank() ? user.getUsername() : user.getUsername() + " (" + role + ")");
        }
        return labels;
    }

    private boolean isOpen(String status) {
        return status != null && OPEN_TASK_STATUSES.stream().anyMatch(s -> s.equalsIgnoreCase(status.trim()));
    }

    private boolean isAssignedTo(String assignee, UUID userId, String roleName) {
        if (assignee == null) {
            return false;
        }
        String value = assignee.trim();
        return value.equalsIgnoreCase(String.valueOf(userId))
                || (!roleName.isBlank() && value.equalsIgnoreCase(roleName.trim()));
    }

    /**
     * Workflow rows hold the document id as free text, and older rows hold
     * placeholders such as TEMP_USER, so a bad value must not take the whole
     * dashboard down with it.
     */
    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
