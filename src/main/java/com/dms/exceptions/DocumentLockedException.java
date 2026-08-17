package com.dms.exceptions;

import java.time.LocalDateTime;

/**
 * Raised when a user tries to change a document that another user currently
 * holds for editing. Surfaces as HTTP 409 so the client can distinguish it from
 * a permission failure and show who is holding the lock.
 */
public class DocumentLockedException extends RuntimeException {

    private final String lockedByUsername;
    private final LocalDateTime lockedAt;

    public DocumentLockedException(String lockedByUsername, LocalDateTime lockedAt) {
        super("This document is currently being edited by "
                + (lockedByUsername == null || lockedByUsername.isBlank() ? "another user" : lockedByUsername));
        this.lockedByUsername = lockedByUsername;
        this.lockedAt = lockedAt;
    }

    public String getLockedByUsername() {
        return lockedByUsername;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }
}
