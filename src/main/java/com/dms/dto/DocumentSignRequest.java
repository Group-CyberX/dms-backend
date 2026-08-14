package com.dms.dto;

import java.util.UUID;

public record DocumentSignRequest(
        UUID documentVersionId,
        String comments,
        String documentHash // Generated on frontend or calculated from content streaming asset
) {}
