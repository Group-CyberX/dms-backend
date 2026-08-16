package com.dms.dao;

import com.dms.models.Documents;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Documents, UUID> {

    @Query("select (count(d) > 0) from Documents d where d.is_deleted = false and d.title = :title and ((:folderId is null and d.folder_id is null) or d.folder_id = :folderId)")
    boolean existsByTitleInFolder(@Param("title") String title, @Param("folderId") UUID folderId);

    /**
     * Reads a document with a row-level write lock (SELECT ... FOR UPDATE).
     * Used when acquiring the edit lock so two users clicking at the same moment
     * cannot both be told they hold it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Documents d where d.document_id = :id")
    Optional<Documents> findByIdForUpdate(@Param("id") UUID id);

    // Search functionality - Search across document title and metadata
    @Query(value = """
        SELECT DISTINCT d.* FROM \"Document\" d
        LEFT JOIN document_metadata m ON d.document_id = m.document_id
        LEFT JOIN \"DocumentVersion\" dv ON d.document_id = dv.document_id
        WHERE d.is_deleted = false
              AND (:searchTerm IS NULL 
                   OR LOWER(d.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(m.meta_key) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(m.meta_value) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(dv.ocr_content) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
    """, nativeQuery = true)
    List<Documents> universalSearch(@Param("searchTerm") String searchTerm);

    // Soft-delete functionality - For document tagging and preview
    @Query("select d from Documents d where d.is_deleted = false")
    List<Documents> findAllActive();

    @Query("select d from Documents d where d.document_id = :id and d.is_deleted = false")
    Optional<Documents> findActiveById(@Param("id") UUID id);

    @Query("select d from Documents d where d.owner_id = :ownerId and d.is_deleted = false")
    List<Documents> findByOwnerIdAndNotDeleted(@Param("ownerId") UUID ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.is_deleted = true, d.deleted_at = CURRENT_TIMESTAMP where d.document_id = :id")
    int softDeleteById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.is_deleted = false, d.deleted_at = null where d.document_id = :id")
    int restoreById(@Param("id") UUID id);

    @Query(value = "SELECT * FROM \"Document\" WHERE is_deleted = true", nativeQuery = true)
    List<Documents> findAllDeleted();

    @Query("select d from Documents d where d.is_deleted = true and d.owner_id = :ownerId")
    List<Documents> findAllDeletedByOwner(@Param("ownerId") UUID ownerId);

    // Search by tags - using native SQL
    @Query(value = """
        SELECT DISTINCT d.* FROM \"Document\" d
        INNER JOIN \"DocumentTag\" dt ON d.document_id = dt.document_id
        INNER JOIN \"Tag\" t ON dt.tag_id = t.tag_id
        WHERE LOWER(t.tag_name) LIKE LOWER(CONCAT('%', ?1, '%'))
              AND d.is_deleted = false
    """, nativeQuery = true)
    List<Documents> searchByTag(String tagName);

    // Combined search - search title, metadata, OCR content, AND tags
    @Query(value = """
        SELECT DISTINCT d.* FROM \"Document\" d
        LEFT JOIN document_metadata m ON d.document_id = m.document_id
        LEFT JOIN \"DocumentTag\" dt ON d.document_id = dt.document_id
        LEFT JOIN \"Tag\" t ON dt.tag_id = t.tag_id
        LEFT JOIN \"DocumentVersion\" dv ON d.document_id = dv.document_id
        WHERE d.is_deleted = false
              AND (?1 IS NULL 
                   OR LOWER(d.title) LIKE LOWER(CONCAT('%', ?1, '%'))
                   OR LOWER(m.meta_key) LIKE LOWER(CONCAT('%', ?1, '%'))
                   OR LOWER(m.meta_value) LIKE LOWER(CONCAT('%', ?1, '%'))
                   OR LOWER(t.tag_name) LIKE LOWER(CONCAT('%', ?1, '%'))
                   OR LOWER(dv.ocr_content) LIKE LOWER(CONCAT('%', ?1, '%')))
    """, nativeQuery = true)
    List<Documents> universalSearchIncludingTags(String searchTerm);

    // Owner-scoped operations (do not change search behavior)
    @Query("select d from Documents d where d.is_deleted = false and d.owner_id = :ownerId")
    List<Documents> findAllActiveByOwner(@Param("ownerId") UUID ownerId);

    @Query("select d from Documents d where d.document_id = :id and d.is_deleted = false and d.owner_id = :ownerId")
    Optional<Documents> findActiveByIdAndOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.is_deleted = true, d.deleted_at = CURRENT_TIMESTAMP where d.document_id = :id and d.owner_id = :ownerId")
    int softDeleteByIdAndOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.is_deleted = false, d.deleted_at = null where d.document_id = :id and d.owner_id = :ownerId")
    int restoreByIdAndOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    // New methods for folder tree slice
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.folder_id = :targetFolderId where d.document_id in :documentIds and d.is_deleted = false")
    int moveToFolder(@Param("documentIds") List<UUID> documentIds, @Param("targetFolderId") UUID targetFolderId);

    @Query("select count(d) from Documents d where d.folder_id = :folderId and d.is_deleted = false")
    long countActiveByFolder(@Param("folderId") UUID folderId);

    @Query("select coalesce(sum(d.file_size), 0) from Documents d where d.folder_id = :folderId and d.is_deleted = false")
    long sumFileSizeByFolder(@Param("folderId") UUID folderId);

    @Query("select d.folder_id, count(d) from Documents d where d.is_deleted = false group by d.folder_id")
    List<Object[]> countActiveByFolderGrouped();

    @Query("select d.folder_id, coalesce(sum(d.file_size),0) from Documents d where d.is_deleted = false group by d.folder_id")
    List<Object[]> sumFileSizeByFolderGrouped();

    // Cascading folder delete: soft-delete every active document across a set of folder ids
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.is_deleted = true, d.deleted_at = CURRENT_TIMESTAMP where d.folder_id in :folderIds and d.is_deleted = false")
    int softDeleteByFolderIds(@Param("folderIds") List<UUID> folderIds);

    // Cascading folder restore: bring back every document soft-deleted alongside that folder subtree
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.is_deleted = false, d.deleted_at = null where d.folder_id in :folderIds and d.is_deleted = true")
    int restoreByFolderIds(@Param("folderIds") List<UUID> folderIds);

    @Query("select count(d) from Documents d where d.folder_id in :folderIds and d.is_deleted = true")
    long countDeletedByFolderIds(@Param("folderIds") List<UUID> folderIds);

    // ---- Dashboard aggregates -------------------------------------------

    @Query("select count(d) from Documents d where d.is_deleted = false")
    long countActive();

    @Query("select coalesce(sum(d.file_size), 0) from Documents d where d.is_deleted = false")
    long sumFileSizeActive();

    @Query("select count(d) from Documents d where d.is_deleted = true")
    long countDeleted();

    @Query("select count(d) from Documents d where d.is_deleted = false and d.owner_id = :ownerId")
    long countActiveByOwner(@Param("ownerId") UUID ownerId);

    /** Titles for a set of ids, so the SLA panel needs one query, not one per row. */
    @Query("select d from Documents d where d.document_id in :ids")
    List<Documents> findAllByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * One page of the document list, with the title search and folder filter
     * applied by the database. Passing ownerId restricts it to that person's
     * documents; passing null returns everyone's, for the roles allowed to see
     * them.
     */
    @Query("""
            select d from Documents d
            where d.is_deleted = false
              and (:ownerId is null or d.owner_id = :ownerId)
              and (:folderId is null or d.folder_id = :folderId)
              and (:search is null or :search = '' or lower(d.title) like lower(concat('%', :search, '%')))
            """)
    Page<Documents> findPage(@Param("ownerId") UUID ownerId,
                             @Param("folderId") UUID folderId,
                             @Param("search") String search,
                             Pageable pageable);
}
