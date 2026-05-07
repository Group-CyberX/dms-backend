package com.dms.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "UploadedPart")
@Table(name = "\"UploadedPart\"")
public class UploadedPart {
    
    @Id
    @Column(name = "part_id")
    private UUID partId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private MultipartUploadSession session;

    @Column(name = "part_number")
    private Integer partNumber;

    @Column(name = "e_tag")
    private String eTag;

    @Column(name = "part_size")
    private Long partSize;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    public UploadedPart() {}

    public UploadedPart(UUID partId, MultipartUploadSession session, Integer partNumber, 
                       String eTag, Long partSize) {
        this.partId = partId;
        this.session = session;
        this.partNumber = partNumber;
        this.eTag = eTag;
        this.partSize = partSize;
        this.uploadedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getPartId() { 
        return partId; 
    }

    public void setPartId(UUID partId) { 
        this.partId = partId; 
    }

    public MultipartUploadSession getSession() { 
        return session; 
    }

    public void setSession(MultipartUploadSession session) { 
        this.session = session; 
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

    public Long getPartSize() { 
        return partSize; 
    }

    public void setPartSize(Long partSize) { 
        this.partSize = partSize; 
    }

    public LocalDateTime getUploadedAt() { 
        return uploadedAt; 
    }

    public void setUploadedAt(LocalDateTime uploadedAt) { 
        this.uploadedAt = uploadedAt; 
    }
}
