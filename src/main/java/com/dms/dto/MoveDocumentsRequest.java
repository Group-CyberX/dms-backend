package com.dms.dto;

import java.util.List;
import java.util.UUID;

public class MoveDocumentsRequest {
    private List<UUID> documentIds;
    private UUID targetFolderId;

    public MoveDocumentsRequest() {}

    public List<UUID> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<UUID> documentIds) {
        this.documentIds = documentIds;
    }

    public UUID getTargetFolderId() {
        return targetFolderId;
    }

    public void setTargetFolderId(UUID targetFolderId) {
        this.targetFolderId = targetFolderId;
    }
}
