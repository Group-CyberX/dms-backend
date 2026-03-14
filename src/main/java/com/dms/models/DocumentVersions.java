package com.dms.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "DocumentVersions")
@Table(name = "\"DocumentVersion\"")
public class DocumentVersions {
    @Id
    @Column(name = "version_id")
    private UUID version_id;

    @Column(name = "document_id")
    private UUID document_id;

    @Column(name = "version_number")
    private String version_number;

    @Column(name = "s3_bucket_key")
    private String s3_bucket_key;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "ocr_content")
    private String ocr_content;

    @Column(name = "created_at")
    private LocalDateTime created_at;

    public DocumentVersions() {
    }

    public DocumentVersions(UUID version_id,
                            UUID document_id,
                            String version_number,
                            String s3_bucket_key,
                            String checksum,
                            String ocr_content,
                            LocalDateTime created_at) {
        this.version_id = version_id;
        this.document_id = document_id;
        this.version_number = version_number;
        this.s3_bucket_key = s3_bucket_key;
        this.checksum = checksum;
        this.ocr_content = ocr_content;
        this.created_at = created_at;
    }

    public UUID getVersion_id() {
        return version_id;
    }

    public void setVersion_id(UUID version_id) {
        this.version_id = version_id;
    }

    public UUID getDocument_id() {
        return document_id;
    }

    public void setDocument_id(UUID document_id) {
        this.document_id = document_id;
    }

    public String getVersion_number() {
        return version_number;
    }

    public void setVersion_number(String version_number) {
        this.version_number = version_number;
    }

    public String getS3_bucket_key() {
        return s3_bucket_key;
    }

    public void setS3_bucket_key(String s3_bucket_key) {
        this.s3_bucket_key = s3_bucket_key;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getOcr_content() {
        return ocr_content;
    }

    public void setOcr_content(String ocr_content) {
        this.ocr_content = ocr_content;
    }

    public LocalDateTime getCreated_at() {
        return created_at;
    }

    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }

    @Override
    public String toString() {
        return "DocumentVersions{" +
                "version_id=" + version_id +
                ", document_id=" + document_id +
                ", version_number='" + version_number + '\'' +
                ", s3_bucket_key='" + s3_bucket_key + '\'' +
                ", checksum='" + checksum + '\'' +
                ", ocr_content='" + ocr_content + '\'' +
                ", created_at=" + created_at +
                '}';
    }
}
