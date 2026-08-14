package com.dms.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "Folders")
@Table(name = "\"Folder\"")
public class Folders {
    @Id
    @Column(name = "folder_id")
    private UUID folder_id;

    @Column(name = "name")
    private String name;

    @Column(name = "parent_folder_id")
    private UUID parent_folder_id;

    @Column(name = "path")
    private String path;

    @Column(name = "is_deleted")
    private boolean is_deleted;

    @Column(name = "deleted_at")
    private LocalDateTime deleted_at;

    public Folders() {
    }

    public Folders(UUID folder_id,
                   String name,
                   UUID parent_folder_id,
                   String path) {
        this.folder_id = folder_id;
        this.name = name;
        this.parent_folder_id = parent_folder_id;
        this.path = path;
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

    public UUID getParent_folder_id() {
        return parent_folder_id;
    }

    public void setParent_folder_id(UUID parent_folder_id) {
        this.parent_folder_id = parent_folder_id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isIs_deleted() {
        return is_deleted;
    }

    public void setIs_deleted(boolean is_deleted) {
        this.is_deleted = is_deleted;
    }

    public LocalDateTime getDeleted_at() {
        return deleted_at;
    }

    public void setDeleted_at(LocalDateTime deleted_at) {
        this.deleted_at = deleted_at;
    }

    @Override
    public String toString() {
        return "Folders{" +
                "folder_id=" + folder_id +
                ", name='" + name + '\'' +
                ", parent_folder_id=" + parent_folder_id +
                ", path='" + path + '\'' +
                ", is_deleted=" + is_deleted +
                '}';
    }
}
