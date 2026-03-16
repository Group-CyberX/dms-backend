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
    private List<StepApprover> stepApprovers;

    @Data
    public static class StepApprover {
        private int stepOrder;
        private String approverRole;
    }
}
