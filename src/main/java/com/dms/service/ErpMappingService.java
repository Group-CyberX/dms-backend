package com.dms.service;

import com.dms.dao.IntegrationMappingRepository;
import com.dms.models.IntegrationMapping;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Translates ERP records into DMS metadata using the configured field mappings.
 *
 * This class is the whole of the "universal connector" claim in §4.2.2: SAP sends
 * PurchaseOrderNo, Oracle sends PO_NUMBER, and neither name appears anywhere in
 * the DMS. Supporting a new ERP is rows in integration_mappings, not Java.
 */
@Service
public class ErpMappingService {

    private final IntegrationMappingRepository mappingRepository;

    public ErpMappingService(IntegrationMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    /**
     * Renames the keys of an ERP record to the DMS names configured for this
     * connection. Fields with no mapping are dropped, so an ERP adding columns
     * cannot silently pollute our metadata.
     */
    public Map<String, Object> toDmsFields(UUID connectionId, String entityType, Map<String, Object> erpRecord) {
        List<IntegrationMapping> mappings = mappingsFor(connectionId, entityType);

        Map<String, Object> result = new LinkedHashMap<>();
        for (IntegrationMapping mapping : mappings) {
            if (erpRecord.containsKey(mapping.getErpField())) {
                result.put(mapping.getDmsField(), erpRecord.get(mapping.getErpField()));
            }
        }
        return result;
    }

    /**
     * The value that identifies this record - the purchase order number, usually.
     * Taken from the mapping flagged as the reference key; falls back to a few
     * common names so a connection with no mappings still syncs something useful.
     */
    public Optional<String> extractReference(UUID connectionId, String entityType, Map<String, Object> erpRecord) {
        for (IntegrationMapping mapping : mappingsFor(connectionId, entityType)) {
            if (mapping.isReferenceKeyOrFalse()) {
                Object value = erpRecord.get(mapping.getErpField());
                if (value != null && !value.toString().isBlank()) {
                    return Optional.of(value.toString().trim());
                }
            }
        }

        for (String fallback : List.of("PurchaseOrderNo", "InvoiceNo", "DocumentNo", "Reference", "Id")) {
            Object value = erpRecord.get(fallback);
            if (value != null && !value.toString().isBlank()) {
                return Optional.of(value.toString().trim());
            }
        }

        return Optional.empty();
    }

    /** Mappings for a specific entity type, falling back to the connection's full set. */
    private List<IntegrationMapping> mappingsFor(UUID connectionId, String entityType) {
        if (entityType != null && !entityType.isBlank()) {
            List<IntegrationMapping> scoped =
                    mappingRepository.findByErpConnectionIdAndEntityType(connectionId, entityType);
            if (!scoped.isEmpty()) {
                return scoped;
            }
        }
        return mappingRepository.findByErpConnectionId(connectionId);
    }

    /**
     * Sensible starting mappings for a brand-new connection, so the demo does not
     * begin with an empty mapping table. Which field names get seeded depends on
     * which demo backend the connection is actually going to talk to - the bundled
     * mock ERP and the standalone Nexus ERP use different field-naming conventions
     * on purpose (see ERP_INTEGRATION_EXPLAINED.md), and seeding the wrong one means
     * every sync silently matches nothing. A real deployment would still edit these
     * in the UI to match whatever that vendor's API actually returns.
     */
    public List<IntegrationMapping> defaultMappings(UUID connectionId, String erpType) {
        if ("NEXUS".equalsIgnoreCase(erpType)) {
            return List.of(
                    mapping(connectionId, "PURCHASE_ORDER", "poNumber", "poNumber", true),
                    mapping(connectionId, "PURCHASE_ORDER", "vendorName", "vendor", false),
                    mapping(connectionId, "PURCHASE_ORDER", "amount", "amount", false),
                    mapping(connectionId, "PURCHASE_ORDER", "status", "erpStatus", false)
                    // Nexus ERP has no invoices endpoint, so no INVOICE rows are seeded.
            );
        }

        // GENERIC / SAP / ORACLE / DYNAMICS / INFOR / EPICOR all fall back to the
        // bundled mock ERP's field names - the only one of these actually
        // reachable without a real vendor tenant.
        return List.of(
                mapping(connectionId, "PURCHASE_ORDER", "PurchaseOrderNo", "poNumber", true),
                mapping(connectionId, "PURCHASE_ORDER", "VendorName", "vendor", false),
                mapping(connectionId, "PURCHASE_ORDER", "TotalAmount", "amount", false),
                mapping(connectionId, "PURCHASE_ORDER", "Currency", "currency", false),
                mapping(connectionId, "PURCHASE_ORDER", "Status", "erpStatus", false),
                mapping(connectionId, "INVOICE", "InvoiceNo", "invoiceNumber", true),
                mapping(connectionId, "INVOICE", "PurchaseOrderNo", "poNumber", false),
                mapping(connectionId, "INVOICE", "VendorName", "vendor", false),
                mapping(connectionId, "INVOICE", "TotalAmount", "amount", false),
                mapping(connectionId, "INVOICE", "DueDate", "dueDate", false)
        );
    }

    private IntegrationMapping mapping(UUID connectionId, String entityType,
                                       String erpField, String dmsField, boolean isReference) {
        return IntegrationMapping.builder()
                .erpConnectionId(connectionId)
                .entityType(entityType)
                .erpField(erpField)
                .dmsField(dmsField)
                .isReferenceKey(isReference)
                .build();
    }
}
