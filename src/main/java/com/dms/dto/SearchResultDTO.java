package com.dms.dto;

import java.util.List;
import java.util.UUID;

public class SearchResultDTO {
    private UUID documentId;
    private String title;
    private List<MetadataDTO> metadata;

    public SearchResultDTO(UUID documentId, String title, List<MetadataDTO> metadata) {
        this.documentId = documentId;
        this.title = title;
        this.metadata = metadata;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<MetadataDTO> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<MetadataDTO> metadata) {
        this.metadata = metadata;
    }
}
