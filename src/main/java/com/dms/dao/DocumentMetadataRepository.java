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

    /**
     * How widely each metadata key is used, aggregated by the database.
     *
     * The policies screen previously loaded every metadata row in the system
     * and built these counts in Java, which also walked each row's document
     * association one at a time.
     */
    @Query("""
            SELECT m.key AS key,
                   COUNT(DISTINCT m.document.document_id) AS documentCount,
                   COUNT(DISTINCT m.value) AS distinctValues
            FROM DocumentMetadata m
            WHERE m.key IS NOT NULL
            GROUP BY m.key
            ORDER BY COUNT(DISTINCT m.document.document_id) DESC
            """)
    List<MetadataKeyUsage> findKeyUsage();

    /** A few example values per key, for the samples column. */
    @Query("""
            SELECT m.key AS key, m.value AS value
            FROM DocumentMetadata m
            WHERE m.key IS NOT NULL AND m.value IS NOT NULL
            GROUP BY m.key, m.value
            ORDER BY m.key, m.value
            """)
    List<MetadataKeyValue> findDistinctKeyValues();

    interface MetadataKeyUsage {
        String getKey();
        long getDocumentCount();
        long getDistinctValues();
    }

    interface MetadataKeyValue {
        String getKey();
        String getValue();
    }
}
