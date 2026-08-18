package com.dms.constants;

public final class WorkflowConstants {

    private WorkflowConstants() {
    }

    // workflow instance statuses
    public static final String WORKFLOW_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String WORKFLOW_APPROVED = "APPROVED";
    public static final String WORKFLOW_REJECTED = "REJECTED";

    // task statuses
    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_ACTIVE = "ACTIVE";
    public static final String TASK_APPROVED = "APPROVED";
    public static final String TASK_REJECTED = "REJECTED";

    // document statuses
    public static final String DOCUMENT_NEW = "NEW";
    public static final String DOCUMENT_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String DOCUMENT_APPROVED = "APPROVED";
    public static final String DOCUMENT_REJECTED = "REJECTED";

    // workflow types
    public static final String WORKFLOW_TYPE_SEQUENTIAL = "SEQUENTIAL";
    public static final String WORKFLOW_TYPE_PARALLEL = "PARALLEL";

    // priority levels
    public static final String PRIORITY_LOW = "LOW";
    public static final String PRIORITY_MEDIUM = "MEDIUM";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";
}