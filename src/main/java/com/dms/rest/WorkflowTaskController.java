package com.dms.rest;

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

    @GetMapping("/instance/{instanceId}")
    public List<WorkflowTask> getTasksByInstance(@PathVariable Long instanceId) {
        return workflowTaskService.getTasksByInstanceId(instanceId);
    }

    @PostMapping("/{taskId}/approve")
    public WorkflowTask approveTask(@PathVariable Long taskId) {
        return workflowTaskService.approveTask(taskId);
    }

    @PostMapping("/{taskId}/reject")
    public WorkflowTask rejectTask(@PathVariable Long taskId) {
        return workflowTaskService.rejectTask(taskId);
    }
}