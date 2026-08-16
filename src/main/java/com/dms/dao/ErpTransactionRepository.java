package com.dms.dao;

import com.dms.models.ErpTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ErpTransactionRepository extends JpaRepository<ErpTransaction, UUID> {

    Optional<ErpTransaction> findByExternalRefIgnoreCase(String externalRef);

    Optional<ErpTransaction> findByErpConnectionIdAndExternalRefIgnoreCase(UUID erpConnectionId, String externalRef);

    /** Sync history for the admin screen - paged, newest first. */
    Page<ErpTransaction> findAllByOrderBySyncedAtDesc(Pageable pageable);

    List<ErpTransaction> findBySyncStatus(String syncStatus);

    long countBySyncStatus(String syncStatus);

    // ---- Console counters -------------------------------------------------
    // "Today" is measured from midnight, so the figures answer "what has this
    // integration done since the start of the day" rather than "ever".

    long countBySyncStatusAndSyncedAtAfter(String syncStatus, LocalDateTime since);

    long countByErpConnectionId(UUID erpConnectionId);

    @Modifying
    @Query("delete from ErpTransaction t where t.erpConnectionId = :connectionId")
    void deleteByErpConnectionId(@Param("connectionId") UUID connectionId);

    /**
     * Documents attached to records that came from one connection. Counted with
     * a join rather than by loading the links and grouping them in Java.
     */
    @Query("""
            select count(distinct l.documentId)
            from DocumentErpLink l, ErpTransaction t
            where l.transactionId = t.transactionId
              and t.erpConnectionId = :connectionId
            """)
    long countLinkedDocuments(@Param("connectionId") UUID connectionId);
}
