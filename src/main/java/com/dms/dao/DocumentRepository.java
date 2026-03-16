package com.dms.dao;

import com.dms.models.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("""
        SELECT DISTINCT d FROM Document d
        LEFT JOIN d.metadata m
        WHERE LOWER(d.title) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(m.value) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(m.key) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    List<Document> searchDocuments(@Param("query") String query);

}