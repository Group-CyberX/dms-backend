package com.dms.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processing_jobs")
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "document_version_id", nullable = false)
    private UUID documentVersionId;

    @Column(name = "job_type")
    private String jobType;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Create an empty processing job entity
    public ProcessingJob() {
    }

    // Create a processing job with version, type, and status
    public ProcessingJob(UUID documentVersionId, String jobType, String status) {
        this.documentVersionId = documentVersionId;
        this.jobType = jobType;
        this.status = status;
    }

    // Get job ID
    public UUID getJobId() {
        return jobId;
    }

    // Set job ID
    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    // Get document version ID
    public UUID getDocumentVersionId() {
        return documentVersionId;
    }

    // Set document version ID
    public void setDocumentVersionId(UUID documentVersionId) {
        this.documentVersionId = documentVersionId;
    }

    // Get job type
    public String getJobType() {
        return jobType;
    }

    // Set job type
    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    // Get job status
    public String getStatus() {
        return status;
    }

    // Set job status
    public void setStatus(String status) {
        this.status = status;
    }

    // Get job creation time
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Set job creation time
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
