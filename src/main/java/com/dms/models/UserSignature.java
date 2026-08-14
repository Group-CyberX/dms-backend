package com.dms.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_signatures")
public class UserSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_signature_id", updatable = false, nullable = false)
    private UUID userSignatureId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "signature_type", length = 20, nullable = false)
    private String signatureType;

    // Update this field in your UserSignature.java file
    @Lob
    @Column(name = "signature_image", columnDefinition = "bytea")
    private byte[] signatureImage;

    @Column(name = "storage_url", length = 500)
    private String storageUrl;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getUserSignatureId() { return userSignatureId; }
    public void setUserSignatureId(UUID userSignatureId) { this.userSignatureId = userSignatureId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getSignatureType() { return signatureType; }
    public void setSignatureType(String signatureType) { this.signatureType = signatureType; }
    public byte[] getSignatureImage() { return signatureImage; }
    public void setSignatureImage(byte[] signatureImage) { this.signatureImage = signatureImage; }
    public String getStorageUrl() { return storageUrl; }
    public void setStorageUrl(String storageUrl) { this.storageUrl = storageUrl; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
