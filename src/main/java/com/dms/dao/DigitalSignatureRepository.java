package com.dms.dao;

import com.dms.models.DigitalSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface DigitalSignatureRepository extends JpaRepository<DigitalSignature, UUID> {
    List<DigitalSignature> findByDocumentVersionId(UUID documentVersionId);
}
