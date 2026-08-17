package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dto.DocumentLockStatusResponse;
import com.dms.exceptions.DocumentLockedException;
import com.dms.exceptions.ResourceNotFoundException;
import com.dms.models.Documents;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pessimistic edit locking for documents.
 *
 * The system stores files in S3 and has no in-place editor, so "editing" means
 * replacing the file or changing its metadata and tags. Those operations are
 * serialised by giving one user the document at a time, rather than trying to
 * merge concurrent changes.
 *
 * Expiry is evaluated lazily whenever the lock is read: a lock older than the
 * configured timeout is simply treated as free. That avoids a background
 * sweeper (scheduling is not enabled in this application) and means a crashed
 * browser cannot hold a document hostage.
 */
@Service
public class DocumentLockService {

    private final DocumentRepository documentRepository;
    private final AuditLogService auditLogService;

    @Value("${app.document.lock.timeout-minutes:15}")
    private long lockTimeoutMinutes;

    public DocumentLockService(DocumentRepository documentRepository, AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Current lock state, with expiry applied. Never throws for an unlocked document. */
    @Transactional(readOnly = true)
    public DocumentLockStatusResponse status(UUID documentId, UUID currentUserId) {
        Documents document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        return toStatus(document, currentUserId);
    }

    /**
     * Guard for every operation that changes document content. Passes when the
     * document is free, when the lock has expired, or when the caller holds it.
     */
    @Transactional(readOnly = true)
    public void assertCanMutate(UUID documentId, UUID currentUserId) {
        documentRepository.findById(documentId).ifPresent(document -> {
            if (isHeldByAnotherUser(document, currentUserId)) {
                throw new DocumentLockedException(document.getLockedByUsername(), document.getLockedAt());
            }
        });
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    /**
     * Takes the document for editing. Re-acquiring a lock you already hold is
     * allowed and refreshes the timer, so simply staying on the page keeps it.
     *
     * @throws DocumentLockedException if another user holds an unexpired lock
     */
    @Transactional
    public DocumentLockStatusResponse acquire(UUID documentId, UUID userId, String username, String remoteAddr) {
        // Row-level write lock: two simultaneous acquires are serialised here,
        // so exactly one of them can win.
        Documents document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (isHeldByAnotherUser(document, userId)) {
            throw new DocumentLockedException(document.getLockedByUsername(), document.getLockedAt());
        }

        boolean takingOverExpiredLock =
                document.getLockedByUserId() != null && !document.getLockedByUserId().equals(userId);

        document.setLockedByUserId(userId);
        document.setLockedByUsername(username);
        document.setLockedAt(LocalDateTime.now());
        document.setIs_locked(true);
        documentRepository.save(document);

        auditLogService.createAuditLog(
                takingOverExpiredLock ? "DOCUMENT_LOCK_TAKEOVER" : "DOCUMENT_LOCKED",
                documentId, remoteAddr, "SUCCESS");

        return toStatus(document, userId);
    }

    /**
     * Releases the lock. The holder can always release their own; an
     * administrator can force-release someone else's (US-30).
     *
     * @throws DocumentLockedException if a non-admin tries to release another user's lock
     */
    @Transactional
    public DocumentLockStatusResponse release(UUID documentId, UUID userId, boolean isAdmin, String remoteAddr) {
        Documents document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Nothing to do - report the free state rather than failing.
        if (document.getLockedByUserId() == null) {
            clearLock(document);
            documentRepository.save(document);
            return DocumentLockStatusResponse.unlocked(documentId);
        }

        boolean ownsLock = document.getLockedByUserId().equals(userId);
        boolean expired = isExpired(document.getLockedAt());

        if (!ownsLock && !isAdmin && !expired) {
            throw new DocumentLockedException(document.getLockedByUsername(), document.getLockedAt());
        }

        String action = ownsLock ? "DOCUMENT_UNLOCKED" : "DOCUMENT_LOCK_FORCE_RELEASED";
        clearLock(document);
        documentRepository.save(document);

        auditLogService.createAuditLog(action, documentId, remoteAddr, "SUCCESS");

        return DocumentLockStatusResponse.unlocked(documentId);
    }

    /**
     * Releases a lock held by the given user without complaining if they do not
     * hold it. Used after a save, where the edit session is over either way.
     */
    @Transactional
    public void releaseIfHeldBy(UUID documentId, UUID userId) {
        documentRepository.findByIdForUpdate(documentId).ifPresent(document -> {
            if (userId.equals(document.getLockedByUserId())) {
                clearLock(document);
                documentRepository.save(document);
            }
        });
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private boolean isHeldByAnotherUser(Documents document, UUID userId) {
        UUID holder = document.getLockedByUserId();
        if (holder == null || holder.equals(userId)) {
            return false;
        }
        return !isExpired(document.getLockedAt());
    }

    private boolean isExpired(LocalDateTime lockedAt) {
        // A lock with no timestamp is treated as stale rather than eternal.
        if (lockedAt == null) {
            return true;
        }
        return lockedAt.plusMinutes(lockTimeoutMinutes).isBefore(LocalDateTime.now());
    }

    private void clearLock(Documents document) {
        document.setLockedByUserId(null);
        document.setLockedByUsername(null);
        document.setLockedAt(null);
        document.setIs_locked(false);
    }

    private DocumentLockStatusResponse toStatus(Documents document, UUID currentUserId) {
        UUID holder = document.getLockedByUserId();
        if (holder == null || isExpired(document.getLockedAt())) {
            return DocumentLockStatusResponse.unlocked(document.getDocument_id());
        }

        return new DocumentLockStatusResponse(
                document.getDocument_id(),
                true,
                holder,
                document.getLockedByUsername(),
                document.getLockedAt(),
                document.getLockedAt().plusMinutes(lockTimeoutMinutes),
                holder.equals(currentUserId));
    }

    /** Exposed so tests can set the timeout without a Spring context. */
    void setLockTimeoutMinutes(long lockTimeoutMinutes) {
        this.lockTimeoutMinutes = lockTimeoutMinutes;
    }
}
