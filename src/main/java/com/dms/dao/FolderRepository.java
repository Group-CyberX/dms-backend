package com.dms.dao;

import com.dms.models.Folders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folders, UUID> {
    @Query("select f from Folders f where f.is_deleted = false and lower(f.name) = lower(:name)")
    Optional<Folders> findByNameIgnoreCase(@Param("name") String name);

    @Query("select f from Folders f where f.parent_folder_id is null")
    List<Folders> findByParent_folder_idIsNull();

    // Unfiltered on purpose: used to walk a subtree for both cascade-delete
    // (from an active root) and cascade-restore (from a deleted root), so it
    // must see descendants regardless of their own is_deleted state.
    @Query("select f from Folders f where f.parent_folder_id = :parentId")
    List<Folders> findByParent_folder_id(@Param("parentId") UUID parentId);

    List<Folders> findByPathStartingWith(String prefix);

    @Query("select f from Folders f where f.is_deleted = false")
    List<Folders> findAllActive();

    // A folder is a "trash root" (shown as one row in the recycle bin) if it's
    // deleted and either has no parent, or its parent survived active — i.e.
    // it's the top of whatever subtree got cascade-deleted together.
    @Query("""
        select f from Folders f
        where f.is_deleted = true
          and (f.parent_folder_id is null
               or f.parent_folder_id not in (
                   select f2.folder_id from Folders f2 where f2.is_deleted = true
               ))
    """)
    List<Folders> findTrashRoots();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Folders f set f.is_deleted = true, f.deleted_at = CURRENT_TIMESTAMP where f.folder_id in :folderIds")
    int softDeleteByIds(@Param("folderIds") List<UUID> folderIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Folders f set f.is_deleted = false, f.deleted_at = null where f.folder_id in :folderIds")
    int restoreByIds(@Param("folderIds") List<UUID> folderIds);
}
