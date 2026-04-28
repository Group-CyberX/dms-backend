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

    public WorkflowTask approveTask(Long taskId, WorkflowTaskActionRequest request) {
        WorkflowTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (WorkflowConstants.TASK_APPROVED.equals(task.getStatus())) {
            return task;
        }

        if (WorkflowConstants.TASK_REJECTED.equals(task.getStatus())) {
            throw new RuntimeException("Rejected task cannot be approved");
        }

        WorkflowInstance instance = instanceRepo.findById(task.getInstanceId())
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));

        if (instance.getTemplateId() != null) {
            WorkflowTemplate template = templateRepo.findById(instance.getTemplateId())
                    .orElseThrow(() -> new RuntimeException("Workflow template not found"));

            if (WorkflowConstants.WORKFLOW_TYPE_SEQUENTIAL.equalsIgnoreCase(template.getWorkflowType())) {
                List<WorkflowTask> allTasks = taskRepo.findByInstanceIdOrderByStepOrderAsc(instance.getId());

                for (WorkflowTask existingTask : allTasks) {
                    if (existingTask.getStepOrder() < task.getStepOrder()
                            && !WorkflowConstants.TASK_APPROVED.equals(existingTask.getStatus())) {
                        throw new RuntimeException("Previous step must be approved first");
                    }
                }
            }
        }

        task.setStatus(WorkflowConstants.TASK_APPROVED);
        task.setActionComment(normalizeComment(request));
        task = taskRepo.save(task);

        List<WorkflowTask> updatedTasks = taskRepo.findByInstanceIdOrderByStepOrderAsc(instance.getId());

        boolean allApproved = updatedTasks.stream()
                .allMatch(t -> WorkflowConstants.TASK_APPROVED.equals(t.getStatus()));

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

    public WorkflowTask rejectTask(Long taskId, WorkflowTaskActionRequest request) {
        WorkflowTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (WorkflowConstants.TASK_APPROVED.equals(task.getStatus())) {
            throw new RuntimeException("Approved task cannot be rejected");
        }

        task.setStatus(WorkflowConstants.TASK_REJECTED);
        task.setActionComment(normalizeComment(request));
        task = taskRepo.save(task);

        WorkflowInstance instance = instanceRepo.findById(task.getInstanceId())
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));

        instance.setStatus(WorkflowConstants.WORKFLOW_REJECTED);
        instanceRepo.save(instance);

        documentLifecycleService.updateDocumentStatus(
                instance.getDocumentId(),
                WorkflowConstants.DOCUMENT_REJECTED
        );

        return task;
    }

    public WorkflowTask rejectTask(Long taskId) {
        return rejectTask(taskId, null);
    }

    private String normalizeComment(WorkflowTaskActionRequest request) {
        if (request == null || request.getComment() == null) {
            return null;
        }

        String trimmed = request.getComment().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
