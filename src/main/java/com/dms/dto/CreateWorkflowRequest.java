package com.dms.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateWorkflowRequest {

    private String documentId;
    private String documentType;

    // if provided, use this template to create workflow; if null → manual workflow creation
    private Long templateId; 
    private String workflowName;
    private String description;
    private String priority;
    private LocalDate dueDate;
    // Workflow type: SEQUENTIAL or PARALLEL
    private String workflowType;
    private List<String> approvers;
    private String createdByUserId;
    /**
     * Null means "inherit from the template" (or false for a manual workflow).
     * An explicit true/false from the builder always wins.
     */
    private Boolean requiresSignature;
    private boolean saveAsTemplate;

    // Name of the new template (if saveAsTemplate = true)
    private String templateName; 
}