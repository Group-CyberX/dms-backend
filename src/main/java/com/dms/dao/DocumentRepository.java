package com.dms.dao;

import com.dms.models.Documents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Documents, UUID> {

    @Query("""
    SELECT DISTINCT d FROM Documents d
    LEFT JOIN d.metadata m
    WHERE 
        (:query IS NULL OR :query = '' OR
            LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(m.value) LIKE LOWER(CONCAT('%', :query, '%'))
        )
    AND (:documentType IS NULL OR :documentType = 'any' OR d.documentType = :documentType)
    AND (:status IS NULL OR :status = 'any' OR d.status = :status)
    AND (:owner IS NULL OR :owner = 'any' OR LOWER(d.owner) LIKE LOWER(CONCAT('%', :owner, '%')))
    AND (:signatureStatus IS NULL OR :signatureStatus = 'any' OR d.signatureStatus = :signatureStatus)
    AND (:sinceDate IS NULL OR d.createdDate >= :sinceDate)
    """)
    List<Documents> searchDocuments(
        @Param("query") String query,
        @Param("documentType") String documentType,
        @Param("status") String status,
        @Param("owner") String owner,
        @Param("signatureStatus") String signatureStatus,
        @Param("sinceDate") LocalDateTime sinceDate
    );

}