package com.dms.dto;

import lombok.Data;

//used when approving or rejecting a task
@Data
public class WorkflowTaskActionRequest {
    // Optional comment provided by approver when approving/rejecting
    private String comment;
}
