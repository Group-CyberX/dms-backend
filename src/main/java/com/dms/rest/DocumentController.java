package com.dms.rest;

import com.dms.dao.DocumentRepository;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.UploadDocumentRequest;
import com.dms.models.Documents;
import com.dms.service.DocumentUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import com.dms.service.AuditLogService;
import com.dms.models.AuditLog;
import com.dms.service.NotificationService;

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

    public DocumentController(DocumentRepository documentRepository, DocumentUploadService documentUploadService, AuditLogService auditLogService,NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.documentUploadService = documentUploadService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Documents> getAll() {
        return documentRepository.findAll();
    }

    // audit document viewd
    @GetMapping("/{id}")
    public ResponseEntity<Documents> getById(@PathVariable("id") UUID id, HttpServletRequest request) {
        Optional<Documents> doc = documentRepository.findById(id);
        if (doc.isPresent()) {
            createAuditLog("DOCUMENT_VIEWED", id, request, "SUCCESS");
            return ResponseEntity.ok(doc.get());
        } else {
            createAuditLog("DOCUMENT_VIEWED", id, request, "FAILED");
            return ResponseEntity.notFound().build();
        }
    }

    // audit document created
    @PostMapping
    public ResponseEntity<Documents> create(@RequestBody Documents doc, HttpServletRequest request) {
        if (doc.getDocument_id() == null) {
            doc.setDocument_id(UUID.randomUUID());
        }
        if (doc.getCreated_at() == null) {
            doc.setCreated_at(LocalDateTime.now());
        }
        Documents saved = documentRepository.save(doc);
        createAuditLog("DOCUMENT_CREATED", saved.getDocument_id(), request, "SUCCESS");
        return ResponseEntity.created(URI.create("/api/documents/" + saved.getDocument_id())).body(saved);
    }

    // audit document upload
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {
        try {
            UploadDocumentRequest uploadReq = new UploadDocumentRequest(title, folderId, category, tags, description);
            DocumentUploadResponse response = documentUploadService.uploadDocument(file, uploadReq);

            // check if the service ACTUALLY succeeded before celebrating
            if (!response.isSuccess()) {
                // It failed a validation rule (like invalid filename or tags)
                createAuditLog("DOCUMENT_UPLOAD", null, request, "FAILED");
                // Return a 400 Bad Request so the React frontend knows it failed
                return ResponseEntity.badRequest().body(response);
            }

            UUID newDocumentId = response.getDocumentId();
            createAuditLog("DOCUMENT_UPLOAD", newDocumentId, request, "SUCCESS");

            // Send the notification
            UUID testUserId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-1234567890ab");
            String notificationMessage = "Your document '" + title + "' was successfully uploaded.";
            notificationService.sendNotification(testUserId, notificationMessage);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            createAuditLog("DOCUMENT_UPLOAD", null, request, "FAILED");
            DocumentUploadResponse errorResponse = new DocumentUploadResponse(
                    null, null, null, "Upload failed: " + e.getMessage(), false
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // audit document update
    @PutMapping("/{id}")
    public ResponseEntity<Documents> update(@PathVariable("id") UUID id, @RequestBody Documents update, HttpServletRequest request) {
        Optional<Documents> existingOpt = documentRepository.findById(id);
        if (existingOpt.isEmpty()) {
            createAuditLog("DOCUMENT_EDITED", id, request, "FAILED");
            return ResponseEntity.notFound().build();
        }
        Documents existing = existingOpt.get();
        existing.setTitle(update.getTitle());
        existing.setOwner_id(update.getOwner_id());
        existing.setFolder_id(update.getFolder_id());
        existing.setCurrent_version_id(update.getCurrent_version_id());
        existing.setIs_locked(update.isIs_locked());
        existing.setIs_deleted(update.isIs_deleted());
        if (update.getCreated_at() != null) {
            existing.setCreated_at(update.getCreated_at());
        }
        Documents saved = documentRepository.save(existing);
        createAuditLog("DOCUMENT_EDITED", saved.getDocument_id(), request, "SUCCESS");
        return ResponseEntity.ok(saved);
    }

    // audit document delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, HttpServletRequest request) {
        if (!documentRepository.existsById(id)) {
            createAuditLog("DOCUMENT_DELETED", id, request, "FAILED");
            return ResponseEntity.notFound().build();
        }
        documentRepository.deleteById(id);
        createAuditLog("DOCUMENT_DELETED", id, request, "SUCCESS");
        return ResponseEntity.noContent().build();
    }

    // helper method
    private void createAuditLog(String action, UUID entityId, HttpServletRequest request, String status) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntity_id(entityId);
        log.setIp(request.getRemoteAddr());
        log.setStatus(status);
        auditLogService.saveLog(log);
    }
}