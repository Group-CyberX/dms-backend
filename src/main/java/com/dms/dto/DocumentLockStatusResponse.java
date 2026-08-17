package com.dms.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The effective edit-lock state of a document, with expiry already applied - a
 * lock older than the configured timeout is reported as free.
 */
public record DocumentLockStatusResponse(
        UUID documentId,
        boolean locked,
        UUID lockedByUserId,
        String lockedByUsername,
        LocalDateTime lockedAt,
        LocalDateTime expiresAt,
        boolean heldByCurrentUser
) {
    /** No one is editing this document. */
    public static DocumentLockStatusResponse unlocked(UUID documentId) {
        return new DocumentLockStatusResponse(documentId, false, null, null, null, null, false);
    }
}
