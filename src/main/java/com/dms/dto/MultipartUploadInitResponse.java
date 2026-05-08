package com.dms.dto;

import java.util.UUID;

public class MultipartUploadInitResponse {
    private UUID sessionId;
    private String s3UploadId;
    private Long partSize;

    public MultipartUploadInitResponse() {}

    public MultipartUploadInitResponse(UUID sessionId, String s3UploadId, Long partSize) {
        this.sessionId = sessionId;
        this.s3UploadId = s3UploadId;
        this.partSize = partSize;
    }

    public UUID getSessionId() { 
        return sessionId; 
    }

    public void setSessionId(UUID sessionId) { 
        this.sessionId = sessionId; 
    }

    public String getS3UploadId() { 
        return s3UploadId; 
    }

    public void setS3UploadId(String s3UploadId) { 
        this.s3UploadId = s3UploadId; 
    }

    public Long getPartSize() { 
        return partSize; 
    }

    public void setPartSize(Long partSize) { 
        this.partSize = partSize; 
    }
}
