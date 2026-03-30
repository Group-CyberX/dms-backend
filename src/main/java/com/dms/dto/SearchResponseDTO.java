package com.dms.dto;

import java.util.UUID;

public class SearchResponseDTO {
    private UUID documentId;
    private String title;

    public SearchResponseDTO() {}

    public SearchResponseDTO(UUID documentId, String title) {
        this.documentId = documentId;
        this.title = title;
    }

    public SearchResponseDTO(String title) {
        this.title = title;
    }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}