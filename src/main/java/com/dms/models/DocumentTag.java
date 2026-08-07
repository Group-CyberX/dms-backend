package com.dms.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity(name = "DocumentTag")
@Table(name = "\"DocumentTag\"")
public class DocumentTag {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "tag_id")
    private UUID tagId;

    public DocumentTag() {
    }

    public DocumentTag(UUID id,
                       UUID documentId,
                       UUID tagId) {
        this.id = id;
        this.documentId = documentId;
        this.tagId = tagId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public void setTagId(UUID tagId) {
        this.tagId = tagId;
    }

    @Override
    public String toString() {
        return "DocumentTag{" +
                "id=" + id +
                ", documentId=" + documentId +
                ", tagId=" + tagId +
                '}';
    }
}
