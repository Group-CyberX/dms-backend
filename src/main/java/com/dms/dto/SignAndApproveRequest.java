package com.dms.dto;

import java.util.List;
import java.util.UUID;

/**
 * Request sent when an approver places one or more signatures on a document and
 * confirms the approval. The stamped result is saved as a new document version.
 */
public record SignAndApproveRequest(
        UUID documentId,
        Long taskId,                       // optional - present when signing as part of a workflow task
        String comments,
        List<SignaturePlacementDTO> placements
) {}
