package com.dms.service;

import com.dms.constants.WorkflowConstants;
import com.dms.dao.WorkflowInstanceRepository;
import com.dms.dao.WorkflowTaskRepository;
import com.dms.dao.WorkflowTemplateStepRepository;
import com.dms.dto.CreateWorkflowRequest;
import com.dms.models.WorkflowInstance;
import com.dms.models.WorkflowTask;
import com.dms.models.WorkflowTemplate;
import com.dms.models.WorkflowTemplateStep;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkflowService {

    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowTaskRepository taskRepo;
    private final WorkflowTemplateStepRepository stepRepo;
    private final WorkflowTemplateService templateService;
    private final DocumentLifecycleService documentLifecycleService;

    // Constructor injection of dependencies
    public WorkflowService(
            WorkflowInstanceRepository instanceRepo,
            WorkflowTaskRepository taskRepo,
            WorkflowTemplateStepRepository stepRepo,
            WorkflowTemplateService templateService,
            DocumentLifecycleService documentLifecycleService
    ) {
        this.instanceRepo = instanceRepo;
        this.taskRepo = taskRepo;
        this.stepRepo = stepRepo;
        this.templateService = templateService;
        this.documentLifecycleService = documentLifecycleService;
    }

    // Main method to create workflow (manual or template-based)
    public WorkflowInstance createWorkflow(CreateWorkflowRequest request) {

        if (request.getDocumentId() == null || request.getDocumentId().isBlank()) {
            throw new RuntimeException("Document ID is required");
        }
        if (request.getWorkflowName() == null || request.getWorkflowName().isBlank()) {
            throw new RuntimeException("Workflow name is required");
        }
        if (request.getPriority() == null || request.getPriority().isBlank()) {
            throw new RuntimeException("Priority is required");
        }
        if (request.getDueDate() == null) {
            throw new RuntimeException("Due date is required");
        }

        Long finalTemplateId = request.getTemplateId();

        // Manual workflow                 
        if (finalTemplateId == null) {

            // Must provide approvers for manual workflow
            if (request.getApprovers() == null || request.getApprovers().isEmpty()) {
                throw new RuntimeException("Approvers are required for manual workflow");
            }

            if (request.isSaveAsTemplate()) {
                // Convert manual workflow → save as reusable template   
                WorkflowTemplate newTemplate =
                        templateService.createTemplateFromManualWorkflow(request);
                finalTemplateId = newTemplate.getId();
            } else {
                // Pure manual workflow (no template)
                finalTemplateId = null;
            }
        }

        WorkflowInstance instance = new WorkflowInstance();
        instance.setDocumentId(request.getDocumentId());
        instance.setTemplateId(finalTemplateId);
        instance.setWorkflowName(request.getWorkflowName());
        instance.setPriority(request.getPriority());
        instance.setDueDate(request.getDueDate());

        // Default status
        instance.setStatus(WorkflowConstants.WORKFLOW_PENDING_APPROVAL);
        instance.setCreatedByUserId(request.getCreatedByUserId());

        instance = instanceRepo.save(instance);

        documentLifecycleService.updateDocumentStatus(
                request.getDocumentId(),
                WorkflowConstants.DOCUMENT_PENDING_APPROVAL
        );

        if (finalTemplateId != null) {

            // Template-based workflow
            List<WorkflowTemplateStep> steps =
                    stepRepo.findByTemplateIdOrderByStepOrderAsc(finalTemplateId);

            if (steps == null || steps.isEmpty()) {
                throw new RuntimeException("No steps found for workflow template");
            }

            createTasksFromTemplate(instance, steps);

        } else {
            // Manual flow with ad-hoc approvers
            createTasksManual(instance, request.getApprovers());
        }

        return instance;
    }

    // Create tasks based on template steps
    private void createTasksFromTemplate(WorkflowInstance instance, List<WorkflowTemplateStep> steps) {
        for (WorkflowTemplateStep step : steps) {
            WorkflowTask task = new WorkflowTask();

            task.setInstanceId(instance.getId());
            task.setStepOrder(step.getStepOrder());
            // If approverUserId is set → assign to that user; otherwise assign to role 
            task.setUserId(
                    step.getApproverUserId() != null && !step.getApproverUserId().isBlank()
                            ? step.getApproverUserId()
                            : step.getApproverRole()
            );
            task.setStatus(WorkflowConstants.TASK_PENDING);
            taskRepo.save(task);
        }
    }

    // Create tasks manually (no template)
    private void createTasksManual(WorkflowInstance instance, List<String> approvers) {
        int order = 1;
        for (String role : approvers) {
            WorkflowTask task = new WorkflowTask();
            task.setInstanceId(instance.getId());
            task.setStepOrder(order++);
            task.setUserId(role);
            task.setStatus(WorkflowConstants.TASK_PENDING);
            taskRepo.save(task);
        }
    }

    // Fetch all workflow instances (for listing / My Tasks page)
    public List<WorkflowInstance> getAllWorkflows() {
        return instanceRepo.findAll();
    }
}