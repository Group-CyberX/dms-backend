package com.dms.dao;

import com.dms.models.DocumentVersions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersions, UUID> {

    @Query("select v from DocumentVersions v where v.document_id = :documentId order by v.created_at desc")
    List<DocumentVersions> findByDocument_idOrderByCreated_atDesc(@Param("documentId") UUID documentId);
}
