package com.dms.dao;

import com.dms.models.UploadedPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UploadedPartRepository extends JpaRepository<UploadedPart, UUID> {
    
    List<UploadedPart> findBySessionSessionId(UUID sessionId);
    
    UploadedPart findBySessionSessionIdAndPartNumber(UUID sessionId, Integer partNumber);
}
