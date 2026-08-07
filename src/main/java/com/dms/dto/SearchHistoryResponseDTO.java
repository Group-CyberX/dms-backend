package com.dms.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class SearchHistoryResponseDTO {

    private UUID searchId;
    private String query;
    private UUID documentId;
    private String documentTitle;
    private LocalDateTime timestamp;

    public SearchHistoryResponseDTO() {
    }

    public SearchHistoryResponseDTO(UUID searchId, String query, UUID documentId, String documentTitle, LocalDateTime timestamp) {
        this.searchId = searchId;
        this.query = query;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.timestamp = timestamp;
    }

    public UUID getSearchId() {
        return searchId;
    }

    public void setSearchId(UUID searchId) {
        this.searchId = searchId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
