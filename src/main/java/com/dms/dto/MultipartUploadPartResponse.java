package com.dms.dto;

public class MultipartUploadPartResponse {
    private Integer partNumber;
    private String eTag;
    private Long uploadedBytes;
    private Long totalBytes;

    public MultipartUploadPartResponse() {}

    public MultipartUploadPartResponse(Integer partNumber, String eTag, Long uploadedBytes, Long totalBytes) {
        this.partNumber = partNumber;
        this.eTag = eTag;
        this.uploadedBytes = uploadedBytes;
        this.totalBytes = totalBytes;
    }

    public Integer getPartNumber() { 
        return partNumber; 
    }

    public void setPartNumber(Integer partNumber) { 
        this.partNumber = partNumber; 
    }

    public String getETag() { 
        return eTag; 
    }

    public void setETag(String eTag) { 
        this.eTag = eTag; 
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
}
