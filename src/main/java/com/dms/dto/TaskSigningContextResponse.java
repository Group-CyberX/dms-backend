package com.dms.dto;

/**
 * Everything the UI needs when an approver clicks "Approve": whether this
 * workflow demands a placed signature, and which document to open for signing.
 */
public record TaskSigningContextResponse(
        Long taskId,
        Long instanceId,
        String documentId,
        String workflowName,
        boolean requiresSignature
) {}
