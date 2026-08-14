package com.dms.dto;

import java.util.List;
import java.util.UUID;

public class FolderRestoreResponse {
    private List<UUID> restoredFolderIds;
    private int documentsRestored;

    public FolderRestoreResponse() {}

    public FolderRestoreResponse(List<UUID> restoredFolderIds, int documentsRestored) {
        this.restoredFolderIds = restoredFolderIds;
        this.documentsRestored = documentsRestored;
    }

    public List<UUID> getRestoredFolderIds() {
        return restoredFolderIds;
    }

    public void setRestoredFolderIds(List<UUID> restoredFolderIds) {
        this.restoredFolderIds = restoredFolderIds;
    }

    public int getDocumentsRestored() {
        return documentsRestored;
    }

    public void setDocumentsRestored(int documentsRestored) {
        this.documentsRestored = documentsRestored;
    }
}
