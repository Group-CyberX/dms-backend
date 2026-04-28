package com.dms.rest;

import com.dms.dto.CreateWorkflowTemplateRequest;
import com.dms.models.WorkflowTemplate;
import com.dms.models.WorkflowTemplateStep;
import com.dms.service.WorkflowTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@CrossOrigin(origins = "http://localhost:3000")
public class WorkflowTemplateController {

    private final WorkflowTemplateService templateService;

    public WorkflowTemplateController(WorkflowTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    public WorkflowTemplate createTemplate(@RequestBody CreateWorkflowTemplateRequest request) {
        return templateService.createTemplate(request);
    }

    @PutMapping("/{templateId}")
    public WorkflowTemplate updateTemplate(
            @PathVariable Long templateId,
            @RequestBody CreateWorkflowTemplateRequest request
    ) {
        return templateService.updateTemplate(templateId, request);
    }

    @DeleteMapping("/{templateId}")
    public void deleteTemplate(@PathVariable Long templateId) {
        templateService.deleteTemplate(templateId);
    }

    @GetMapping
    public List<WorkflowTemplate> getAllTemplates() {
        return templateService.getAllTemplates();
    }

    @GetMapping("/{templateId}")
    public WorkflowTemplate getTemplateById(@PathVariable Long templateId) {
        return templateService.getAllTemplates().stream()
                .filter(template -> template.getId().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Template not found"
                ));
    }

    @GetMapping("/by-document-type/{documentType}")
    public List<WorkflowTemplate> getTemplatesByDocumentType(@PathVariable String documentType) {
        return templateService.getTemplatesByDocumentType(documentType);
    }

    @GetMapping("/{templateId}/steps")
    public List<WorkflowTemplateStep> getStepsByTemplateId(@PathVariable Long templateId) {
        return templateService.getStepsByTemplateId(templateId);
    }
}