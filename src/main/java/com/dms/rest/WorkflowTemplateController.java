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

    @GetMapping
    public List<WorkflowTemplate> getAllTemplates() {
        return templateService.getAllTemplates();
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