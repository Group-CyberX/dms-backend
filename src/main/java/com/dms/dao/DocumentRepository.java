package com.dms.dao;

import com.dms.models.Documents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Documents, UUID> {

    @Query("select (count(d) > 0) from Documents d where d.title = :title and ((:folderId is null and d.folder_id is null) or d.folder_id = :folderId)")
    boolean existsByTitleInFolder(@Param("title") String title, @Param("folderId") UUID folderId);

    @Query("""
        SELECT DISTINCT d FROM Documents d
        LEFT JOIN d.metadata m
        WHERE (:searchTerm IS NULL 
               OR LOWER(d.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
               OR LOWER(m.key) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
               OR LOWER(m.value) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
    """)
    List<Documents> universalSearch(@Param("searchTerm") String searchTerm);
}

