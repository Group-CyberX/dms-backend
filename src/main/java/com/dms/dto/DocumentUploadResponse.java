package com.dms.dto;

import java.util.UUID;

public class DocumentUploadResponse {
    private UUID documentId;
    private UUID versionId;
    private String title;
    private String message;
    private boolean success;

    public DocumentUploadResponse() {}

    public DocumentUploadResponse(UUID documentId, UUID versionId, String title, String message, boolean success) {
        this.documentId = documentId;
        this.versionId = versionId;
        this.title = title;
        this.message = message;
        this.success = success;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
