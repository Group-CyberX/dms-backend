package com.dms.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "digital_signatures")
public class DigitalSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "signature_id", updatable = false, nullable = false)
    private UUID signatureId;

    @Column(name = "document_version_id", nullable = false)
    private UUID documentVersionId;

    @Column(name = "signature_user_id", nullable = false)
    private UUID signatureUserId;

    @Column(name = "hash", length = 255, nullable = false)
    private String hash;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private LocalDateTime signedAt;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "algorithm", length = 50)
    private String algorithm;

    // No @Lob here: on PostgreSQL that maps byte[] to the "oid" large-object
    // type, while the column is declared bytea - the insert then fails with
    // "column tsa_token is of type bytea but expression is of type oid".
    // Plain byte[] maps to bytea, which is what we want for an RFC 3161 token.
    @Column(name = "tsa_token", columnDefinition = "bytea")
    private byte[] tsaToken;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "VALID";

    @PrePersist
    protected void onSign() {
        this.signedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getSignatureId() { return signatureId; }
    public void setSignatureId(UUID signatureId) { this.signatureId = signatureId; }
    public UUID getDocumentVersionId() { return documentVersionId; }
    public void setDocumentVersionId(UUID documentVersionId) { this.documentVersionId = documentVersionId; }
    public UUID getSignatureUserId() { return signatureUserId; }
    public void setSignatureUserId(UUID signatureUserId) { this.signatureUserId = signatureUserId; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public byte[] getTsaToken() { return tsaToken; }
    public void setTsaToken(byte[] tsaToken) { this.tsaToken = tsaToken; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
