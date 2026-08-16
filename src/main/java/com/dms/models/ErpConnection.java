package com.dms.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A configured ERP system the DMS talks to.
 *
 * The connector is deliberately generic: it holds a base URL and credentials and
 * speaks REST. Supporting a different ERP is a row in this table plus field
 * mappings, not new Java - which is what "universal connector" means in §4.2.2.
 */
@Entity
@Table(name = "erp_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "connection_id", updatable = false, nullable = false)
    private UUID connectionId;

    /** Display name, e.g. "SAP S/4HANA - Production". */
    @Column(name = "name", nullable = false)
    private String name;

    /** SAP | ORACLE | DYNAMICS | GENERIC - drives nothing but the badge today. */
    @Column(name = "erp_type")
    private String erpType;

    /** Base URL of the ERP's REST API. */
    @Column(name = "api_endpoint", nullable = false)
    private String apiEndpoint;

    /** API_KEY | BASIC | NONE */
    @Column(name = "auth_type")
    private String authType;

    /**
     * The credential, AES-encrypted at rest. Never returned by the API - the
     * response DTO has no field for it.
     */
    @Column(name = "auth_config", columnDefinition = "text")
    private String authConfig;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = Boolean.TRUE;

    /** Result of the last connectivity test or sync: UNKNOWN | OK | FAILED. */
    @Column(name = "status")
    @Builder.Default
    private String status = "UNKNOWN";

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isActiveOrFalse() {
        return Boolean.TRUE.equals(isActive);
    }
}
