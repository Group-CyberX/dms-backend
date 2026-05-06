package com.dms.service;

import com.dms.constants.WorkflowConstants;
import com.dms.dao.WorkflowInstanceRepository;
import com.dms.dao.WorkflowTaskRepository;
import com.dms.dao.WorkflowTemplateRepository;
import com.dms.dto.WorkflowTaskActionRequest;
import com.dms.models.WorkflowInstance;
import com.dms.models.WorkflowTask;
import com.dms.models.WorkflowTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkflowTaskService {

    private final WorkflowTaskRepository taskRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowTemplateRepository templateRepo;
    private final DocumentLifecycleService documentLifecycleService;

    public WorkflowTaskService(
            WorkflowTaskRepository taskRepo,
            WorkflowInstanceRepository instanceRepo,
            WorkflowTemplateRepository templateRepo,
            DocumentLifecycleService documentLifecycleService
    ) {
        this.taskRepo = taskRepo;
        this.instanceRepo = instanceRepo;
        this.templateRepo = templateRepo;
        this.documentLifecycleService = documentLifecycleService;
    }

    public List<WorkflowTask> getTasksByInstanceId(Long instanceId) {
        return taskRepo.findByInstanceIdOrderByStepOrderAsc(instanceId);
    }

    // Approve a task with optional comment
    public WorkflowTask approveTask(Long taskId, WorkflowTaskActionRequest request) {
        // Fetch the task to approve
        WorkflowTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

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

        if (!isParallel) {
            // For sequential workflows: activate the next pending step
            List<WorkflowTask> allTasks = taskRepo.findByInstanceIdOrderByStepOrderAsc(instance.getId());

            for (WorkflowTask nextTask : allTasks) {
                if (WorkflowConstants.TASK_PENDING.equals(nextTask.getStatus())) {
                    nextTask.setStatus(WorkflowConstants.TASK_ACTIVE);
                    taskRepo.save(nextTask);
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
