package com.dms.dto;

import java.util.UUID;

/**
 * A comment on a shared document.
 *
 * Leaving pageNumber/anchorX/anchorY null creates a general comment that only
 * appears in the discussion drawer. Supplying them anchors it to a point on the
 * page, where it also shows as a pin. Coordinates are fractions of the page
 * (0.0 - 1.0) from the top-left, matching the signature placement convention.
 */
public record AddCommentRequest(
        String content,
        Integer pageNumber,
        Double anchorX,
        Double anchorY,
        UUID parentCommentId,
        /**
         * COMMENT (default) for a pinned note, or TEXT for typewriter text that
         * is drawn straight onto the page when the version is saved.
         */
        String annotationType
) {
    /** Convenience for the callers that only ever create ordinary comments. */
    public AddCommentRequest(String content, Integer pageNumber, Double anchorX,
                             Double anchorY, UUID parentCommentId) {
        this(content, pageNumber, anchorX, anchorY, parentCommentId, null);
    }
}
