package com.dms.rest;

import com.dms.dto.TaskContextResponse;
import com.dms.dto.TaskSigningContextResponse;
import com.dms.dto.WorkflowTaskActionRequest;
import com.dms.models.WorkflowTask;
import com.dms.service.WorkflowTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin
public class WorkflowTaskController {

    private final WorkflowTaskService workflowTaskService;

    public WorkflowTaskController(WorkflowTaskService workflowTaskService) {
        this.workflowTaskService = workflowTaskService;
    }

    // Get all tasks for a specific workflow instance
    @GetMapping("/instance/{instanceId}")
    public List<WorkflowTask> getTasksByInstance(@PathVariable Long instanceId) {
        return workflowTaskService.getTasksByInstanceId(instanceId);
    }

    // Tells the UI whether this task needs a placed signature before approval,
    // and which document to open for signing.
    @GetMapping("/{taskId}/signing-context")
    public TaskSigningContextResponse getSigningContext(@PathVariable Long taskId) {
        return workflowTaskService.getSigningContext(taskId);
    }

    /**
     * Everything the document page needs about one task: its status, the
     * workflow's status, whether it is waiting on this caller, and the banner
     * text. One request, in place of the several the page used to make.
     */
    @GetMapping("/{taskId}/context")
    public TaskContextResponse getContext(@PathVariable Long taskId) {
        return workflowTaskService.getTaskContext(taskId);
    }

    // Approve a task
    @PostMapping("/{taskId}/approve")
    public WorkflowTask approveTask(@PathVariable Long taskId,
                                    @RequestBody(required = false) WorkflowTaskActionRequest request) {
        return workflowTaskService.approveTask(taskId, request);
    }

    // Reject a task
    @PostMapping("/{taskId}/reject")
    public WorkflowTask rejectTask(@PathVariable Long taskId,
                                   @RequestBody(required = false) WorkflowTaskActionRequest request) {
        return workflowTaskService.rejectTask(taskId, request);
    }
}