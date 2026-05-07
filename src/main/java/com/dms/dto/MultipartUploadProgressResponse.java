package com.dms.dto;

import java.util.UUID;

public class MultipartUploadProgressResponse {
    private UUID sessionId;
    private Long uploadedBytes;
    private Long totalBytes;
    private Double percentComplete;
    private String status;

    public MultipartUploadProgressResponse() {}

    public MultipartUploadProgressResponse(UUID sessionId, Long uploadedBytes, Long totalBytes, 
                                          Double percentComplete, String status) {
        this.sessionId = sessionId;
        this.uploadedBytes = uploadedBytes;
        this.totalBytes = totalBytes;
        this.percentComplete = percentComplete;
        this.status = status;
    }

    public UUID getSessionId() { 
        return sessionId; 
    }

    public void setSessionId(UUID sessionId) { 
        this.sessionId = sessionId; 
    }

    public Long getUploadedBytes() { 
        return uploadedBytes; 
    }

    public void setUploadedBytes(Long uploadedBytes) { 
        this.uploadedBytes = uploadedBytes; 
    }

    public Long getTotalBytes() { 
        return totalBytes; 
    }

    public void setTotalBytes(Long totalBytes) { 
        this.totalBytes = totalBytes; 
    }

    public Double getPercentComplete() { 
        return percentComplete; 
    }

    public void setPercentComplete(Double percentComplete) { 
        this.percentComplete = percentComplete; 
    }

    public String getStatus() { 
        return status; 
    }

    public void setStatus(String status) { 
        this.status = status; 
    }
}
