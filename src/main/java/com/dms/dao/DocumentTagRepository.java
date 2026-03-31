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

@Repository
public interface DocumentTagRepository extends JpaRepository<DocumentTag, UUID> {
    
    @Query("SELECT dt FROM DocumentTag dt WHERE dt.documentId = :documentId")
    List<DocumentTag> findByDocumentId(@Param("documentId") UUID documentId);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentTag dt WHERE dt.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);
}
