package com.dms.dao;

import com.dms.dto.DocumentTitleDTO;
import com.dms.dto.SearchResultDTO;
import com.dms.models.Documents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Documents, UUID> {

    @Query("""
    SELECT DISTINCT new com.dms.dto.DocumentTitleDTO(d.title)
    FROM Documents d
    LEFT JOIN d.metadata m
    WHERE LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%'))
       OR LOWER(CAST(m.value AS String)) LIKE LOWER(CONCAT('%', :query, '%'))
       OR LOWER(m.key) LIKE LOWER(CONCAT('%', :query, '%'))
""")
    List<DocumentTitleDTO> searchDocuments(@Param("query") String query);

    @Query("""
    SELECT DISTINCT d
    FROM Documents d
    LEFT JOIN d.metadata m
    WHERE (:query IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%')) 
           OR LOWER(CAST(m.value AS String)) LIKE LOWER(CONCAT('%', :query, '%')))
       AND (:documentType IS NULL OR EXISTS (SELECT 1 FROM d.metadata m2 WHERE LOWER(m2.key) = 'documenttype' AND LOWER(CAST(m2.value AS String)) LIKE LOWER(CONCAT('%', :documentType, '%'))))
       AND (:status IS NULL OR EXISTS (SELECT 1 FROM d.metadata m2 WHERE LOWER(m2.key) = 'status' AND LOWER(CAST(m2.value AS String)) LIKE LOWER(CONCAT('%', :status, '%'))))
       AND (:owner IS NULL OR EXISTS (SELECT 1 FROM d.metadata m2 WHERE LOWER(m2.key) = 'owner' AND LOWER(CAST(m2.value AS String)) LIKE LOWER(CONCAT('%', :owner, '%'))))
       AND (:tags IS NULL OR EXISTS (SELECT 1 FROM d.metadata m2 WHERE LOWER(m2.key) = 'tags' AND LOWER(CAST(m2.value AS String)) LIKE LOWER(CONCAT('%', :tags, '%'))))
       AND (:signatureStatus IS NULL OR EXISTS (SELECT 1 FROM d.metadata m2 WHERE LOWER(m2.key) = 'signaturestatus' AND LOWER(CAST(m2.value AS String)) LIKE LOWER(CONCAT('%', :signatureStatus, '%'))))
""")
    List<Documents> searchDocumentsWithFilters(
            @Param("query") String query,
            @Param("documentType") String documentType,
            @Param("status") String status,
            @Param("owner") String owner,
            @Param("tags") String tags,
            @Param("signatureStatus") String signatureStatus
    );

    @Query("select (count(d) > 0) from Documents d where d.title = :title and ((:folderId is null and d.folder_id is null) or d.folder_id = :folderId)")
    boolean existsByTitleInFolder(@Param("title") String title, @Param("folderId") UUID folderId);

}
