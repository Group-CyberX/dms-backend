package com.dms.rest;

import com.dms.dao.DocumentRepository;
import com.dms.dao.UserRepository;
import com.dms.dto.DocumentResponse;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.UploadDocumentRequest;
import com.dms.models.Documents;
import com.dms.models.User;
import com.dms.service.AuditLogService;
import com.dms.service.DocumentUploadService;
import com.dms.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    public DocumentController(DocumentRepository documentRepository,
                              DocumentUploadService documentUploadService,
                              AuditLogService auditLogService,
                              NotificationService notificationService,
                              UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.documentUploadService = documentUploadService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<DocumentResponse> getAll(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        return documentRepository.findAllActiveByOwner(
                        com.dms.security.SecurityUtils.currentUserId())
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
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
                doc.isIs_deleted()
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

        return documentRepository.findAllDeletedByOwner(
                        com.dms.security.SecurityUtils.currentUserId())
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
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

        Documents saved = documentRepository.save(doc);
        String ip = getClientIp(request);
        auditLogService.createAuditLog("DOCUMENT_CREATED", saved.getDocument_id(), ip, "SUCCESS");

        return ResponseEntity.created(URI.create("/api/documents/" + saved.getDocument_id()))
                .body(convertToDTO(saved));
    }

    @PostMapping("/upload")
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
    public ResponseEntity<DocumentResponse> update(@PathVariable("id") UUID id,
                                                   @RequestBody Documents update,
                                                   HttpServletRequest request) {
        UUID userId = com.dms.security.SecurityUtils.currentUserId();
        Optional<Documents> existingOpt = documentRepository.findActiveByIdAndOwner(id, userId);
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
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id,
                                       HttpServletRequest request) {
        String ip = getClientIp(request);
        int updated = documentRepository.softDeleteByIdAndOwner(id, com.dms.security.SecurityUtils.currentUserId());

        if (updated == 0) {
            auditLogService.createAuditLog("DOCUMENT_DELETED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }

        auditLogService.createAuditLog("DOCUMENT_DELETED", id, ip, "SUCCESS");
        notificationService.sendInternalSystemNotification("Document deleted");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable("id") UUID id,
                                        HttpServletRequest request) {
        String ip = getClientIp(request);
        int updated = documentRepository.restoreByIdAndOwner(id, com.dms.security.SecurityUtils.currentUserId());

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
