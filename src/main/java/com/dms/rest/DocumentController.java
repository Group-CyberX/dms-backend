package com.dms.rest;

import com.dms.dao.DocumentRepository;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.UploadDocumentRequest;
import com.dms.models.Documents;
import com.dms.service.DocumentUploadService;
import com.dms.service.AuditLogService;
import com.dms.service.NotificationService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentUploadService documentUploadService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public DocumentController(DocumentRepository documentRepository,
                              DocumentUploadService documentUploadService,
                              AuditLogService auditLogService,
                              NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.documentUploadService = documentUploadService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    // Get all active documents
    @GetMapping
    public List<Documents> getAll() {
        return documentRepository.findAllActive();
    }

    //  Get by ID with audit
    @GetMapping("/{id}")
    public ResponseEntity<Documents> getById(@PathVariable UUID id, HttpServletRequest request) {
        Optional<Documents> doc = documentRepository.findActiveById(id);
        String ip = getClientIp(request);

        if (doc.isPresent()) {
            auditLogService.createAuditLog("DOCUMENT_VIEWED", id, ip, "SUCCESS");
            return ResponseEntity.ok(doc.get());
        } else {
            auditLogService.createAuditLog("DOCUMENT_VIEWED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }
    }

    //  Trash view
    @GetMapping("/trash")
    public List<Documents> getDeleted() {
        return documentRepository.findAllDeleted();
    }

    //  Create with audit
    @PostMapping
    public ResponseEntity<Documents> create(@RequestBody Documents doc, HttpServletRequest request) {
        if (doc.getDocument_id() == null) {
            doc.setDocument_id(UUID.randomUUID());
        }
        if (doc.getCreated_at() == null) {
            doc.setCreated_at(LocalDateTime.now());
        }

        doc.setIs_deleted(false);

        Documents saved = documentRepository.save(doc);
        String ip = getClientIp(request);

        auditLogService.createAuditLog("DOCUMENT_CREATED", saved.getDocument_id(), ip, "SUCCESS");

        return ResponseEntity.created(URI.create("/api/documents/" + saved.getDocument_id())).body(saved);
    }

    //  Upload with audit + notifications
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {

        UUID testUserId = UUID.fromString("0b0f8543-672e-4a5a-bb8d-99da74f94f90");
        String ip = getClientIp(request);

        try {
            UploadDocumentRequest uploadReq = new UploadDocumentRequest(title, folderId, category, tags, description);
            DocumentUploadResponse response = documentUploadService.uploadDocument(file, uploadReq);

            if (!response.isSuccess()) {
                auditLogService.createAuditLog("DOCUMENT_UPLOAD", null, ip, "FAILED");
                notificationService.sendNotification(testUserId, "Failed: " + response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }

            UUID newDocumentId = response.getDocumentId();

            auditLogService.createAuditLog("DOCUMENT_UPLOAD", newDocumentId, ip, "SUCCESS");
            notificationService.sendNotification(testUserId, "Document '" + title + "' uploaded successfully");

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            auditLogService.createAuditLog("DOCUMENT_UPLOAD", null, ip, "FAILED");
            notificationService.sendNotification(testUserId, "Upload failed due to system error");

            DocumentUploadResponse errorResponse =
                    new DocumentUploadResponse(null, null, null, "Upload failed: " + e.getMessage(), false);

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    //  Update with audit
    @PutMapping("/{id}")
    public ResponseEntity<Documents> update(@PathVariable UUID id,
                                            @RequestBody Documents update,
                                            HttpServletRequest request) {

        Optional<Documents> existingOpt = documentRepository.findById(id);
        String ip = getClientIp(request);

        if (existingOpt.isEmpty()) {
            auditLogService.createAuditLog("DOCUMENT_EDITED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }

        Documents existing = existingOpt.get();

        existing.setTitle(update.getTitle());
        existing.setOwner_id(update.getOwner_id());
        existing.setFolder_id(update.getFolder_id());
        existing.setCurrent_version_id(update.getCurrent_version_id());
        existing.setIs_locked(update.isIs_locked());

        if (update.getCreated_at() != null) {
            existing.setCreated_at(update.getCreated_at());
        }

        Documents saved = documentRepository.save(existing);

        auditLogService.createAuditLog("DOCUMENT_EDITED", id, ip, "SUCCESS");
        notificationService.sendInternalSystemNotification("Document " + saved.getTitle() + " modified");

        return ResponseEntity.ok(saved);
    }

    //  Soft delete with audit
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        String ip = getClientIp(request);

        int updated = documentRepository.softDeleteById(id);

        if (updated == 0) {
            auditLogService.createAuditLog("DOCUMENT_DELETED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }

        auditLogService.createAuditLog("DOCUMENT_DELETED", id, ip, "SUCCESS");
        notificationService.sendInternalSystemNotification("Document deleted");

        return ResponseEntity.noContent().build();
    }

    // Restore
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable UUID id) {
        int updated = documentRepository.restoreById(id);
        return updated == 0 ? ResponseEntity.notFound().build() : ResponseEntity.noContent().build();
    }

    //  Approve with audit
    @PutMapping("/{id}/approve")
    public ResponseEntity<Documents> approveDocument(@PathVariable UUID id, HttpServletRequest request) {
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

        return ResponseEntity.ok(doc);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        return (ip == null || ip.isEmpty()) ? request.getRemoteAddr() : ip;
    }
}