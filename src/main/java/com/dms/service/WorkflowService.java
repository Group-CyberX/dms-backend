package com.dms.service;

import com.dms.dto.CreateWorkflowRequest;
import com.dms.models.*;
import com.dms.dao.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkflowService {

    private final com.dms.dao.WorkflowInstanceRepository instanceRepo;
    private final com.dms.dao.WorkflowTaskRepository taskRepo;
    private final com.dms.dao.WorkflowTemplateStepRepository stepRepo;

    public WorkflowService(
            com.dms.dao.WorkflowInstanceRepository instanceRepo,
            com.dms.dao.WorkflowTaskRepository taskRepo,
            com.dms.dao.WorkflowTemplateStepRepository stepRepo) {

        this.instanceRepo = instanceRepo;
        this.taskRepo = taskRepo;
        this.stepRepo = stepRepo;
    }

    public com.dms.models.WorkflowInstance createWorkflow(CreateWorkflowRequest request) {

        com.dms.models.WorkflowInstance instance = new com.dms.models.WorkflowInstance();
        instance.setDocumentId(request.getDocumentId());
        instance.setTemplateId(request.getTemplateId());
        instance.setWorkflowName(request.getWorkflowName());
        instance.setPriority(request.getPriority());
        instance.setDueDate(request.getDueDate());
        instance.setStatus("PENDING_APPROVAL");

        instance = instanceRepo.save(instance);

        if (request.getTemplateId() != null) {

            List<com.dms.models.WorkflowTemplateStep> steps =
                    stepRepo.findByTemplateId(request.getTemplateId());

            for (com.dms.models.WorkflowTemplateStep step : steps) {

                com.dms.models.WorkflowTask task = new com.dms.models.WorkflowTask();
                task.setInstanceId(instance.getId());
                task.setStepOrder(step.getStepOrder());
                task.setUserId(step.getApproverRole());
                task.setStatus("PENDING");

                taskRepo.save(task);
            }

        } else {

            int order = 1;

            for (String user : request.getApprovers()) {

                com.dms.models.WorkflowTask task = new com.dms.models.WorkflowTask();
                task.setInstanceId(instance.getId());
                task.setStepOrder(order++);
                task.setUserId(user);
                task.setStatus("PENDING");

                taskRepo.save(task);
            }
        }

        return instance;
    }

    public List<com.dms.models.WorkflowInstance> getAllWorkflows() {
        return instanceRepo.findAll();
    }
}