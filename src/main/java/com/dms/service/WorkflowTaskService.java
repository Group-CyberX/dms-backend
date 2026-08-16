package com.dms.service;

import com.dms.constants.WorkflowConstants;
import com.dms.dao.WorkflowInstanceRepository;
import com.dms.dao.WorkflowTaskRepository;
import com.dms.dao.UserRepository;
import com.dms.dao.WorkflowTemplateRepository;
import com.dms.dto.TaskContextResponse;
import com.dms.dto.TaskSigningContextResponse;
import com.dms.dto.WorkflowTaskActionRequest;
import com.dms.exceptions.ResourceNotFoundException;
import com.dms.models.User;
import com.dms.models.WorkflowInstance;
import com.dms.models.WorkflowTask;
import com.dms.models.WorkflowTemplate;
import com.dms.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowTaskService {

    private final WorkflowTaskRepository taskRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowTemplateRepository templateRepo;
    private final DocumentLifecycleService documentLifecycleService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public WorkflowTaskService(
            WorkflowTaskRepository taskRepo,
            WorkflowInstanceRepository instanceRepo,
            WorkflowTemplateRepository templateRepo,
            DocumentLifecycleService documentLifecycleService,
            AuditLogService auditLogService,
            NotificationService notificationService,
            UserRepository userRepository
    ) {
        this.taskRepo = taskRepo;
        this.instanceRepo = instanceRepo;
        this.templateRepo = templateRepo;
        this.documentLifecycleService = documentLifecycleService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    // ---- Audit and notification helpers ---------------------------------

    /**
     * Tells whoever raised the workflow what happened to it.
     *
     * The creator id is stored as text and older rows hold placeholders such as
     * TEMP_USER, so a value that is not a real user id is simply skipped rather
     * than failing the approval.
     */
    private void notifyWorkflowCreator(WorkflowInstance instance, String message) {
        String creatorId = instance.getCreatedByUserId();
        if (creatorId == null || creatorId.isBlank()) {
            return;
        }
        try {
            notificationService.sendNotification(UUID.fromString(creatorId.trim()), message);
        } catch (IllegalArgumentException ignored) {
            // Not a user id - nothing to notify.
        }
    }

    /**
     * Tells the next approver that a step is now waiting on them.
     *
     * A step may be addressed to a role rather than to a person, in which case
     * everyone holding that role is told.
     */
    private void notifyAssignee(WorkflowTask task, String message) {
        String assignee = task.getUserId();
        if (assignee == null || assignee.isBlank()) {
            return;
        }

        try {
            notificationService.sendNotification(UUID.fromString(assignee.trim()), message);
            return;
        } catch (IllegalArgumentException ignored) {
            // Not a user id, so treat it as a role name below.
        }

        userRepository.findAll().stream()
                .filter(u -> u.getRole() != null
                        && assignee.trim().equalsIgnoreCase(String.valueOf(u.getRole().getName())))
                .forEach(u -> notificationService.sendNotification(u.getUserId(), message));
    }

    /** Documents are identified by text on the instance; parse defensively. */
    private UUID documentIdOf(WorkflowInstance instance) {
        try {
            return instance.getDocumentId() == null ? null : UUID.fromString(instance.getDocumentId().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public List<WorkflowTask> getTasksByInstanceId(Long instanceId) {
        return taskRepo.findByInstanceIdOrderByStepOrderAsc(instanceId);
    }

    /**
     * A task may only be actioned by the person it is waiting on.
     *
     * WorkflowTask.userId holds either a user id or a role name, depending on
     * how the step was defined, so both are accepted - this is the same rule the
     * My Tasks screen uses to decide what to show. A System Administrator may
     * act on any task; the audit row still records who really did it.
     */
    private void assertAssignedToCaller(WorkflowTask task) {
        User caller = SecurityUtils.currentUser();

        boolean isSystemAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_SYSTEM_ADMIN".equals(a.getAuthority()));
        if (isSystemAdmin) {
            return;
        }

        String assignee = task.getUserId() == null ? "" : task.getUserId().trim();
        boolean byId = assignee.equalsIgnoreCase(String.valueOf(caller.getUserId()));
        boolean byRole = caller.getRole() != null
                && assignee.equalsIgnoreCase(String.valueOf(caller.getRole().getName()).trim());

        if (!byId && !byRole) {
            throw new AccessDeniedException("This approval step is not assigned to you.");
        }
    }

    /**
     * Resolves, for a single task, whether the approver has to place a signature
     * and which document they would be signing. Called by the UI the moment
     * "Approve" is clicked so it knows whether to open the signing page.
     */
    public TaskSigningContextResponse getSigningContext(Long taskId) {
        WorkflowTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        WorkflowInstance instance = instanceRepo.findById(task.getInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Workflow instance not found for task " + taskId));

        // A missing template is not fatal here - it just means "no signature required".
        // The instance carries the decision. Workflows created before that column
        // existed fall back to their template, so old data still behaves sensibly.
        boolean requiresSignature = instance.requiresSignatureOrFalse();
        if (!requiresSignature && instance.getRequiresSignature() == null && instance.getTemplateId() != null) {
            requiresSignature = templateRepo.findById(instance.getTemplateId())
                    .map(WorkflowTemplate::requiresSignatureOrFalse)
                    .orElse(false);
        }

        return new TaskSigningContextResponse(
                task.getId(),
                instance.getId(),
                instance.getDocumentId(),
                instance.getWorkflowName(),
                requiresSignature
        );
    }

    /**
     * The full context for one task, in a single query pair.
     *
     * This replaces the document page's own reconstruction of the same facts,
     * which cost one request per workflow on the document plus a lookup of the
     * current user.
     */
    public TaskContextResponse getTaskContext(Long taskId) {
        WorkflowTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        WorkflowInstance instance = instanceRepo.findById(task.getInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Workflow instance not found for task " + taskId));

        User caller = SecurityUtils.currentUser();
        boolean assignedToMe = isAssignedTo(task.getUserId(), caller.getUserId(),
                caller.getRole() != null ? String.valueOf(caller.getRole().getName()) : "");

        String taskStatus = task.getStatus() == null ? "" : task.getStatus().toUpperCase();
        String workflowStatus = instance.getStatus() == null ? "" : instance.getStatus().toUpperCase();

        boolean overdue = instance.getDueDate() != null
                && !WorkflowConstants.WORKFLOW_APPROVED.equalsIgnoreCase(workflowStatus)
                && !WorkflowConstants.WORKFLOW_REJECTED.equalsIgnoreCase(workflowStatus)
                && instance.getDueDate().isBefore(LocalDate.now());

        // Same precedence the page used, kept in one place so the banner and the
        // rule that actually blocks the action cannot drift apart.
        String message = null;
        if (WorkflowConstants.WORKFLOW_REJECTED.equalsIgnoreCase(workflowStatus)) {
            message = "Workflow rejected";
        } else if (overdue) {
            message = "Due date expired";
        } else if (WorkflowConstants.TASK_PENDING.equalsIgnoreCase(taskStatus)) {
            message = "Waiting for previous step";
        } else if (!assignedToMe) {
            message = "This step is assigned to someone else";
        } else if (WorkflowConstants.TASK_APPROVED.equalsIgnoreCase(taskStatus)) {
            message = "Task approved";
        } else if (WorkflowConstants.TASK_REJECTED.equalsIgnoreCase(taskStatus)) {
            message = "Task rejected";
        }

        return new TaskContextResponse(
                task.getId(),
                instance.getId(),
                instance.getDocumentId(),
                instance.getWorkflowName(),
                getSigningContext(taskId).requiresSignature(),
                task.getStatus(),
                instance.getStatus(),
                instance.getDueDate(),
                assignedToMe,
                overdue,
                message);
    }

    /** Overload used by the task-context path, where the ids are already known. */
    private boolean isAssignedTo(String assignee, UUID userId, String roleName) {
        if (assignee == null) {
            return false;
        }
        String value = assignee.trim();
        return value.equalsIgnoreCase(String.valueOf(userId))
                || (!roleName.isBlank() && value.equalsIgnoreCase(roleName.trim()));
    }

    // Approve a task with optional comment
    public WorkflowTask approveTask(Long taskId, WorkflowTaskActionRequest request) {
        // Fetch the task to approve
        WorkflowTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        assertAssignedToCaller(task);

        // Get the workflow instance
        WorkflowInstance instance = instanceRepo.findById(task.getInstanceId())
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));

        // If workflow is already rejected, no further approvals allowed
        if (WorkflowConstants.WORKFLOW_REJECTED.equals(instance.getStatus())) {
            throw new RuntimeException("Cannot approve task: workflow has been rejected");
        }

        if (WorkflowConstants.TASK_APPROVED.equals(task.getStatus())) {
            return task;
        }

        // If task is already rejected, it cannot be approved
        if (WorkflowConstants.TASK_REJECTED.equals(task.getStatus())) {
            throw new RuntimeException("Rejected task cannot be approved");
        }

        // Determine workflow type: prefer instance.workflowType, fall back to template if needed
        String workflowType = instance.getWorkflowType();
        if ((workflowType == null || workflowType.isBlank()) && instance.getTemplateId() != null) {
            WorkflowTemplate template = templateRepo.findById(instance.getTemplateId())
                    .orElse(null);
            if (template != null) {
                workflowType = template.getWorkflowType();
            }
        }

        boolean isParallel = WorkflowConstants.WORKFLOW_TYPE_PARALLEL.equalsIgnoreCase(workflowType);

        // For sequential workflows, only ACTIVE step may be approved
        if (!isParallel) {
            if (!WorkflowConstants.TASK_ACTIVE.equals(task.getStatus())) {
                throw new RuntimeException("Only the active step can be approved. Current status: " + task.getStatus());
            }
        }

        // Approve the task
        task.setStatus(WorkflowConstants.TASK_APPROVED);
        task.setActionComment(normalizeComment(request));
        task = taskRepo.save(task);

        UUID documentId = documentIdOf(instance);
        auditLogService.tryRecord("WORKFLOW_TASK_APPROVED", SecurityUtils.currentUserId(),
                documentId, null, "SUCCESS");
        notifyWorkflowCreator(instance,
                "Step " + task.getStepOrder() + " of '" + instance.getWorkflowName() + "' was approved");

        if (!isParallel) {
            // For sequential workflows: activate the next pending step
            List<WorkflowTask> allTasks = taskRepo.findByInstanceIdOrderByStepOrderAsc(instance.getId());

            for (WorkflowTask nextTask : allTasks) {
                if (WorkflowConstants.TASK_PENDING.equals(nextTask.getStatus())) {
                    nextTask.setStatus(WorkflowConstants.TASK_ACTIVE);
                    taskRepo.save(nextTask);

                    // The step is now waiting on someone: tell them, rather than
                    // relying on them to check the workflow list.
                    notifyAssignee(nextTask,
                            "'" + instance.getWorkflowName() + "' requires your approval");
                    break; // Only activate the first pending task
                }
            }
        }

        // Check if all tasks in the workflow instance are approved
        List<WorkflowTask> updatedTasks = taskRepo.findByInstanceIdOrderByStepOrderAsc(instance.getId());

        boolean allApproved = updatedTasks.stream()
                .allMatch(t -> WorkflowConstants.TASK_APPROVED.equals(t.getStatus()));

        // If all tasks are approved, update workflow instance and document status
        if (allApproved) {
            instance.setStatus(WorkflowConstants.WORKFLOW_APPROVED);
            instanceRepo.save(instance);

            documentLifecycleService.updateDocumentStatus(
                    instance.getDocumentId(),
                    WorkflowConstants.DOCUMENT_APPROVED
            );

            auditLogService.tryRecord("WORKFLOW_APPROVED", SecurityUtils.currentUserId(),
                    documentId, null, "SUCCESS");
            notifyWorkflowCreator(instance,
                    "'" + instance.getWorkflowName() + "' has been fully approved");
        }

        return task;
    }

    public WorkflowTask approveTask(Long taskId) {
        return approveTask(taskId, null);
    }

    // Reject a task with optional comment
    public WorkflowTask rejectTask(Long taskId, WorkflowTaskActionRequest request) {
        WorkflowTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        assertAssignedToCaller(task);

        // Get the workflow instance
        WorkflowInstance instance = instanceRepo.findById(task.getInstanceId())
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));

        // If workflow is already rejected, no further actions allowed
        if (WorkflowConstants.WORKFLOW_REJECTED.equals(instance.getStatus())) {
            throw new RuntimeException("Workflow has already been rejected");
        }

        // Cannot reject already approved task
        if (WorkflowConstants.TASK_APPROVED.equals(task.getStatus())) {
            throw new RuntimeException("Approved task cannot be rejected");
        }

        // Only ACTIVE or PENDING tasks can be rejected
        if (!WorkflowConstants.TASK_ACTIVE.equals(task.getStatus()) 
                && !WorkflowConstants.TASK_PENDING.equals(task.getStatus())) {
            throw new RuntimeException("Cannot reject this task. Current status: " + task.getStatus());
        }

        // Reject the task
        task.setStatus(WorkflowConstants.TASK_REJECTED);
        task.setActionComment(normalizeComment(request));
        task = taskRepo.save(task);

        // When a task is rejected, the entire workflow instance is considered rejected
        instance.setStatus(WorkflowConstants.WORKFLOW_REJECTED);
        instanceRepo.save(instance);

        // Mark all other pending tasks as PENDING (they cannot be acted upon)
        List<WorkflowTask> allTasks = taskRepo.findByInstanceIdOrderByStepOrderAsc(instance.getId());
        for (WorkflowTask otherTask : allTasks) {
            if (!otherTask.getId().equals(taskId) && WorkflowConstants.TASK_ACTIVE.equals(otherTask.getStatus())) {
                otherTask.setStatus(WorkflowConstants.TASK_PENDING);
                taskRepo.save(otherTask);
            }
        }

        documentLifecycleService.updateDocumentStatus(
                instance.getDocumentId(),
                WorkflowConstants.DOCUMENT_REJECTED
        );

        auditLogService.tryRecord("WORKFLOW_REJECTED", SecurityUtils.currentUserId(),
                documentIdOf(instance), null, "SUCCESS");
        notifyWorkflowCreator(instance,
                "'" + instance.getWorkflowName() + "' was rejected at step " + task.getStepOrder());

        return task;
    }

    public WorkflowTask rejectTask(Long taskId) {
        return rejectTask(taskId, null);
    }

    // Clean comment
    private String normalizeComment(WorkflowTaskActionRequest request) {
        if (request == null || request.getComment() == null) {
            return null;
        }

        String trimmed = request.getComment().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
