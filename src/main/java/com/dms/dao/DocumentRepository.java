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
    // Same reasoning as the combined search above: EXISTS instead of a join
    // plus DISTINCT, so a document carrying several matching tags is examined
    // once rather than produced repeatedly and then deduplicated.
    @Query(value = """
        SELECT d.* FROM \"Document\" d
        WHERE d.is_deleted = false
              AND (CAST(?2 AS uuid) IS NULL OR d.owner_id = CAST(?2 AS uuid))
              AND EXISTS (SELECT 1 FROM \"DocumentTag\" dt
                           JOIN \"Tag\" t ON t.tag_id = dt.tag_id
                           WHERE dt.document_id = d.document_id
                             AND t.tag_name ILIKE CONCAT('%', ?1, '%'))
    """, nativeQuery = true)
    List<Documents> searchByTag(String tagName, String ownerId);

    // Combined search - title, metadata, tags and OCR text.
    //
    // Written as EXISTS rather than four LEFT JOINs and a DISTINCT. The joined
    // form produced one row per metadata x tag x version combination for every
    // document before collapsing them again, and the scan of the OCR text then
    // ran over that multiplied set - a search for a common letter took nearly
    // half a minute. EXISTS stops at the first match per document and never
    // multiplies rows, and the cheap comparisons are placed first so the OCR
    // text is only read for documents nothing else matched.
    @Query(value = """
        SELECT d.* FROM \"Document\" d
        WHERE d.is_deleted = false
              AND (CAST(?2 AS uuid) IS NULL OR d.owner_id = CAST(?2 AS uuid))
              AND (?1 IS NULL
                   OR d.title ILIKE CONCAT('%', ?1, '%')
                   OR EXISTS (SELECT 1 FROM document_metadata m
                               WHERE m.document_id = d.document_id
                                 AND (m.meta_key ILIKE CONCAT('%', ?1, '%')
                                      OR m.meta_value ILIKE CONCAT('%', ?1, '%')))
                   OR EXISTS (SELECT 1 FROM \"DocumentTag\" dt
                               JOIN \"Tag\" t ON t.tag_id = dt.tag_id
                               WHERE dt.document_id = d.document_id
                                 AND t.tag_name ILIKE CONCAT('%', ?1, '%'))
                   OR EXISTS (SELECT 1 FROM \"DocumentVersion\" dv
                               WHERE dv.document_id = d.document_id
                                 AND dv.ocr_content ILIKE CONCAT('%', ?1, '%')))
    """, nativeQuery = true)
    List<Documents> universalSearchIncludingTags(String searchTerm, String ownerId);

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
              and (:status is null or d.status = :status)
              and (:search is null or :search = '' or lower(d.title) like lower(concat('%', :search, '%')))
            """)
    Page<Documents> findPage(@Param("ownerId") UUID ownerId,
                             @Param("folderId") UUID folderId,
                             @Param("status") String status,
                             @Param("search") String search,
                             Pageable pageable);

    /**
     * The same page, with the owner's name resolved by the database.
     *
     * The list previously took the page and then went back for the owner names
     * in a second query. Against a database that is not on this machine every
     * extra round trip is most of the time the request costs, so the join does
     * it in one.
     */
    @Query("""
            select d as document, u.username as ownerName
            from Documents d
            left join User u on u.userId = d.owner_id
            where d.is_deleted = false
              and (:ownerId is null or d.owner_id = :ownerId)
              and (:folderId is null or d.folder_id = :folderId)
              and (:status is null or d.status = :status)
              and (:search is null or :search = '' or lower(d.title) like lower(concat('%', :search, '%')))
            """)
    Page<DocumentWithOwner> findPageWithOwner(@Param("ownerId") UUID ownerId,
                                             @Param("folderId") UUID folderId,
                                             @Param("status") String status,
                                             @Param("search") String search,
                                             Pageable pageable);

    interface DocumentWithOwner {
        Documents getDocument();
        String getOwnerName();
    }

    /**
     * Records where a document has reached. One row, by primary key.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Documents d set d.status = :status where d.document_id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") String status);

    /**
     * How many documents are waiting for someone to start a workflow on them.
     * Counted by the database so the badge does not depend on the page on
     * screen.
     */
    @Query("""
            select count(d) from Documents d
            where d.is_deleted = false
              and d.status = :status
              and (:ownerId is null or d.owner_id = :ownerId)
            """)
    long countByStatus(@Param("ownerId") UUID ownerId, @Param("status") String status);

    /**
     * Backfill for rows that predate the status column: everything with no
     * status yet, so the one-off pass can decide what each should carry.
     */
    @Query(value = "select document_id from \"Document\" where status is null", nativeQuery = true)
    List<UUID> findIdsWithoutStatus();

    /**
     * The document ids any workflow has ever been started on. Read once by the
     * backfill, never on a request.
     */
    @Query(value = "select distinct document_id from workflow_instance where document_id is not null",
           nativeQuery = true)
    List<String> findDocumentIdsWithWorkflow();

    @Modifying
    @Transactional
    @Query(value = "update \"Document\" set status = :status where status is null", nativeQuery = true)
    int backfillMissingStatus(@Param("status") String status);

    /**
     * One page of the recycle bin. Scoped the way the bin listing is: an
     * ownerId restricts it to that person, null returns everyone.
     */
    @Query("""
            select d from Documents d
            where d.is_deleted = true
              and (:ownerId is null or d.owner_id = :ownerId)
              and (:search is null or :search = '' or lower(d.title) like lower(concat('%', :search, '%')))
            """)
    Page<Documents> findDeletedPage(@Param("ownerId") UUID ownerId,
                                    @Param("search") String search,
                                    Pageable pageable);

    /**
     * The three recycle-bin figures in one query, so the cards do not depend on
     * how many rows the current page happens to hold.
     *
     * expiringSoon counts everything with a week or less left, including what
     * is already due - the count the cards previously produced in the browser
     * excluded exactly the most urgent rows.
     */
    @Query("""
            select count(d) as count,
                   coalesce(sum(d.file_size), 0) as totalBytes,
                   coalesce(sum(case when d.deleted_at < :expiringBefore then 1 else 0 end), 0) as expiringSoon
            from Documents d
            where d.is_deleted = true
              and (:ownerId is null or d.owner_id = :ownerId)
            """)
    TrashSummary summariseDeleted(@Param("ownerId") UUID ownerId,
                                  @Param("expiringBefore") java.time.LocalDateTime expiringBefore);

    interface TrashSummary {
        Long getCount();
        Long getTotalBytes();
        Long getExpiringSoon();
    }
}
