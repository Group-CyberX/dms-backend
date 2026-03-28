package com.dms.dao;

import com.dms.models.Documents;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Documents, UUID> {

    @Query("select (count(d) > 0) from Documents d where d.is_deleted = false and d.title = :title and ((:folderId is null and d.folder_id is null) or d.folder_id = :folderId)")
    boolean existsByTitleInFolder(@Param("title") String title, @Param("folderId") UUID folderId);

    @Query("select d from Documents d where d.is_deleted = false")
    List<Documents> findAllActive();

    @Query("select d from Documents d where d.document_id = :id and d.is_deleted = false")
    Optional<Documents> findActiveById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.is_deleted = true where d.document_id = :id")
    int softDeleteById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.is_deleted = false where d.document_id = :id")
    int restoreById(@Param("id") UUID id);

    @Query(value = "SELECT * FROM \"Document\" WHERE is_deleted = true", nativeQuery = true)
    List<Documents> findAllDeleted();
}
