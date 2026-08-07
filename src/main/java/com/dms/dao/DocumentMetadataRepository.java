package com.dms.dao;

import com.dms.models.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing document metadata CRUD operations.
 */
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, UUID> {

    // Find all metadata entries for a specific document
    @Query("SELECT m FROM DocumentMetadata m WHERE m.document.document_id = :documentId")
    List<DocumentMetadata> findByDocument_document_id(@Param("documentId") UUID documentId);

    // Find a specific metadata entry by document ID and metadata key
    @Query("SELECT m FROM DocumentMetadata m WHERE m.document.document_id = :documentId AND m.key = :key")
    Optional<DocumentMetadata> findByDocument_document_idAndKey(@Param("documentId") UUID documentId, @Param("key") String key);

    // Delete a metadata entry by document ID and metadata key
    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentMetadata m WHERE m.document.document_id = :documentId AND m.key = :key")
    void deleteByDocument_document_idAndKey(@Param("documentId") UUID documentId, @Param("key") String key);
}
