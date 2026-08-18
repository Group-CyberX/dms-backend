package com.dms.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class DocumentResponse {
    private UUID document_id;
    private String title;
    private UUID owner_id;
    private String owner_name;
    private UUID folder_id;
    private UUID current_version_id;
    private LocalDateTime created_at;
    private LocalDateTime deleted_at;
    private Long file_size;
    private boolean is_locked;
    private boolean is_deleted;
    private String status;

    public DocumentResponse() {
    }

    public DocumentResponse(UUID document_id, String title, UUID owner_id, String owner_name, UUID folder_id,
                       UUID current_version_id, LocalDateTime created_at, LocalDateTime deleted_at, Long file_size, boolean is_locked, boolean is_deleted, String status) {
        this.document_id = document_id;
        this.title = title;
        this.owner_id = owner_id;
        this.owner_name = owner_name;
        this.folder_id = folder_id;
        this.current_version_id = current_version_id;
        this.created_at = created_at;
        this.deleted_at = deleted_at;
        this.file_size = file_size;
        this.is_locked = is_locked;
        this.is_deleted = is_deleted;
        this.status = status;
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

    public String getOwner_name() {
        return owner_name;
    }

    public void setOwner_name(String owner_name) {
        this.owner_name = owner_name;
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

    public LocalDateTime getDeleted_at() {
        return deleted_at;
    }

    public void setDeleted_at(LocalDateTime deleted_at) {
        this.deleted_at = deleted_at;
    }

    public Long getFile_size() {
        return file_size;
    }

    public void setFile_size(Long file_size) {
        this.file_size = file_size;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
