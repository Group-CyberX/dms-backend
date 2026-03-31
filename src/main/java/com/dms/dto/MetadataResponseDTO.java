package com.dms.dto;

import java.util.UUID;

public class MetadataResponseDTO {
    private UUID metadataId;
    private UUID documentId;
    private String key;
    private String value;

    public MetadataResponseDTO() {}

    public MetadataResponseDTO(UUID metadataId, UUID documentId, String key, String value) {
        this.metadataId = metadataId;
        this.documentId = documentId;
        this.key = key;
        this.value = value;
    }

    public UUID getMetadataId() {
        return metadataId;
    }

    public void setMetadataId(UUID metadataId) {
        this.metadataId = metadataId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}