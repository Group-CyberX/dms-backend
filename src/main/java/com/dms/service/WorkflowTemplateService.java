package com.dms.service;

import com.dms.constants.WorkflowConstants;
import com.dms.dao.WorkflowTemplateRepository;
import com.dms.dao.WorkflowTemplateStepRepository;
import com.dms.dto.CreateWorkflowRequest;
import com.dms.dto.CreateWorkflowTemplateRequest;
import com.dms.models.WorkflowTemplate;
import com.dms.models.WorkflowTemplateStep;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkflowTemplateService {

    private final WorkflowTemplateRepository templateRepo;
    private final WorkflowTemplateStepRepository stepRepo;

    public WorkflowTemplateService(
            WorkflowTemplateRepository templateRepo,
            WorkflowTemplateStepRepository stepRepo
    ) {
        this.templateRepo = templateRepo;
        this.stepRepo = stepRepo;
    }

    public WorkflowTemplate createTemplate(CreateWorkflowTemplateRequest request) {

        WorkflowTemplate template = new WorkflowTemplate();
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
        template.setCreatedAt(LocalDateTime.now());

        template = templateRepo.save(template);

        if (request.getStepApprovers() != null) {
            for (CreateWorkflowTemplateRequest.StepApprover stepDto : request.getStepApprovers()) {
                WorkflowTemplateStep step = new WorkflowTemplateStep();
                step.setTemplateId(template.getId());
                step.setStepOrder(stepDto.getStepOrder());
                step.setApproverRole(stepDto.getApproverRole());
                stepRepo.save(step);
            }
        }

        return template;
    }

    public List<WorkflowTemplate> getAllTemplates() {
        return templateRepo.findAll();
    }

    public List<WorkflowTemplate> getTemplatesByDocumentType(String documentType) {
        return templateRepo.findByDocumentTypeIgnoreCase(documentType);
    }

    public List<WorkflowTemplateStep> getStepsByTemplateId(Long templateId) {
        return stepRepo.findByTemplateIdOrderByStepOrderAsc(templateId);
    }

    // manual workflow -> save as new template
    public WorkflowTemplate createTemplateFromManualWorkflow(CreateWorkflowRequest request) {

        WorkflowTemplate template = new WorkflowTemplate();

        String templateName = request.getTemplateName() != null && !request.getTemplateName().isBlank()
                ? request.getTemplateName()
                : request.getWorkflowName();
        template.setName(templateName);
        template.setDescription("Auto-created from manual workflow");
        template.setDocumentType(
                request.getDocumentType() == null || request.getDocumentType().isBlank()
                        ? "UNKNOWN"
                        : request.getDocumentType()
        );
        template.setNumberOfSteps(request.getApprovers().size());
        template.setWorkflowType(WorkflowConstants.WORKFLOW_TYPE_SEQUENTIAL);
        template.setCreatedBy(request.getCreatedByUserId());
        template.setSystemTemplate(false);
        template.setCreatedAt(LocalDateTime.now());

        template = templateRepo.save(template);

        int order = 1;
        for (String approverRole : request.getApprovers()) {
            WorkflowTemplateStep step = new WorkflowTemplateStep();
            step.setTemplateId(template.getId());
            step.setStepOrder(order++);
            step.setApproverRole(approverRole);
            stepRepo.save(step);
        }

        return template;
    }
}