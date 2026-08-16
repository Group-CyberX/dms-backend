package com.dms.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The join between a stored document and the ERP transaction it belongs to.
 *
 * This single row is the point of the whole project: it turns "someone remembers
 * which invoice matches PO-2026-0042" into a stored fact, which is what §5.3
 * (Document-ERP Linking) and criterion 10.2 are asking for.
 */
@Entity
// The document page asks "what is this attached to" on every open.
@Table(name = "document_erp_links", indexes = {
        @Index(name = "idx_erp_links_document", columnList = "document_id"),
        @Index(name = "idx_erp_links_transaction", columnList = "transaction_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentErpLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "link_id", updatable = false, nullable = false)
    private UUID linkId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    /** AUTO when matched from extracted text, MANUAL when a user linked it. */
    @Column(name = "link_type")
    @Builder.Default
    private String linkType = "AUTO";

    /** The reference that produced the match, kept for explainability. */
    @Column(name = "matched_reference")
    private String matchedReference;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
