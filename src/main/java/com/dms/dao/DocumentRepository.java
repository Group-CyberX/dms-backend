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

    // Search by tags - using native SQL.
    //
    // ownerId is what keeps a search inside the caller's own library: null
    // searches everything, for the roles holding canSearchAllDocuments. The
    // cast is needed because a plain null parameter leaves Postgres unable to
    // infer the type of the comparison.
    @Query(value = """
        SELECT DISTINCT d.* FROM \"Document\" d
        INNER JOIN \"DocumentTag\" dt ON d.document_id = dt.document_id
        INNER JOIN \"Tag\" t ON dt.tag_id = t.tag_id
        WHERE LOWER(t.tag_name) LIKE LOWER(CONCAT('%', :tagName, '%'))
              AND d.is_deleted = false
              AND (CAST(:ownerId AS uuid) IS NULL OR d.owner_id = CAST(:ownerId AS uuid))
    """, nativeQuery = true)
    List<Documents> searchByTag(@Param("tagName") String tagName, @Param("ownerId") String ownerId);

    // Combined search - search title, metadata, OCR content, AND tags.
    // ownerId scopes the search exactly as it does in searchByTag.
    @Query(value = """
        SELECT DISTINCT d.* FROM \"Document\" d
        LEFT JOIN document_metadata m ON d.document_id = m.document_id
        LEFT JOIN \"DocumentTag\" dt ON d.document_id = dt.document_id
        LEFT JOIN \"Tag\" t ON dt.tag_id = t.tag_id
        LEFT JOIN \"DocumentVersion\" dv ON d.document_id = dv.document_id
        WHERE d.is_deleted = false
              AND (CAST(:ownerId AS uuid) IS NULL OR d.owner_id = CAST(:ownerId AS uuid))
              AND (:searchTerm IS NULL 
                   OR LOWER(d.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(m.meta_key) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(m.meta_value) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(t.tag_name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(dv.ocr_content) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
    """, nativeQuery = true)
    List<Documents> universalSearchIncludingTags(@Param("searchTerm") String searchTerm,
                                                 @Param("ownerId") String ownerId);

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

    // Driven by DocumentLifecycleService as a workflow starts, completes or is
    // rejected, so the list can filter on the lifecycle without joining workflows.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.status = :status where d.document_id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status);

    // New methods for folder tree slice
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.folder_id = :targetFolderId where d.document_id in :documentIds and d.is_deleted = false")
    int moveToFolder(@Param("documentIds") List<UUID> documentIds, @Param("targetFolderId") UUID targetFolderId);

    /** Move, but only the documents the caller owns - ids for anyone else's are ignored. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.folder_id = :targetFolderId where d.document_id in :documentIds and d.owner_id = :ownerId and d.is_deleted = false")
    int moveToFolderForOwner(@Param("documentIds") List<UUID> documentIds,
                             @Param("targetFolderId") UUID targetFolderId,
                             @Param("ownerId") UUID ownerId);

    @Query("select count(d) from Documents d where d.folder_id = :folderId and d.is_deleted = false")
    long countActiveByFolder(@Param("folderId") UUID folderId);

    @Query("select coalesce(sum(d.file_size), 0) from Documents d where d.folder_id = :folderId and d.is_deleted = false")
    long sumFileSizeByFolder(@Param("folderId") UUID folderId);

    @Query("select d.folder_id, count(d) from Documents d where d.is_deleted = false group by d.folder_id")
    List<Object[]> countActiveByFolderGrouped();

    @Query("select d.folder_id, coalesce(sum(d.file_size),0) from Documents d where d.is_deleted = false group by d.folder_id")
    List<Object[]> sumFileSizeByFolderGrouped();

    // Owner-scoped versions of the two above.
    //
    // The folder tree needs these because its badges have to agree with the
    // document list beside them, and that list is owner-scoped by default
    // (findAllActiveByOwner). Counting every document in the system made a
    // folder claim 26 files next to a list showing 3.
    @Query("select d.folder_id, count(d) from Documents d where d.is_deleted = false and d.owner_id = :ownerId group by d.folder_id")
    List<Object[]> countActiveByFolderGroupedForOwner(@Param("ownerId") UUID ownerId);

    @Query("select d.folder_id, coalesce(sum(d.file_size),0) from Documents d where d.is_deleted = false and d.owner_id = :ownerId group by d.folder_id")
    List<Object[]> sumFileSizeByFolderGroupedForOwner(@Param("ownerId") UUID ownerId);

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
     * One page of the document list, with the title search, folder and status
     * filters applied by the database. Passing ownerId restricts it to that
     * person's documents; passing null returns everyone's, for the roles allowed
     * to see them.
     *
     * A document with no status yet counts as NEW, so the filter still agrees
     * with the badge on rows written before the column existed.
     */
    @Query("""
            select d from Documents d
            where d.is_deleted = false
              and (:ownerId is null or d.owner_id = :ownerId)
              and (:folderId is null or d.folder_id = :folderId)
              and (:status is null or d.status = :status
                   or (:status = 'NEW' and d.status is null))
              and (:search is null or :search = '' or lower(d.title) like lower(concat('%', :search, '%')))
            """)
    Page<Documents> findPage(@Param("ownerId") UUID ownerId,
                             @Param("folderId") UUID folderId,
                             @Param("status") String status,
                             @Param("search") String search,
                             Pageable pageable);

    /**
     * Every filter chip count in one query. Asking for them one status at a time
     * would be five round trips for a header that is always on screen.
     */
    @Query("""
            select d.status, count(d) from Documents d
            where d.is_deleted = false
              and (:ownerId is null or d.owner_id = :ownerId)
            group by d.status
            """)
    List<Object[]> countByStatusGrouped(@Param("ownerId") UUID ownerId);
}
