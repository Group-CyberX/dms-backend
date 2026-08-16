package com.dms.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A business record pulled from an ERP - a purchase order, invoice, or similar.
 *
 * Doubles as the sync log: sync_status, retry_count and last_error_message are
 * what the admin screen shows and what the retry button acts on, covering the
 * "integration errors are logged and recoverable" criterion in §10.2.
 */
@Entity
@Table(name = "erp_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id", updatable = false, nullable = false)
    private UUID transactionId;

    @Column(name = "erp_connection_id", nullable = false)
    private UUID erpConnectionId;

    /** PURCHASE_ORDER | INVOICE | ... */
    @Column(name = "transaction_type")
    private String transactionType;

    /**
     * The ERP's own identifier, e.g. "PO-2026-0042". This is the value matched
     * against text extracted from uploaded documents.
     */
    @Column(name = "external_ref", nullable = false)
    private String externalRef;

    /** The mapped record as JSON, so the UI can show whatever the ERP sent. */
    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    /** SUCCESS | FAILED */
    @Column(name = "sync_status")
    @Builder.Default
    private String syncStatus = "SUCCESS";

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "synced_at")
    @Builder.Default
    private LocalDateTime syncedAt = LocalDateTime.now();
}
