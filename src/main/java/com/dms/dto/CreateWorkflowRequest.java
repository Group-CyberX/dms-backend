package com.dms.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateWorkflowRequest {

    private String documentId;
    private String documentType;
    private Long templateId;
    private String workflowName;
    private String description;
    private String priority;
    private LocalDate dueDate;
    private List<String> approvers;
    private String createdByUserId;
    private boolean saveAsTemplate;
    private String templateName; 
}