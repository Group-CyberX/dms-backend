package com.dms.dao;

import com.dms.models.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MetadataRepository extends JpaRepository<DocumentMetadata, UUID> {

    List<DocumentMetadata> findByDocument_DocumentId(UUID documentId);

    List<DocumentMetadata> findByKeyAndValue(String key, String value);
}