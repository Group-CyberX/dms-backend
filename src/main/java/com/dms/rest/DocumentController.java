package com.dms.rest;

import com.dms.constants.WorkflowConstants;
import com.dms.dao.DocumentRepository;
import com.dms.dao.UserRepository;
import com.dms.dto.DocumentLockStatusResponse;
import com.dms.dto.DocumentResponse;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.UploadDocumentRequest;
import com.dms.models.Documents;
import com.dms.models.User;
import com.dms.service.AuditLogService;
import com.dms.service.DocumentLockService;
import com.dms.service.DocumentUploadService;
import com.dms.service.NotificationService;
import com.dms.service.PermissionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentUploadService documentUploadService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final DocumentLockService documentLockService;
    private final PermissionService permissionService;

    public DocumentController(DocumentRepository documentRepository,
                              DocumentUploadService documentUploadService,
                              AuditLogService auditLogService,
                              NotificationService notificationService,
                              UserRepository userRepository,
                              DocumentLockService documentLockService,
                              PermissionService permissionService) {
        this.documentRepository = documentRepository;
        this.documentUploadService = documentUploadService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.documentLockService = documentLockService;
        this.permissionService = permissionService;
    }

    // ------------------------------------------------------------------
    // Edit locking
    //
    // A document is held by one user at a time while they change its file,
    // metadata or tags. Everyone else keeps read access.
    // ------------------------------------------------------------------

    @PostMapping("/{id}/lock")
    public DocumentLockStatusResponse lockDocument(@PathVariable("id") UUID id, HttpServletRequest httpReq) {
        UUID userId = com.dms.security.SecurityUtils.currentUserId();
        String username = userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("Unknown user");

        return documentLockService.acquire(id, userId, username, httpReq.getRemoteAddr());
    }

    @PostMapping("/{id}/unlock")
    public DocumentLockStatusResponse unlockDocument(@PathVariable("id") UUID id,
                                                    Authentication authentication,
                                                    HttpServletRequest httpReq) {
        UUID userId = com.dms.security.SecurityUtils.currentUserId();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SYSTEM_ADMIN".equals(a.getAuthority())
                        || "SYSTEM_ADMIN".equals(a.getAuthority()));

        return documentLockService.release(id, userId, isAdmin, httpReq.getRemoteAddr());
    }

    @GetMapping("/{id}/lock-status")
    public DocumentLockStatusResponse lockStatus(@PathVariable("id") UUID id) {
        return documentLockService.status(id, com.dms.security.SecurityUtils.currentUserId());
    }

    /**
     * Whose documents a caller may list. Everyone sees their own; canViewAllDocuments
     * is what lets a role see what the rest of the organisation has uploaded, and
     * asking for the wider scope without it simply falls back to the caller's own.
     */
    private UUID listScopeOwnerId(Authentication auth, boolean all) {
        boolean canSeeAll = permissionService.hasPermission(auth, "canViewAllDocuments");
        return (all && canSeeAll) ? null : com.dms.security.SecurityUtils.currentUserId();
    }

    /**
     * Whether the caller may change documents belonging to other people.
     *
     * Deliberately its own permission rather than being inferred from
     * canViewAllDocuments and canEditDocument together: a role given sight of
     * the whole library so it can route uploads would otherwise silently gain
     * the power to edit and delete everything in it.
     */
    private boolean canManageAcrossOwners(Authentication auth) {
        return permissionService.hasPermission(auth, "canManageAllDocuments");
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewDocument')")
    public List<DocumentResponse> getAll(
            Authentication auth,
            @RequestParam(value = "all", required = false, defaultValue = "false") boolean all) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        UUID ownerId = listScopeOwnerId(auth, all);
        List<Documents> docs = ownerId == null
                ? documentRepository.findAllActive()
                : documentRepository.findAllActiveByOwner(ownerId);

        // One lookup for every owner on the list rather than one per document.
        Map<UUID, String> ownerNames = ownerNames(docs);
        return docs.stream()
                .map(doc -> convertToDTO(doc, ownerNames))
                .collect(Collectors.toList());
    }

    /**
     * One page of documents.
     *
     * Two things happen here that did not happen in the unpaged list: the
     * search and folder filters run in the database, and the owner names are
     * fetched in a single query instead of one per row. The old path did a user
     * lookup for every document it converted.
     */
    @GetMapping("/page")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewDocument')")
    public Page<DocumentResponse> listPage(@RequestParam(defaultValue = "false") boolean all,
                                           @RequestParam(required = false) UUID folderId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        UUID ownerId = listScopeOwnerId(auth, all);
        String term = (search == null || search.isBlank()) ? null : search.trim();
        String statusFilter = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "created_at"));

        Page<Documents> documents = documentRepository.findPage(ownerId, folderId, statusFilter, term, pageable);

        Map<UUID, String> ownerNames = ownerNames(documents.getContent());
        return documents.map(doc -> convertToDTO(doc, ownerNames));
    }

    /**
     * How many documents sit at each status, for the filter chips above the list.
     * Scoped the same way the list is, so the counts always match what the chips
     * actually open.
     */
    @GetMapping("/status-counts")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewDocument')")
    public Map<String, Long> statusCounts(@RequestParam(defaultValue = "false") boolean all,
                                          Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : documentRepository.countByStatusGrouped(listScopeOwnerId(auth, all))) {
            // Documents predating the status column group under NEW, which is where
            // an unrouted document belongs anyway.
            String status = row[0] == null ? WorkflowConstants.DOCUMENT_NEW : row[0].toString();
            counts.merge(status, ((Number) row[1]).longValue(), Long::sum);
        }
        return counts;
    }

    /** Owner names for a page of documents, in one query. */
    private Map<UUID, String> ownerNames(List<Documents> documents) {
        Set<UUID> ownerIds = documents.stream()
                .map(Documents::getOwner_id)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (ownerIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(ownerIds).forEach(u -> names.put(u.getUserId(), u.getUsername()));
        return names;
    }

    private DocumentResponse convertToDTO(Documents doc, Map<UUID, String> ownerNames) {
        UUID ownerId = doc.getOwner_id();
        String ownerName = "Unknown";
        if (ownerId != null) {
            ownerName = ownerId.toString().equals("00000000-0000-0000-0000-000000000000")
                    ? "System"
                    : ownerNames.getOrDefault(ownerId, "Unknown");
        }

        return new DocumentResponse(
                doc.getDocument_id(), doc.getTitle(), doc.getOwner_id(), ownerName,
                doc.getFolder_id(), doc.getCurrent_version_id(), doc.getCreated_at(),
                doc.getDeleted_at(), doc.getFile_size(), doc.isIs_locked(), doc.isIs_deleted(),
                doc.getStatus());
    }

    private DocumentResponse convertToDTO(Documents doc) {
        String ownerName = "Unknown";

        if (doc.getOwner_id() != null) {
            if (doc.getOwner_id().toString().equals("00000000-0000-0000-0000-000000000000")) {
                ownerName = "System";
            } else {
                Optional<User> owner = userRepository.findById(doc.getOwner_id());
                if (owner.isPresent()) {
                    ownerName = owner.get().getUsername();
                }
            }
        }

        return new DocumentResponse(
                doc.getDocument_id(),
                doc.getTitle(),
                doc.getOwner_id(),
                ownerName,
                doc.getFolder_id(),
                doc.getCurrent_version_id(),
                doc.getCreated_at(),
                doc.getDeleted_at(),
                doc.getFile_size(),
                doc.isIs_locked(),
                doc.isIs_deleted(),
                doc.getStatus()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getById(@PathVariable("id") UUID id,
                                                    HttpServletRequest request) {
        Optional<Documents> doc = documentRepository.findActiveById(id);
        String ip = getClientIp(request);

        if (doc.isPresent()) {
            auditLogService.createAuditLog("DOCUMENT_VIEWED", id, ip, "SUCCESS");
            return ResponseEntity.ok(convertToDTO(doc.get()));
        } else {
            auditLogService.createAuditLog("DOCUMENT_VIEWED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/trash")
    public List<DocumentResponse> getDeleted(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        // The recycle bin has its own scope permission: seeing everyone's deleted
        // documents is a decision about the bin, not a side effect of being able
        // to edit documents across owners.
        List<Documents> deleted = permissionService.hasPermission(auth, "canViewAllDeletedDocuments")
                ? documentRepository.findAllDeleted()
                : documentRepository.findAllDeletedByOwner(
                        com.dms.security.SecurityUtils.currentUserId());

        Map<UUID, String> ownerNames = ownerNames(deleted);
        return deleted.stream()
                .map(doc -> convertToDTO(doc, ownerNames))
                .collect(Collectors.toList());
    }

    // The permission keys below are the same ones the UI uses to show or hide
    // each button, so enforcing them here changes nothing for a legitimate user
    // and closes the gap for anyone calling the API directly.
    @PostMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canCreateDocument')")
    public ResponseEntity<DocumentResponse> create(@RequestBody Documents doc,
                                                   HttpServletRequest request) {
        if (doc.getDocument_id() == null) {
            doc.setDocument_id(UUID.randomUUID());
        }
        if (doc.getCreated_at() == null) {
            doc.setCreated_at(LocalDateTime.now());
        }
        doc.setIs_deleted(false);
        doc.setOwner_id(com.dms.security.SecurityUtils.currentUserId());
        if (doc.getStatus() == null || doc.getStatus().isBlank()) {
            doc.setStatus(WorkflowConstants.DOCUMENT_NEW);
        }

        Documents saved = documentRepository.save(doc);
        String ip = getClientIp(request);
        auditLogService.createAuditLog("DOCUMENT_CREATED", saved.getDocument_id(), ip, "SUCCESS");

        return ResponseEntity.created(URI.create("/api/documents/" + saved.getDocument_id()))
                .body(convertToDTO(saved));
    }

    @PostMapping("/upload")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canCreateDocument')")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "description", required = false) String description,
            Authentication auth,
            HttpServletRequest request) {

        if (auth == null || !auth.isAuthenticated()) {
            DocumentUploadResponse errorResponse = new DocumentUploadResponse(
                    null, null, null, file != null ? file.getOriginalFilename() : null,
                    "User not authenticated", false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String ip = getClientIp(request);

        try {
            UploadDocumentRequest uploadReq = new UploadDocumentRequest(title, folderId, category, tags, description);
            DocumentUploadResponse response = documentUploadService.uploadDocument(file, uploadReq, user.getUserId());

            if (!response.isSuccess()) {
                auditLogService.createAuditLog("DOCUMENT_UPLOAD", null, ip, "FAILED");
                notificationService.sendNotification(user.getUserId(), "Failed: " + response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }

            auditLogService.createAuditLog("DOCUMENT_UPLOAD", response.getDocumentId(), ip, "SUCCESS");
            notificationService.sendNotification(user.getUserId(), "Document '" + title + "' uploaded successfully");

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            auditLogService.createAuditLog("DOCUMENT_UPLOAD", null, ip, "FAILED");
            notificationService.sendNotification(user.getUserId(), "Upload failed due to system error");

            DocumentUploadResponse errorResponse = new DocumentUploadResponse(
                    null, null, null, "Upload failed: " + e.getMessage(), false);
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditDocument')")
    public ResponseEntity<DocumentResponse> update(@PathVariable("id") UUID id,
                                                   @RequestBody Documents update,
                                                   HttpServletRequest request,
                                                   Authentication auth) {
        UUID userId = com.dms.security.SecurityUtils.currentUserId();
        Optional<Documents> existingOpt = canManageAcrossOwners(auth)
                ? documentRepository.findActiveById(id)
                : documentRepository.findActiveByIdAndOwner(id, userId);
        String ip = getClientIp(request);

        if (existingOpt.isEmpty()) {
            auditLogService.createAuditLog("DOCUMENT_EDITED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }

        Documents existing = existingOpt.get();
        existing.setTitle(update.getTitle());
        existing.setFolder_id(update.getFolder_id());
        existing.setCurrent_version_id(update.getCurrent_version_id());
        existing.setIs_locked(update.isIs_locked());

        if (update.getCreated_at() != null) {
            existing.setCreated_at(update.getCreated_at());
        }

        Documents saved = documentRepository.save(existing);
        auditLogService.createAuditLog("DOCUMENT_EDITED", id, ip, "SUCCESS");
        notificationService.sendInternalSystemNotification("Document " + saved.getTitle() + " modified");

        return ResponseEntity.ok(convertToDTO(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canDeleteDocument')")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id,
                                       HttpServletRequest request,
                                       Authentication auth) {
        String ip = getClientIp(request);
        int updated = canManageAcrossOwners(auth)
                ? documentRepository.softDeleteById(id)
                : documentRepository.softDeleteByIdAndOwner(id, com.dms.security.SecurityUtils.currentUserId());

        if (updated == 0) {
            auditLogService.createAuditLog("DOCUMENT_DELETED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }

        auditLogService.createAuditLog("DOCUMENT_DELETED", id, ip, "SUCCESS");
        notificationService.sendInternalSystemNotification("Document deleted");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canRestoreRecycleBin')")
    public ResponseEntity<Void> restore(@PathVariable("id") UUID id,
                                        HttpServletRequest request,
                                        Authentication auth) {
        String ip = getClientIp(request);
        int updated = canManageAcrossOwners(auth)
                ? documentRepository.restoreById(id)
                : documentRepository.restoreByIdAndOwner(id, com.dms.security.SecurityUtils.currentUserId());

        if (updated == 0) {
            auditLogService.createAuditLog("DOCUMENT_RESTORED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }

        auditLogService.createAuditLog("DOCUMENT_RESTORED", id, ip, "SUCCESS");
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<DocumentResponse> approveDocument(@PathVariable("id") UUID id,
                                                            HttpServletRequest request) {
        Optional<Documents> docOpt = documentRepository.findById(id);
        String ip = getClientIp(request);

        if (docOpt.isEmpty()) {
            auditLogService.createAuditLog("DOCUMENT_APPROVED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }

        Documents doc = docOpt.get();
        documentRepository.save(doc);
        auditLogService.createAuditLog("DOCUMENT_APPROVED", id, ip, "SUCCESS");
        notificationService.sendInternalSystemNotification("Document approved: " + doc.getTitle());

        return ResponseEntity.ok(convertToDTO(doc));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        return (ip == null || ip.isEmpty()) ? request.getRemoteAddr() : ip;
    }
}
