package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dto.DocumentLockStatusResponse;
import com.dms.exceptions.DocumentLockedException;
import com.dms.models.Documents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The lock has to get three things right: only one holder at a time, the holder
 * is never blocked by their own lock, and an abandoned lock eventually frees
 * itself. These tests pin all three down.
 */
class DocumentLockServiceTest {

    private DocumentRepository documentRepository;
    private AuditLogService auditLogService;
    private DocumentLockService service;

    private final UUID documentId = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        auditLogService = mock(AuditLogService.class);
        service = new DocumentLockService(documentRepository, auditLogService);
        service.setLockTimeoutMinutes(15);

        // save() returns whatever it was given, like the real repository.
        when(documentRepository.save(any(Documents.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Documents document() {
        Documents doc = new Documents();
        doc.setDocument_id(documentId);
        doc.setTitle("Contract.pdf");
        return doc;
    }

    private Documents lockedBy(UUID userId, String username, LocalDateTime lockedAt) {
        Documents doc = document();
        doc.setLockedByUserId(userId);
        doc.setLockedByUsername(username);
        doc.setLockedAt(lockedAt);
        doc.setIs_locked(true);
        return doc;
    }

    private void existing(Documents doc) {
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(doc));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("acquiring a free document succeeds and records the holder")
    void acquiresFreeDocument() {
        existing(document());

        DocumentLockStatusResponse status = service.acquire(documentId, alice, "Alice", "127.0.0.1");

        assertTrue(status.locked());
        assertEquals(alice, status.lockedByUserId());
        assertEquals("Alice", status.lockedByUsername());
        assertTrue(status.heldByCurrentUser());

        ArgumentCaptor<Documents> saved = ArgumentCaptor.forClass(Documents.class);
        verify(documentRepository).save(saved.capture());
        assertTrue(saved.getValue().isIs_locked(), "is_locked must be kept in sync for the UI badge");
    }

    @Test
    @DisplayName("a second user is refused while the lock is live")
    void refusesSecondUser() {
        existing(lockedBy(alice, "Alice", LocalDateTime.now()));

        DocumentLockedException ex = assertThrows(DocumentLockedException.class,
                () -> service.acquire(documentId, bob, "Bob", "127.0.0.1"));

        assertEquals("Alice", ex.getLockedByUsername());
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("re-acquiring your own lock is allowed and refreshes the timer")
    void reacquiringOwnLockIsIdempotent() {
        LocalDateTime old = LocalDateTime.now().minusMinutes(5);
        existing(lockedBy(alice, "Alice", old));

        DocumentLockStatusResponse status = service.acquire(documentId, alice, "Alice", "127.0.0.1");

        assertTrue(status.heldByCurrentUser());
        ArgumentCaptor<Documents> saved = ArgumentCaptor.forClass(Documents.class);
        verify(documentRepository).save(saved.capture());
        assertTrue(saved.getValue().getLockedAt().isAfter(old), "lockedAt should be refreshed");
    }

    @Test
    @DisplayName("an expired lock is treated as free and can be taken over")
    void expiredLockCanBeTakenOver() {
        existing(lockedBy(alice, "Alice", LocalDateTime.now().minusMinutes(20)));

        DocumentLockStatusResponse status = service.acquire(documentId, bob, "Bob", "127.0.0.1");

        assertTrue(status.locked());
        assertEquals(bob, status.lockedByUserId());
        verify(auditLogService).createAuditLog(eq("DOCUMENT_LOCK_TAKEOVER"), eq(documentId), anyString(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("status reports an expired lock as unlocked")
    void statusHidesExpiredLock() {
        existing(lockedBy(alice, "Alice", LocalDateTime.now().minusMinutes(60)));

        DocumentLockStatusResponse status = service.status(documentId, bob);

        assertFalse(status.locked());
        assertNull(status.lockedByUsername());
    }

    @Test
    @DisplayName("assertCanMutate blocks another user but not the holder")
    void assertCanMutateRespectsHolder() {
        existing(lockedBy(alice, "Alice", LocalDateTime.now()));

        assertThrows(DocumentLockedException.class, () -> service.assertCanMutate(documentId, bob));
        assertDoesNotThrow(() -> service.assertCanMutate(documentId, alice));
    }

    @Test
    @DisplayName("assertCanMutate allows everyone once the lock has expired")
    void assertCanMutateIgnoresExpiredLock() {
        existing(lockedBy(alice, "Alice", LocalDateTime.now().minusMinutes(31)));

        assertDoesNotThrow(() -> service.assertCanMutate(documentId, bob));
    }

    @Test
    @DisplayName("the holder can release their own lock")
    void holderCanRelease() {
        existing(lockedBy(alice, "Alice", LocalDateTime.now()));

        DocumentLockStatusResponse status = service.release(documentId, alice, false, "127.0.0.1");

        assertFalse(status.locked());
        verify(auditLogService).createAuditLog(eq("DOCUMENT_UNLOCKED"), eq(documentId), anyString(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("a non-admin cannot release someone else's live lock")
    void nonAdminCannotReleaseOthers() {
        existing(lockedBy(alice, "Alice", LocalDateTime.now()));

        assertThrows(DocumentLockedException.class,
                () -> service.release(documentId, bob, false, "127.0.0.1"));
    }

    @Test
    @DisplayName("an admin can force-release someone else's lock")
    void adminCanForceRelease() {
        existing(lockedBy(alice, "Alice", LocalDateTime.now()));

        DocumentLockStatusResponse status = service.release(documentId, bob, true, "127.0.0.1");

        assertFalse(status.locked());
        verify(auditLogService).createAuditLog(
                eq("DOCUMENT_LOCK_FORCE_RELEASED"), eq(documentId), anyString(), eq("SUCCESS"));
    }

    @Test
    @DisplayName("releaseIfHeldBy only clears the lock for its actual holder")
    void releaseIfHeldByIsScoped() {
        Documents doc = lockedBy(alice, "Alice", LocalDateTime.now());
        existing(doc);

        service.releaseIfHeldBy(documentId, bob);
        verify(documentRepository, never()).save(any());

        service.releaseIfHeldBy(documentId, alice);
        verify(documentRepository).save(any(Documents.class));
        assertFalse(doc.isIs_locked());
    }
}
