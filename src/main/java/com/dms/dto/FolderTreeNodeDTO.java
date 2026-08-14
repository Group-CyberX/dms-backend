package com.dms.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FolderTreeNodeDTO {
    private UUID folder_id;
    private String name;
    private String path;
    private UUID parent_folder_id;
    private long documentCount;
    private long totalSize;
    private List<FolderTreeNodeDTO> children = new ArrayList<>();

    public FolderTreeNodeDTO() {
    }

    public UUID getFolder_id() {
        return folder_id;
    }

    public void setFolder_id(UUID folder_id) {
        this.folder_id = folder_id;
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

    public UUID getParent_folder_id() {
        return parent_folder_id;
    }

    public void setParent_folder_id(UUID parent_folder_id) {
        this.parent_folder_id = parent_folder_id;
    }

    public long getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(long documentCount) {
        this.documentCount = documentCount;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public List<FolderTreeNodeDTO> getChildren() {
        return children;
    }

    public void setChildren(List<FolderTreeNodeDTO> children) {
        this.children = children;
    }
}
