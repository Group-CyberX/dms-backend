package com.dms.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateWorkflowTemplateRequest {
    private String name;
    private String description;
    private String documentType;
    private int numberOfSteps;
    private String workflowType;
    private String createdBy;
    private boolean systemTemplate;
    private List<StepApprover> stepApprovers;

    // Inner DTO representing each step's approver details
    @Data
    public static class StepApprover {
        private int stepOrder;
        private String approverUserId;
        private String approverName;
        private String approverRole;
    }
}
