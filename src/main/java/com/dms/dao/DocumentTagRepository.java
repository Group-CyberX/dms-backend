package com.dms.dao;

import com.dms.models.DocumentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Repository for managing document-tag associations.
 */
@Repository
public interface DocumentTagRepository extends JpaRepository<DocumentTag, UUID> {
    
    // Find all tags associated with a specific document
    @Query("SELECT dt FROM DocumentTag dt WHERE dt.documentId = :documentId")
    List<DocumentTag> findByDocumentId(@Param("documentId") UUID documentId);

    boolean existsByDocumentIdAndTagId(UUID documentId, UUID tagId);
    
    // Delete all tags associated with a specific document
    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentTag dt WHERE dt.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /** Documents carrying a given tag, without scanning the whole join table. */
    @Query("SELECT dt.documentId FROM DocumentTag dt WHERE dt.tagId = :tagId")
    List<UUID> findDocumentIdsByTagId(@Param("tagId") UUID tagId);

    /** Removes a tag from every document carrying it, in one statement. */
    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentTag dt WHERE dt.tagId = :tagId")
    void deleteByTagId(@Param("tagId") UUID tagId);

    /**
     * Documents per tag, counted by the database. The tag vocabulary screen
     * used to load every document-tag row and total them in a HashMap.
     */
    @Query("SELECT dt.tagId AS tagId, COUNT(dt.documentId) AS documentCount " +
           "FROM DocumentTag dt GROUP BY dt.tagId")
    List<TagUsage> findTagUsage();

    interface TagUsage {
        UUID getTagId();
        long getDocumentCount();
    }
}
