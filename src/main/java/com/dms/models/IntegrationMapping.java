package com.dms.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Translates one ERP field name into the DMS metadata key it corresponds to.
 *
 * This table is what makes the connector universal: SAP calls it
 * "PurchaseOrderNo", Oracle calls it "PO_NUMBER", and the DMS only ever sees
 * "poNumber". Adding an ERP means inserting rows here, not writing code.
 */
@Entity
@Table(name = "integration_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mapping_id", updatable = false, nullable = false)
    private UUID mappingId;

    @Column(name = "erp_connection_id", nullable = false)
    private UUID erpConnectionId;

    /** Which record type this mapping applies to, e.g. PURCHASE_ORDER. */
    @Column(name = "entity_type")
    private String entityType;

    /** Field name as the ERP sends it. */
    @Column(name = "erp_field", nullable = false)
    private String erpField;

    /** Metadata key the DMS stores it under. */
    @Column(name = "dms_field", nullable = false)
    private String dmsField;

    /**
     * When true, this field holds the reference used to match documents to
     * transactions (the purchase order number, typically). Exactly one mapping
     * per entity type should carry this.
     */
    @Column(name = "is_reference_key")
    @Builder.Default
    private Boolean isReferenceKey = Boolean.FALSE;

    public boolean isReferenceKeyOrFalse() {
        return Boolean.TRUE.equals(isReferenceKey);
    }
}
