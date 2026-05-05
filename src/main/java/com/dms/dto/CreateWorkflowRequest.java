package com.dms.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateWorkflowRequest {

    private String documentId;

    private Long templateId;

    private String workflowName;

    private String priority;

    private LocalDate dueDate;

    private List<String> approvers;
}