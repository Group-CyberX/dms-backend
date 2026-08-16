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
import com.dms.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {

    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowTaskRepository taskRepo;
    private final WorkflowTemplateStepRepository stepRepo;
    private final WorkflowTemplateService templateService;
    private final DocumentLifecycleService documentLifecycleService;
    private final AuditLogService auditLogService;

    // Constructor injection of dependencies
    public WorkflowService(
            WorkflowInstanceRepository instanceRepo,
            WorkflowTaskRepository taskRepo,
            WorkflowTemplateStepRepository stepRepo,
            WorkflowTemplateService templateService,
            DocumentLifecycleService documentLifecycleService,
            AuditLogService auditLogService
    ) {
        this.instanceRepo = instanceRepo;
        this.taskRepo = taskRepo;
        this.stepRepo = stepRepo;
        this.templateService = templateService;
        this.documentLifecycleService = documentLifecycleService;
        this.auditLogService = auditLogService;
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
        if (finalTemplateId != null) {
            WorkflowTemplate template = templateService.getTemplateById(finalTemplateId);
            instance.setDescription(template != null ? template.getDescription() : request.getDescription());
            instance.setDocumentType(template != null ? template.getDocumentType() : request.getDocumentType());
        } else {
            instance.setDescription(request.getDescription());
            instance.setDocumentType(request.getDocumentType());
        }
        instance.setPriority(request.getPriority());
        instance.setDueDate(request.getDueDate());
        // Persist workflow type: prefer template type for template-based workflows,
        // otherwise use the value from the request (manual workflows)
        if (finalTemplateId != null) {
            WorkflowTemplate template = templateService.getTemplateById(finalTemplateId);
            instance.setWorkflowType(template != null && template.getWorkflowType() != null
                ? template.getWorkflowType()
                : WorkflowConstants.WORKFLOW_TYPE_SEQUENTIAL);
        } else {
            instance.setWorkflowType(request.getWorkflowType() == null || request.getWorkflowType().isBlank()
                ? WorkflowConstants.WORKFLOW_TYPE_SEQUENTIAL
                : request.getWorkflowType());
        }

        // Signature requirement: an explicit choice in the builder wins, otherwise
        // inherit whatever the chosen template says. Captured on the instance so
        // later template edits do not change workflows already running.
        if (request.getRequiresSignature() != null) {
            instance.setRequiresSignature(request.getRequiresSignature());
        } else if (finalTemplateId != null) {
            WorkflowTemplate template = templateService.getTemplateById(finalTemplateId);
            instance.setRequiresSignature(template != null && template.requiresSignatureOrFalse());
        } else {
            instance.setRequiresSignature(Boolean.FALSE);
        }

        // Default status
        instance.setStatus(WorkflowConstants.WORKFLOW_PENDING_APPROVAL);
        try {
            instance.setCreatedByUserId(com.dms.security.SecurityUtils.currentUserId().toString());
        } catch (Exception e) {
            instance.setCreatedByUserId(request.getCreatedByUserId());
        }

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

            WorkflowTemplate template = templateService.getTemplateById(finalTemplateId);
            String wfType = template != null && template.getWorkflowType() != null
                    ? template.getWorkflowType()
                    : WorkflowConstants.WORKFLOW_TYPE_SEQUENTIAL;

            createTasksFromTemplate(instance, steps, wfType);

        } else {
            // Manual flow with ad-hoc approvers
            createTasksManual(instance, request.getApprovers(), instance.getWorkflowType());
        }

        // Starting an approval is one of the events an auditor looks for; it was
        // not being recorded anywhere.
        UUID documentId = null;
        try {
            documentId = UUID.fromString(instance.getDocumentId().trim());
        } catch (IllegalArgumentException ignored) {
            // A workflow can carry a non-UUID document reference; the audit row
            // is still worth writing without it.
        }
        auditLogService.tryRecord("WORKFLOW_CREATED", SecurityUtils.currentUserId(),
                documentId, null, "SUCCESS");

        return instance;
    }

    // Create tasks based on template steps; workflowType controls initial task statuses
    private void createTasksFromTemplate(WorkflowInstance instance, List<WorkflowTemplateStep> steps, String workflowType) {
        boolean isParallel = WorkflowConstants.WORKFLOW_TYPE_PARALLEL.equalsIgnoreCase(workflowType);

        for (int i = 0; i < steps.size(); i++) {
            WorkflowTemplateStep step = steps.get(i);
            WorkflowTask task = new WorkflowTask();

            task.setInstanceId(instance.getId());
            task.setStepOrder(step.getStepOrder());
            // If approverUserId is set → assign to that user; otherwise assign to role
            task.setUserId(
                    step.getApproverUserId() != null && !step.getApproverUserId().isBlank()
                            ? step.getApproverUserId()
                            : step.getApproverRole()
            );
            // For parallel workflows all tasks start ACTIVE; for sequential only the first is ACTIVE
            task.setStatus(isParallel ? WorkflowConstants.TASK_ACTIVE : (i == 0 ? WorkflowConstants.TASK_ACTIVE : WorkflowConstants.TASK_PENDING));
            taskRepo.save(task);
        }
    }

    // Create tasks manually (no template)
    private void createTasksManual(WorkflowInstance instance, List<String> approvers, String workflowType) {
        boolean isParallel = WorkflowConstants.WORKFLOW_TYPE_PARALLEL.equalsIgnoreCase(workflowType);

        for (int i = 0; i < approvers.size(); i++) {
            String role = approvers.get(i);
            WorkflowTask task = new WorkflowTask();
            task.setInstanceId(instance.getId());
            task.setStepOrder(i + 1);
            task.setUserId(role);
            // For parallel workflows all tasks start ACTIVE; for sequential only the first is ACTIVE
            task.setStatus(isParallel ? WorkflowConstants.TASK_ACTIVE : (i == 0 ? WorkflowConstants.TASK_ACTIVE : WorkflowConstants.TASK_PENDING));
            taskRepo.save(task);
        }
    }

    // Fetch all workflow instances (for listing / My Tasks page)
    public List<WorkflowInstance> getAllWorkflows() {
        return instanceRepo.findAll();
    }
}