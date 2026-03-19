package com.dms.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "Documents")
@Table(name = "\"Document\"")
public class Documents {
    @Id
    @Column(name = "document_id")
    private UUID document_id;

    @Column(name = "title")
    private String title;

    @Column(name = "owner_id")
    private UUID owner_id;

    @Column(name = "folder_id")
    private UUID folder_id;

    @Column(name = "current_version_id")
    private UUID current_version_id;

    @Column(name = "created_at")
    private LocalDateTime created_at;

    @Column(name = "is_locked")
    private boolean is_locked;

    @Column(name = "is_deleted")
    private boolean is_deleted;

    public Documents(UUID document_id,
                   String title,
                   UUID owner_id,
                   UUID folder_id,
                   UUID current_version_id,
                   LocalDateTime created_at,
                   boolean is_locked,
                   boolean is_deleted) {
        this.document_id = document_id;
        this.title = title;
        this.owner_id = owner_id;
        this.folder_id = folder_id;
        this.current_version_id = current_version_id;
        this.created_at = created_at;
        this.is_locked = is_locked;
        this.is_deleted = is_deleted;
    }

    public Documents() {

    }

    public UUID getDocument_id() {
        return document_id;
    }

    public void setDocument_id(UUID document_id) {
        this.document_id = document_id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getOwner_id() {
        return owner_id;
    }

    public void setOwner_id(UUID owner_id) {
        this.owner_id = owner_id;
    }

    public UUID getFolder_id() {
        return folder_id;
    }

    public void setFolder_id(UUID folder_id) {
        this.folder_id = folder_id;
    }

    public UUID getCurrent_version_id() {
        return current_version_id;
    }

    public void setCurrent_version_id(UUID current_version_id) {
        this.current_version_id = current_version_id;
    }

    public LocalDateTime getCreated_at() {
        return created_at;
    }

    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }

    public boolean isIs_locked() {
        return is_locked;
    }

    public void setIs_locked(boolean is_locked) {
        this.is_locked = is_locked;
    }

    public boolean isIs_deleted() {
        return is_deleted;
    }

    public void setIs_deleted(boolean is_deleted) {
        this.is_deleted = is_deleted;
    }

    @Override
    public String toString() {
        return "Folders{" +
                "document_id=" + document_id +
                ", title='" + title + '\'' +
                ", owner_id=" + owner_id +
                ", folder_id=" + folder_id +
                ", current_version_id=" + current_version_id +
                ", created_at=" + created_at +
                ", is_locked=" + is_locked +
                ", is_deleted=" + is_deleted +
                '}';
    }
}
