package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dao.FolderRepository;
import com.dms.dto.FolderCreateRequest;
import com.dms.dto.FolderDeleteResponse;
import com.dms.dto.FolderRestoreResponse;
import com.dms.dto.FolderTrashItemDTO;
import com.dms.dto.FolderTreeNodeDTO;
import com.dms.dto.MoveDocumentsRequest;
import com.dms.models.Folders;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FolderTreeService {
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogService auditLogService;

    public FolderTreeService(FolderRepository folderRepository,
                             DocumentRepository documentRepository,
                             AuditLogService auditLogService) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * The folder tree, with the document counts and sizes each folder holds.
     *
     * @param ownerId count only this user's documents, or null to count
     *                everyone's. It is not optional by accident: the badges in
     *                the sidebar sit directly beside the document list, and the
     *                two have to be answering the same question. They were not -
     *                the list is owner-scoped by default while these counts were
     *                always global, so a folder showed 26 next to a list of 3.
     *
     *                Whoever calls this has to say which scope they are showing.
     *                An earlier attempt at per-user counts was reverted because
     *                it undercounted against an unscoped list; making the scope
     *                an explicit argument is what stops the two drifting apart
     *                again in either direction.
     */
    public FolderTreeNodeDTO getFullTree(UUID ownerId) {
        List<Folders> all = folderRepository.findAllActive();

        Map<UUID, FolderTreeNodeDTO> dtoMap = all.stream().map(f -> {
            FolderTreeNodeDTO dto = new FolderTreeNodeDTO();
            dto.setFolder_id(f.getFolder_id());
            dto.setName(f.getName());
            dto.setParent_folder_id(f.getParent_folder_id());
            dto.setPath(f.getPath());
            return dto;
        }).collect(Collectors.toMap(FolderTreeNodeDTO::getFolder_id, Function.identity()));

        // Build parent-child relationships
        List<FolderTreeNodeDTO> roots = new ArrayList<>();
        for (Folders f : all) {
            FolderTreeNodeDTO node = dtoMap.get(f.getFolder_id());
            UUID parentId = f.getParent_folder_id();
            if (parentId == null) {
                roots.add(node);
            } else {
                FolderTreeNodeDTO parent = dtoMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // Orphaned: treat as root
                    roots.add(node);
                }
            }
        }

        // Aggregate document counts and total sizes (batch), in the caller's scope.
        List<Object[]> countRows = ownerId == null
                ? documentRepository.countActiveByFolderGrouped()
                : documentRepository.countActiveByFolderGroupedForOwner(ownerId);

        List<Object[]> sizeRows = ownerId == null
                ? documentRepository.sumFileSizeByFolderGrouped()
                : documentRepository.sumFileSizeByFolderGroupedForOwner(ownerId);

        // Totals for the whole scope are accumulated alongside the per-folder
        // figures. A document filed in no folder, or in one that has since been
        // deleted, belongs to no node in the tree, so rolling the nodes up
        // misses it - the root reported 81 beside a list of 92.
        long scopeDocuments = 0L;
        long scopeBytes = 0L;

        Map<UUID, Long> countMap = new HashMap<>();
        for (Object[] row : countRows) {
            UUID folderId = (UUID) row[0];
            Long count = (Long) row[1];
            scopeDocuments += count == null ? 0L : count;
            if (folderId != null) countMap.put(folderId, count);
        }
        Map<UUID, Long> sizeMap = new HashMap<>();
        for (Object[] row : sizeRows) {
            UUID folderId = (UUID) row[0];
            Long size = (Long) row[1];
            scopeBytes += size == null ? 0L : size;
            if (folderId != null) sizeMap.put(folderId, size);
        }

        // Seed every node with the documents filed directly in it.
        dtoMap.values().forEach(node -> {
            node.setDocumentCount(countMap.getOrDefault(node.getFolder_id(), 0L));
            node.setTotalSize(sizeMap.getOrDefault(node.getFolder_id(), 0L));
        });

        // Wrap all roots under a synthetic root to satisfy a single DTO return
        FolderTreeNodeDTO syntheticRoot = new FolderTreeNodeDTO();
        syntheticRoot.setFolder_id(null);
        syntheticRoot.setName("ROOT");
        syntheticRoot.setPath("");
        syntheticRoot.setParent_folder_id(null);
        syntheticRoot.setChildren(roots);

        // Roll the direct counts up the tree, so a folder reports everything
        // filed anywhere beneath it. Counting only direct children made a
        // parent whose documents all live in subfolders report 0, which read as
        // an empty folder even though the files were right there one level down.
        rollUpTotals(syntheticRoot, new HashSet<>());

        // The root reports the scope's real total, which the roll-up cannot
        // reach. Every folder below it keeps its own rolled-up figure.
        syntheticRoot.setDocumentCount(scopeDocuments);
        syntheticRoot.setTotalSize(scopeBytes);
        return syntheticRoot;
    }

    /**
     * Adds each subtree's documents and bytes into its parent, depth first, and
     * returns what the node ended up carrying.
     *
     * The visited set guards against a parent cycle produced by bad data: a
     * cycle would otherwise recurse until the stack gave out, taking the whole
     * folder tree endpoint down with it.
     */
    private long[] rollUpTotals(FolderTreeNodeDTO node, Set<UUID> visited) {
        if (node.getFolder_id() != null && !visited.add(node.getFolder_id())) {
            return new long[]{0L, 0L};
        }

        long documents = node.getDocumentCount();
        long bytes = node.getTotalSize();

        for (FolderTreeNodeDTO child : node.getChildren()) {
            long[] fromChild = rollUpTotals(child, visited);
            documents += fromChild[0];
            bytes += fromChild[1];
        }

        node.setDocumentCount(documents);
        node.setTotalSize(bytes);
        return new long[]{documents, bytes};
    }

    public List<Folders> getDescendants(UUID folderId) {
        Optional<Folders> folderOpt = folderRepository.findById(folderId);
        if (folderOpt.isEmpty()) return Collections.emptyList();
        String base = folderOpt.get().getPath();
        String prefix = base == null || base.isBlank() ? "" : base;
        return folderRepository.findByPathStartingWith(prefix);
    }

    public Folders createFolder(FolderCreateRequest req, String actorIp) {
        // Validate name
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty() || name.length() > 100 || name.contains("/") || name.equals("..")) {
            auditLogService.createAuditLog("FOLDER_CREATED", null, actorIp, "FAILED");
            throw new IllegalArgumentException("Invalid folder name");
        }
        String safeName = name.replace('/', '_');

        UUID parentId = req.getParentFolderId();
        String path;
        UUID parentFolderId = null;
        if (parentId != null) {
            Folders parent = folderRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent folder not found"));
            if (parent.isIs_deleted()) {
                throw new IllegalArgumentException("Cannot create a folder inside a deleted folder");
            }
            parentFolderId = parent.getFolder_id();
            String parentPath = parent.getPath();
            path = (parentPath == null || parentPath.isBlank()) ? safeName : parentPath + "/" + safeName;
        } else {
            path = safeName;
        }

        Folders f = new Folders();
        f.setFolder_id(UUID.randomUUID());
        f.setName(safeName);
        f.setParent_folder_id(parentFolderId);
        f.setPath(path);

        Folders saved = folderRepository.save(f);
        auditLogService.createAuditLog("FOLDER_CREATED", saved.getFolder_id(), actorIp, "SUCCESS");
        return saved;
    }

    /**
     * Moves a folder and its entire subtree to the recycle bin (soft delete),
     * along with every active document in any of those folders. Nothing is
     * actually removed, so the whole thing can be undone via restoreFolderCascade.
     * Traverses via parent_folder_id rather than the path prefix (which can
     * false-match siblings like "Finance" vs "Finance-2024") since a wrong
     * match here would delete someone else's folder.
     */
    public FolderDeleteResponse deleteFolderCascade(UUID folderId, String actorIp) {
        Folders root = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        if (root.isIs_deleted()) {
            throw new IllegalArgumentException("Folder is already in the recycle bin");
        }

        List<Folders> subtree = collectSubtree(folderId);
        List<UUID> folderIds = subtree.stream().map(Folders::getFolder_id).collect(Collectors.toList());

        int documentsMoved = documentRepository.softDeleteByFolderIds(folderIds);
        folderRepository.softDeleteByIds(folderIds);

        auditLogService.createAuditLog("FOLDER_DELETED", folderId, actorIp, "SUCCESS");
        return new FolderDeleteResponse(folderIds, documentsMoved);
    }

    /**
     * Lists the recycle bin's folder rows: one per deleted subtree root, with
     * rolled-up counts of the subfolders and documents that came with it.
     */
    public List<FolderTrashItemDTO> getFolderTrash() {
        List<Folders> roots = folderRepository.findTrashRoots();
        List<FolderTrashItemDTO> result = new ArrayList<>();
        for (Folders root : roots) {
            List<Folders> subtree = collectSubtree(root.getFolder_id());
            List<UUID> ids = subtree.stream().map(Folders::getFolder_id).collect(Collectors.toList());
            long documentCount = documentRepository.countDeletedByFolderIds(ids);
            result.add(new FolderTrashItemDTO(
                    root.getFolder_id(),
                    root.getName(),
                    root.getPath(),
                    root.getDeleted_at(),
                    (int) documentCount,
                    subtree.size() - 1
            ));
        }
        return result;
    }

    /**
     * Restores a folder and its entire subtree out of the recycle bin, along
     * with every document that was soft-deleted inside any of them — the
     * mirror image of deleteFolderCascade.
     */
    public FolderRestoreResponse restoreFolderCascade(UUID folderId, String actorIp) {
        Folders root = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        if (!root.isIs_deleted()) {
            throw new IllegalArgumentException("Folder is not in the recycle bin");
        }

        List<Folders> subtree = collectSubtree(folderId);
        List<UUID> folderIds = subtree.stream().map(Folders::getFolder_id).collect(Collectors.toList());

        folderRepository.restoreByIds(folderIds);
        int documentsRestored = documentRepository.restoreByFolderIds(folderIds);

        auditLogService.createAuditLog("FOLDER_RESTORED", folderId, actorIp, "SUCCESS");
        return new FolderRestoreResponse(folderIds, documentsRestored);
    }

    private List<Folders> collectSubtree(UUID rootId) {
        List<Folders> result = new ArrayList<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            folderRepository.findById(current).ifPresent(result::add);
            for (Folders child : folderRepository.findByParent_folder_id(current)) {
                queue.add(child.getFolder_id());
            }
        }
        return result;
    }

    public int moveDocuments(MoveDocumentsRequest req, String actorIp) {
        if (req.getDocumentIds() == null || req.getDocumentIds().isEmpty() || req.getTargetFolderId() == null) {
            auditLogService.createAuditLog("DOCUMENTS_MOVED", null, actorIp, "FAILED");
            throw new IllegalArgumentException("documentIds and targetFolderId are required");
        }
        // Ensure target folder exists
        folderRepository.findById(req.getTargetFolderId())
                .orElseThrow(() -> new IllegalArgumentException("Target folder not found"));

        int moved = documentRepository.moveToFolder(req.getDocumentIds(), req.getTargetFolderId());
        auditLogService.createAuditLog("DOCUMENTS_MOVED", req.getTargetFolderId(), actorIp, "SUCCESS");
        return moved;
    }
}
