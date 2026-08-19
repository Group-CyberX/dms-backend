package com.dms.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


import java.util.UUID;

@Entity(name = "Documents")
// The document list is always "not deleted, in this folder", and the owner
// filter backs the per-user view.
@Table(name = "\"Document\"", indexes = {
        @Index(name = "idx_document_deleted_folder", columnList = "is_deleted, folder_id"),
        @Index(name = "idx_document_owner", columnList = "owner_id"),
        // The list is always sorted newest-first; without this the database
        // sorted every matching row before taking a page of ten.
        @Index(name = "idx_document_deleted_created", columnList = "is_deleted, created_at"),
        // Serves the "new uploads" filter administrators work from.
        @Index(name = "idx_document_deleted_status", columnList = "is_deleted, status")
})
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

    @Column(name = "deleted_at")
    private LocalDateTime deleted_at;

    @Column(name = "file_size")
    private Long file_size;

    @Column(name = "is_locked")
    private boolean is_locked;

    @Column(name = "is_deleted")
    private boolean is_deleted;

    /**
     * Where the document has reached in its life: NEW until a workflow is
     * started on it, then whatever that workflow reports.
     *
     * Stored on the row rather than derived from workflow_instance on every
     * read. workflow_instance.document_id is a varchar while this table's key
     * is a uuid, so any join between them needs a cast, and a cast on a join
     * column cannot use an index - on the documents list that is the whole
     * table scanned per page. Kept in step by DocumentUploadService when a
     * document is created and by WorkflowService when a workflow starts.
     */
    @Column(name = "status")
    private String status;

    // ---- Edit lock -------------------------------------------------------
    // Who currently holds the document for editing, and since when. The lock
    // is treated as expired once it is older than the configured timeout, so
    // these three fields plus is_locked describe the whole lock state.

    @Column(name = "locked_by_user_id")
    private UUID lockedByUserId;

    @Column(name = "locked_by_username")
    private String lockedByUsername;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

   

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentMetadata> metadata = new ArrayList<>();

    public Documents(UUID document_id,
                   String title,
                   UUID owner_id,
                   UUID folder_id,
                   UUID current_version_id,
                   LocalDateTime created_at,
                   LocalDateTime deleted_at,
                   Long file_size,
                   boolean is_locked,
                   boolean is_deleted) {
        this.document_id = document_id;
        this.title = title;
        this.owner_id = owner_id;
        this.folder_id = folder_id;
        this.current_version_id = current_version_id;
        this.created_at = created_at;
        this.deleted_at = deleted_at;
        this.file_size = file_size;
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

    public UUID getLockedByUserId() {
        return lockedByUserId;
    }

    public void setLockedByUserId(UUID lockedByUserId) {
        this.lockedByUserId = lockedByUserId;
    }

    public String getLockedByUsername() {
        return lockedByUsername;
    }

    public void setLockedByUsername(String lockedByUsername) {
        this.lockedByUsername = lockedByUsername;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
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
                ", deleted_at=" + deleted_at +
                ", file_size=" + file_size +
                ", is_locked=" + is_locked +
                ", is_deleted=" + is_deleted +
            '}';
    }

    

    public List<DocumentMetadata> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<DocumentMetadata> metadata) {
        this.metadata = metadata;
    }
}
