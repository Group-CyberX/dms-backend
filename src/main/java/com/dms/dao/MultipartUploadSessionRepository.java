package com.dms.dao;

import com.dms.models.MultipartUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MultipartUploadSessionRepository extends JpaRepository<MultipartUploadSession, UUID> {
    
    Optional<MultipartUploadSession> findBySessionId(UUID sessionId);
    
    List<MultipartUploadSession> findByUserId(UUID userId);
    
    List<MultipartUploadSession> findByDocumentId(UUID documentId);
    
    List<MultipartUploadSession> findByStatusAndDocumentId(MultipartUploadSession.UploadStatus status, UUID documentId);
}
