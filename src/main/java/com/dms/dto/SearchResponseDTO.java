package com.dms.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SearchResponseDTO {
    private UUID documentId;
    private String title;
    private String createdAt;
    private String owner;
    private String status;
    private String description;
    private List<String> tags;
    private Map<String, String> metadata;

    public SearchResponseDTO() {}

    public SearchResponseDTO(UUID documentId, String title) {
        this.documentId = documentId;
        this.title = title;
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

    public String getCreatedAt() { 
        return createdAt;
    }
    public void setCreatedAt(String createdAt) {
         this.createdAt = createdAt; 
    }

    public String getOwner() {
         return owner; 
    }
    public void setOwner(String owner) { 
        this.owner = owner; 
    }

    public String getStatus() { 
        return status;
    }
    public void setStatus(String status) { 
        this.status = status; 
    }

    public String getDescription() { 
        return description; 
    }
    public void setDescription(String description) {
         this.description = description; 
    }

    public List<String> getTags() {
         return tags; 
    }
    public void setTags(List<String> tags) { 
        this.tags = tags; 
    }

    public Map<String, String> getMetadata() { 
        return metadata; 
    }
    public void setMetadata(Map<String, String> metadata) {
         this.metadata = metadata; 
    }
}