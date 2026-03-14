package com.dms.dto;

import java.util.UUID;

public class UploadDocumentRequest {
    private String title;
    private UUID folderId;
    private String category;
    private String tags;
    private String description;

    public UploadDocumentRequest() {}

    public UploadDocumentRequest(String title, UUID folderId, String category, String tags, String description) {
        this.title = title;
        this.folderId = folderId;
        this.category = category;
        this.tags = tags;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
