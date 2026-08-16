package com.dms.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A stored settings document, either for one user or for the organisation.
 *
 * Preferences are kept as a JSON payload rather than a column per option so
 * that adding a preference to the Settings screen does not require a schema
 * change. The set of keys the server will accept is still fixed - see
 * SettingsService - so the column cannot be used as free storage.
 */
@Entity
@Table(name = "settings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scope", "owner_id"}))
public class SettingEntry {

    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_ORG = "ORG";

    @Id
    @Column(name = "setting_id", updatable = false, nullable = false)
    private UUID settingId = UUID.randomUUID();

    /** USER or ORG. */
    @Column(name = "scope", nullable = false)
    private String scope;

    /** The user these preferences belong to; null for organisation settings. */
    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload = "{}";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UUID getSettingId() { return settingId; }
    public void setSettingId(UUID settingId) { this.settingId = settingId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
