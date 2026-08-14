package com.dms.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row in the recycle bin's folder list: the root of a folder subtree
 * that was cascade-deleted together, with rolled-up counts of what's inside.
 */
public class FolderTrashItemDTO {
    private UUID folderId;
    private String name;
    private String path;
    private LocalDateTime deletedAt;
    private int documentCount;
    private int subfolderCount;

    public FolderTrashItemDTO() {}

    public FolderTrashItemDTO(UUID folderId, String name, String path, LocalDateTime deletedAt,
                               int documentCount, int subfolderCount) {
        this.folderId = folderId;
        this.name = name;
        this.path = path;
        this.deletedAt = deletedAt;
        this.documentCount = documentCount;
        this.subfolderCount = subfolderCount;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    public int getSubfolderCount() {
        return subfolderCount;
    }

    public void setSubfolderCount(int subfolderCount) {
        this.subfolderCount = subfolderCount;
    }
}
