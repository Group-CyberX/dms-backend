package com.dms.service;

import com.dms.constants.WorkflowConstants;
import com.dms.dao.UserRepository;
import com.dms.dao.WorkflowInstanceRepository;
import com.dms.dao.WorkflowTaskRepository;
import com.dms.models.User;
import com.dms.models.WorkflowInstance;
import com.dms.models.WorkflowTask;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tells people when an approval they are holding has passed its due date.
 *
 * The dashboard has always drawn an SLA panel, but nothing ever told the
 * approver - they had to open the dashboard to find out they were late, which
 * is the one thing a late person has not done. This closes that: the day a
 * workflow's due date passes, whoever it is waiting on gets a notification, and
 * the breach is recorded in the audit trail.
 *
 * It fires once per workflow, on the morning after the due date. Anything
 * already overdue before that stays quiet rather than sending a burst of
 * notifications for history nobody can act on retrospectively.
 */
@Service
public class SlaMonitorService {

    private static final List<String> RUNNING_STATUSES =
            List.of("ACTIVE", "PENDING", "PENDING_APPROVAL", "IN_PROGRESS");

    private static final List<String> OPEN_TASK_STATUSES =
            List.of(WorkflowConstants.TASK_ACTIVE, WorkflowConstants.TASK_PENDING);

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public SlaMonitorService(WorkflowInstanceRepository instanceRepository,
                             WorkflowTaskRepository taskRepository,
                             UserRepository userRepository,
                             NotificationService notificationService,
                             AuditLogService auditLogService) {
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    /** 08:00 every day. */
    @Scheduled(cron = "0 0 8 * * *")
    public void notifyBreachedApprovals() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Only workflows that became overdue yesterday, so each one is reported
        // exactly once however long it then stays late.
        List<WorkflowInstance> justBreached = instanceRepository
                .findByStatusIgnoreCaseInAndDueDateBetween(RUNNING_STATUSES, yesterday, yesterday);

        if (justBreached.isEmpty()) {
            return;
        }

        Map<Long, WorkflowInstance> byId = justBreached.stream()
                .collect(Collectors.toMap(WorkflowInstance::getId, w -> w, (a, b) -> a));

        for (WorkflowTask task : taskRepository.findByInstanceIdIn(byId.keySet())) {
            if (task.getStatus() == null
                    || OPEN_TASK_STATUSES.stream().noneMatch(s -> s.equalsIgnoreCase(task.getStatus().trim()))) {
                continue;   // already dealt with
            }

            WorkflowInstance workflow = byId.get(task.getInstanceId());
            String message = "'" + workflow.getWorkflowName()
                    + "' passed its due date on " + workflow.getDueDate() + " and is still waiting on you.";

            notifyAssignee(task.getUserId(), message);

            auditLogService.tryRecord("SLA_BREACHED", null, null, null, "FAILED",
                    "'" + workflow.getWorkflowName() + "' overdue since " + workflow.getDueDate());
        }
    }

    /**
     * A step is addressed either to a person or to a role. A role means every
     * holder of it is the one who can act, so every holder is told.
     */
    private void notifyAssignee(String assignee, String message) {
        if (assignee == null || assignee.isBlank()) {
            return;
        }

        try {
            notificationService.sendNotification(UUID.fromString(assignee.trim()), message);
            return;
        } catch (IllegalArgumentException ignored) {
            // Not a user id, so treat it as a role name.
        }

        for (User user : userRepository.findAll()) {
            if (user.getRole() != null
                    && assignee.trim().equalsIgnoreCase(String.valueOf(user.getRole().getName()))) {
                notificationService.sendNotification(user.getUserId(), message);
            }
        }
    }
}
