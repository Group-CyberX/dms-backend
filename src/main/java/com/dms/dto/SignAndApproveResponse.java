package com.dms.dto;

import java.util.UUID;

/** Result of stamping a document: the new version created and the signature logged against it. */
public record SignAndApproveResponse(
        UUID documentId,
        UUID newVersionId,
        String newVersionNumber,
        UUID signatureId,
        String documentHash,
        int placementsApplied,
        boolean taskApproved
) {}
