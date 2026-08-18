package com.dms.rest;

import com.dms.dao.WorkflowInstanceRepository;
import com.dms.dto.CreateWorkflowRequest;
import com.dms.models.WorkflowInstance;
import com.dms.service.WorkflowService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
@CrossOrigin
public class WorkflowController {

    private final WorkflowService workflowService;

    // Constructor injection of WorkflowService
    public WorkflowController(WorkflowService workflowService) {

        this.workflowService = workflowService;
    }

    // Create new workflow (manual or template-based)
    @PostMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canCreateWorkflow')")
    public WorkflowInstance createWorkflow(@RequestBody CreateWorkflowRequest request) {
        return workflowService.createWorkflow(request);
    }

    // Get all workflows (used for listing / My Tasks page)
    /**
     * One short row per document giving its most recent workflow status, for
     * the badges on the document list and document page.
     */
    @GetMapping("/status-by-document")
    public List<WorkflowInstanceRepository.DocumentWorkflowStatus> getStatusByDocument() {
        return workflowService.getLatestStatusPerDocument();
    }

    /** Workflow count per template, for the policies screen's usage column. */
    @GetMapping("/usage-by-template")
    public List<WorkflowInstanceRepository.TemplateUsage> getUsageByTemplate() {
        return workflowService.getUsageByTemplate();
    }

    @GetMapping
    public List<WorkflowInstance> getAllWorkflows() {
        return workflowService.getAllWorkflows();
    }
}