package com.dms.dao;

import com.dms.dto.DocumentTitleDTO;
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
       OR LOWER(m.value) LIKE LOWER(CONCAT('%', :query, '%'))
       OR LOWER(m.key) LIKE LOWER(CONCAT('%', :query, '%'))
""")
List<DocumentTitleDTO> searchDocuments(@Param("query") String query);

}