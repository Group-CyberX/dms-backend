package com.dms.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "MultipartUploadSession")
@Table(name = "\"MultipartUploadSession\"")
public class MultipartUploadSession {
    
    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "total_size")
    private Long totalSize;

    @Column(name = "uploaded_bytes")
    private Long uploadedBytes;

    @Column(name = "s3_upload_id")
    private String s3UploadId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private UploadStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "category")
    private String category;

    @Column(name = "s3_bucket_key")
    private String s3BucketKey;

    @Column(name = "title")
    private String title;

    @Column(name = "tags")
    private String tags;

    @Column(name = "description", length = 1000)
    private String description;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UploadedPart> parts = new ArrayList<>();

    public enum UploadStatus {
        IN_PROGRESS,
        COMPLETED,
        ABORTED,
        FAILED
    }

    public MultipartUploadSession() {}

    public MultipartUploadSession(UUID sessionId, UUID documentId, UUID userId, String fileName, Long totalSize, String s3UploadId) {
        this.sessionId = sessionId;
        this.documentId = documentId;
        this.userId = userId;
        this.fileName = fileName;
        this.totalSize = totalSize;
        this.uploadedBytes = 0L;
        this.s3UploadId = s3UploadId;
        this.status = UploadStatus.IN_PROGRESS;
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getSessionId() { 
        return sessionId; 
    }

    public void setSessionId(UUID sessionId) { 
        this.sessionId = sessionId; 
    }

    public UUID getDocumentId() { 
        return documentId; 
    }

    public void setDocumentId(UUID documentId) { 
        this.documentId = documentId; 
    }

    public UUID getUserId() { 
        return userId; 
    }

    public void setUserId(UUID userId) { 
        this.userId = userId; 
    }

    public String getFileName() { 
        return fileName; 
    }

    public void setFileName(String fileName) { 
        this.fileName = fileName; 
    }

    public Long getTotalSize() { 
        return totalSize; 
    }

    public void setTotalSize(Long totalSize) { 
        this.totalSize = totalSize; 
    }

    public Long getUploadedBytes() { 
        return uploadedBytes; 
    }

    public void setUploadedBytes(Long uploadedBytes) { 
        this.uploadedBytes = uploadedBytes; 
    }

    public String getS3UploadId() { 
        return s3UploadId; 
    }

    public void setS3UploadId(String s3UploadId) {
        this.s3UploadId = s3UploadId; 
    }

    public UploadStatus getStatus() { 
        return status; 
    }

    public void setStatus(UploadStatus status) { 
        this.status = status; 
    }

    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }

    public void setCreatedAt(LocalDateTime createdAt) { 
        this.createdAt = createdAt; 
    }

    public LocalDateTime getLastActivity() { 
        return lastActivity; 
    }

    public void setLastActivity(LocalDateTime lastActivity) { 
        this.lastActivity = lastActivity; 
    }

    public UUID getFolderId() { 
        return folderId; 
    }

    public void setFolderId(UUID folderId) { 
        this.folderId = folderId; 
    }

    public String getCategory() { 
        return category; 
    }

    public void setCategory(String category) { 
        this.category = category; 
    }

    public String getS3BucketKey() { 
        return s3BucketKey; 
    }

    public void setS3BucketKey(String s3BucketKey) { 
        this.s3BucketKey = s3BucketKey; 
    }

    public List<UploadedPart> getParts() { 
        return parts; 
    }

    public void setParts(List<UploadedPart> parts) { 
        this.parts = parts; 
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}