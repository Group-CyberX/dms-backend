package com.dms.rest;

import com.dms.dao.FolderRepository;
import com.dms.dto.FolderCreateRequest;
import com.dms.dto.FolderDeleteResponse;
import com.dms.dto.FolderRestoreResponse;
import com.dms.dto.FolderTrashItemDTO;
import com.dms.dto.FolderTreeNodeDTO;
import com.dms.models.Folders;
import com.dms.service.AuditLogService;
import com.dms.service.FolderTreeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderRepository folderRepository;
    private final FolderTreeService folderTreeService;
    private final AuditLogService auditLogService;
    private final com.dms.service.PermissionService permissionService;

    public FolderController(FolderRepository folderRepository,
                            FolderTreeService folderTreeService,
                            AuditLogService auditLogService,
                            com.dms.service.PermissionService permissionService) {
        this.folderRepository = folderRepository;
        this.folderTreeService = folderTreeService;
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<Folders> getAll() {
        return folderRepository.findAllActive();
    }

    /**
     * The folder tree with per-folder document counts.
     *
     * The {@code all} flag means the same thing here as on GET /api/documents,
     * and defaults the same way (false - only your own documents). That is
     * deliberate: the counts render as badges beside that list, so the two
     * endpoints have to be asked the same question or the page contradicts
     * itself. It used to, reporting 101 documents beside a list of 3.
     */
    @GetMapping("/tree")
    public FolderTreeNodeDTO getTree(
            @RequestParam(value = "all", required = false, defaultValue = "false") boolean all) {
        UUID ownerId = all ? null : com.dms.security.SecurityUtils.currentUserId();
        return folderTreeService.getFullTree(ownerId);
    }

    /**
     * Recycle bin listing: one row per deleted folder subtree.
     *
     * Folders have no owner, so there is no per-user view of them: they are
     * shown to the roles that may see everyone's deleted documents and to
     * nobody else. An end user was previously shown every deleted folder in the
     * organisation beside a document list scoped to their own.
     */
    @GetMapping("/trash")
    public List<FolderTrashItemDTO> getTrash(org.springframework.security.core.Authentication auth) {
        if (!permissionService.hasPermission(auth, "canViewAllDeletedDocuments")) {
            return List.of();
        }
        return folderTreeService.getFolderTrash();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Folders> getById(@PathVariable("id") UUID id) {
        return folderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/subtree")
    public ResponseEntity<List<Folders>> getSubtree(@PathVariable("id") UUID id) {
        if (!folderRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(folderTreeService.getDescendants(id));
    }

    @PostMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canCreateDocument')")
    public ResponseEntity<Folders> create(@RequestBody FolderCreateRequest request, HttpServletRequest httpReq) {
        Folders saved = folderTreeService.createFolder(request, httpReq.getRemoteAddr());
        auditLogService.createAuditLog("FOLDER_CREATED", saved.getFolder_id(), httpReq.getRemoteAddr(), "SUCCESS");
        return ResponseEntity.created(URI.create("/api/folders/" + saved.getFolder_id())).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditDocument')")
    public ResponseEntity<Folders> update(@PathVariable("id") UUID id, @RequestBody Folders update) {
        Optional<Folders> existingOpt = folderRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Folders existing = existingOpt.get();
        existing.setName(update.getName());
        existing.setParent_folder_id(update.getParent_folder_id());
        existing.setPath(update.getPath());
        Folders saved = folderRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    /**
     * Moves a folder along with all of its subfolders to the recycle bin,
     * moving every document inside any of them to the recycle bin too
     * (soft delete). Nothing is permanently removed.
     *
     * Gated on its own permission rather than on canDeleteDocument. Folders
     * have no owner, so this takes every document inside with it whoever owns
     * them - being allowed to delete your own document should not authorise
     * clearing a shared folder for the whole organisation.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canDeleteFolder')")
    public ResponseEntity<?> delete(@PathVariable("id") UUID id, HttpServletRequest httpReq) {
        if (!folderRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        FolderDeleteResponse result = folderTreeService.deleteFolderCascade(id, httpReq.getRemoteAddr());
        auditLogService.createAuditLog("FOLDER_DELETED", id, httpReq.getRemoteAddr(), "SUCCESS");
        return ResponseEntity.ok(result);
    }

    /**
     * Restores a folder and its entire subtree out of the recycle bin,
     * along with every document that was soft-deleted inside any of them.
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canRestoreRecycleBin')")
    public ResponseEntity<?> restore(@PathVariable("id") UUID id, HttpServletRequest httpReq) {
        if (!folderRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        FolderRestoreResponse result = folderTreeService.restoreFolderCascade(id, httpReq.getRemoteAddr());
        auditLogService.createAuditLog("FOLDER_RESTORED", id, httpReq.getRemoteAddr(), "SUCCESS");
        return ResponseEntity.ok(result);
    }
}
