package com.dms.dto;

import java.util.UUID;

/** Result of writing the review comments into the document as a new version. */
public record SaveAnnotatedVersionResponse(
        UUID documentId,
        UUID newVersionId,
        String newVersionNumber,
        int commentsIncluded
) {}
