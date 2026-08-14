package com.dms.dto;

import java.util.UUID;

public class FolderCreateRequest {
    private String name;
    private UUID parentFolderId; // null means root

    public FolderCreateRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(UUID parentFolderId) {
        this.parentFolderId = parentFolderId;
    }
}
