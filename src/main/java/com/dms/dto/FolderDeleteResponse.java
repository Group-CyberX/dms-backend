package com.dms.dto;

import java.util.List;
import java.util.UUID;

public class FolderDeleteResponse {
    private List<UUID> deletedFolderIds;
    private int documentsMovedToRecycleBin;

    public FolderDeleteResponse() {}

    public FolderDeleteResponse(List<UUID> deletedFolderIds, int documentsMovedToRecycleBin) {
        this.deletedFolderIds = deletedFolderIds;
        this.documentsMovedToRecycleBin = documentsMovedToRecycleBin;
    }

    public List<UUID> getDeletedFolderIds() {
        return deletedFolderIds;
    }

    public void setDeletedFolderIds(List<UUID> deletedFolderIds) {
        this.deletedFolderIds = deletedFolderIds;
    }

    public int getDocumentsMovedToRecycleBin() {
        return documentsMovedToRecycleBin;
    }

    public void setDocumentsMovedToRecycleBin(int documentsMovedToRecycleBin) {
        this.documentsMovedToRecycleBin = documentsMovedToRecycleBin;
    }
}
