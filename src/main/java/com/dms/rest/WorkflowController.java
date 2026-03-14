package com.dms.rest;

import com.dms.dto.CreateWorkflowRequest;
import com.dms.models.WorkflowInstance;
import com.dms.service.WorkflowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
@CrossOrigin
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    public WorkflowInstance createWorkflow(@RequestBody CreateWorkflowRequest request) {

        return workflowService.createWorkflow(request);
    }
    @GetMapping
    public List<WorkflowInstance> getAllWorkflows() {
        return workflowService.getAllWorkflows();
    }

}