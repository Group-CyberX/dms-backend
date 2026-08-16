package com.dms.service;

import com.dms.constants.WorkflowConstants;
import com.dms.dao.UserRepository;
import com.dms.dao.WorkflowTemplateRepository;
import com.dms.dao.WorkflowTemplateStepRepository;
import com.dms.dao.WorkflowInstanceRepository;
import com.dms.dto.CreateWorkflowRequest;
import com.dms.dto.CreateWorkflowTemplateRequest;
import com.dms.models.WorkflowTemplate;
import com.dms.models.User;
import com.dms.models.WorkflowTemplateStep;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowTemplateService {

    // Repositories for DB interaction
    private final WorkflowTemplateRepository templateRepo;
    private final WorkflowTemplateStepRepository stepRepo;
    private final WorkflowInstanceRepository workflowInstanceRepo;
    private final UserRepository userRepository;

    // Constructor injection
    public WorkflowTemplateService(
            WorkflowTemplateRepository templateRepo,
            WorkflowTemplateStepRepository stepRepo,
            WorkflowInstanceRepository workflowInstanceRepo,
            UserRepository userRepository
    ) {
        this.templateRepo = templateRepo;
        this.stepRepo = stepRepo;
        this.workflowInstanceRepo = workflowInstanceRepo;
        this.userRepository = userRepository;
    }

    // Create a new workflow template with steps
    public WorkflowTemplate createTemplate(CreateWorkflowTemplateRequest request) {

        WorkflowTemplate template = new WorkflowTemplate();

        // Set template details
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setDocumentType(request.getDocumentType());
        template.setNumberOfSteps(request.getNumberOfSteps());
        // Workflow type defaults to SEQUENTIAL
        template.setWorkflowType(
                request.getWorkflowType() == null || request.getWorkflowType().isBlank()
                        ? WorkflowConstants.WORKFLOW_TYPE_SEQUENTIAL
                        : request.getWorkflowType()
        );
        template.setCreatedBy(request.getCreatedBy());
        template.setSystemTemplate(request.isSystemTemplate());
        template.setRequiresSignature(request.isRequiresSignature());
        template.setCreatedAt(LocalDateTime.now());

        // Save template first
        template = templateRepo.save(template);

        // Save each step associated with the template
        if (request.getStepApprovers() != null) {
            for (CreateWorkflowTemplateRequest.StepApprover stepDto : request.getStepApprovers()) {
                WorkflowTemplateStep step = new WorkflowTemplateStep();
                step.setTemplateId(template.getId());
                step.setStepOrder(stepDto.getStepOrder());
                step.setApproverUserId(stepDto.getApproverUserId());
                step.setApproverName(stepDto.getApproverName());
                step.setApproverRole(stepDto.getApproverRole());
                stepRepo.save(step);
            }
        }

        return template;
    }

    // Update template and replace all steps
    public WorkflowTemplate updateTemplate(Long templateId, CreateWorkflowTemplateRequest request) {
        // Fetch existing template
        WorkflowTemplate template = templateRepo.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));

        // Update template details
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setDocumentType(request.getDocumentType());
        template.setNumberOfSteps(request.getNumberOfSteps());

        template.setWorkflowType(
                request.getWorkflowType() == null || request.getWorkflowType().isBlank()
                        ? WorkflowConstants.WORKFLOW_TYPE_SEQUENTIAL
                        : request.getWorkflowType()
        );
        template.setCreatedBy(request.getCreatedBy());
        template.setSystemTemplate(request.isSystemTemplate());
        template.setRequiresSignature(request.isRequiresSignature());
        template.setCreatedAt(template.getCreatedAt() == null ? LocalDateTime.now() : template.getCreatedAt());

        template = templateRepo.save(template);

        // Remove old steps before adding new ones
        stepRepo.deleteAll(stepRepo.findByTemplateIdOrderByStepOrderAsc(templateId));

        // Save updated steps
        if (request.getStepApprovers() != null) {
            for (CreateWorkflowTemplateRequest.StepApprover stepDto : request.getStepApprovers()) {
                WorkflowTemplateStep step = new WorkflowTemplateStep();
                step.setTemplateId(template.getId());
                step.setStepOrder(stepDto.getStepOrder());
                step.setApproverUserId(stepDto.getApproverUserId());
                step.setApproverName(stepDto.getApproverName());
                step.setApproverRole(stepDto.getApproverRole());
                stepRepo.save(step);
            }
        }

        return template;
    }

    // Delete template and its steps
    @Transactional
    public void deleteTemplate(Long templateId) {
        if (!templateRepo.existsById(templateId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        }

        stepRepo.deleteAll(stepRepo.findByTemplateIdOrderByStepOrderAsc(templateId));
        templateRepo.deleteById(templateId);
    }

    // Fetch all workflow templates from database
    public List<WorkflowTemplate> getAllTemplates() {
        return templateRepo.findAll();
    }

    // Get how many workflows are using a specific template
    public long getTemplateUsageCount(Long templateId) {
        return workflowInstanceRepo.countByTemplateId(templateId);
    }

    // Fetch templates filtered by document type
    public List<WorkflowTemplate> getTemplatesByDocumentType(String documentType) {
        return templateRepo.findByDocumentTypeIgnoreCase(documentType);
    }

    // Get all steps for a given template in correct order
    public List<WorkflowTemplateStep> getStepsByTemplateId(Long templateId) {
        return stepRepo.findByTemplateIdOrderByStepOrderAsc(templateId);
    }

    // Simple getter for template by id used by other services
    public WorkflowTemplate getTemplateById(Long templateId) {
        return templateRepo.findById(templateId).orElse(null);
    }

    //Convert a manually created workflow into a reusable template
    // manual workflow -> save as new template
    public WorkflowTemplate createTemplateFromManualWorkflow(CreateWorkflowRequest request) {

        WorkflowTemplate template = new WorkflowTemplate();

        // Use provided template name, otherwise fallback to workflow name
        String templateName = request.getTemplateName() != null && !request.getTemplateName().isBlank()
                ? request.getTemplateName()
                : request.getWorkflowName();
        template.setName(templateName);

        template.setDescription(
            request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription()
                : "Auto-created from manual workflow"
        );

        template.setDocumentType(
                request.getDocumentType() == null || request.getDocumentType().isBlank()
                        ? "UNKNOWN"
                        : request.getDocumentType()
        );
        template.setNumberOfSteps(request.getApprovers().size());
        template.setWorkflowType(
            request.getWorkflowType() == null || request.getWorkflowType().isBlank()
                ? WorkflowConstants.WORKFLOW_TYPE_SEQUENTIAL
                : request.getWorkflowType()
        );
        template.setCreatedBy(request.getCreatedByUserId());
        // Manual workflows saved as templates are not system templates
        template.setSystemTemplate(false);
        template.setCreatedAt(LocalDateTime.now());

        // Save template first to generate ID
        template = templateRepo.save(template);

        // Create steps based on approvers list
        int order = 1;
        for (String approver : request.getApprovers()) {
            User user = resolveUser(approver);

            WorkflowTemplateStep step = new WorkflowTemplateStep();
            step.setTemplateId(template.getId());
            step.setStepOrder(order++);
            
            step.setApproverUserId(user != null ? user.getUserId().toString() : approver);
            step.setApproverName(user != null ? user.getUsername() : null);
            step.setApproverRole(user != null && user.getRole() != null ? user.getRole().getName() : approver);
            // Save each step to the database
            stepRepo.save(step);
        }

        return template;
    }

    // Helper method to resolve user by ID or return null if not found
    private User resolveUser(String approverIdentifier) {
        if (approverIdentifier == null || approverIdentifier.isBlank()) {
            return null;
        }

        try {
            // Try converting string to UUID
            UUID userId = UUID.fromString(approverIdentifier.trim());
            return userRepository.findById(userId).orElse(null);
        } catch (IllegalArgumentException ignored) {
            // If not a valid UUID → treat as role or plain string
            return null;
        }
    }
}