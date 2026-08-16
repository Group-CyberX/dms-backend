package com.dms.rest;

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
    @GetMapping
    public List<WorkflowInstance> getAllWorkflows() {
        return workflowService.getAllWorkflows();
    }
}