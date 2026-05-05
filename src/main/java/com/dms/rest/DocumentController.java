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

    public DocumentController(DocumentRepository documentRepository, DocumentUploadService documentUploadService, AuditLogService auditLogService, NotificationService notificationService) {
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
        String ip = getClientIp(request); // Extract the String IP
        if (doc.isPresent()) {
            auditLogService.createAuditLog("DOCUMENT_VIEWED", id, ip, "SUCCESS");
            return ResponseEntity.ok(doc.get());
        } else {
            auditLogService.createAuditLog("DOCUMENT_VIEWED", id, ip, "FAILED");
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
        String ip = getClientIp(request); // Extract the String IP

        auditLogService.createAuditLog("DOCUMENT_CREATED", saved.getDocument_id(), ip, "SUCCESS");
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
        UUID testUserId = UUID.fromString("0b0f8543-672e-4a5a-bb8d-99da74f94f90");
        String ip = getClientIp(request); // Extract the String IP

        try {
            UploadDocumentRequest uploadReq = new UploadDocumentRequest(title, folderId, category, tags, description);
            DocumentUploadResponse response = documentUploadService.uploadDocument(file, uploadReq);

            // We check if the service ACTUALLY succeeded before celebrating
            if (!response.isSuccess()) {
                // It failed a validation rule (like invalid filename or tags)
                auditLogService.createAuditLog("DOCUMENT_UPLOAD", null, ip, "FAILED");
                //Send the failed notification
                notificationService.sendNotification(testUserId, "Failed: " + response.getMessage());
                // Return a 400 Bad Request so the React frontend knows it failed
                return ResponseEntity.badRequest().body(response);
            }

            UUID newDocumentId = response.getDocumentId();
            auditLogService.createAuditLog("DOCUMENT_UPLOAD", newDocumentId, ip, "SUCCESS");


            // Send the notification
            String notificationMessage = "Your document '" + title + "' was successfully uploaded.";
            notificationService.sendNotification(testUserId, notificationMessage);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            auditLogService.createAuditLog("DOCUMENT_UPLOAD", null, ip, "FAILED");
            notificationService.sendNotification(testUserId, "Error: Upload failed due to system error.");
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
        String ip = getClientIp(request); // Extract the String IP

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
        existing.setIs_deleted(update.isIs_deleted());
        if (update.getCreated_at() != null) {
            existing.setCreated_at(update.getCreated_at());
        }
        Documents saved = documentRepository.save(existing);
        auditLogService.createAuditLog("DOCUMENT_EDITED", saved.getDocument_id(), ip, "SUCCESS");
        notificationService.sendInternalSystemNotification("Document "+saved.getTitle()+" was modified.");
        return ResponseEntity.ok(saved);
    }


    //for the testing purposes of audit and notifications
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, HttpServletRequest request) {
        Optional<Documents> docOpt = documentRepository.findById(id);
        String ip = getClientIp(request); // Extract the String IP

        if (docOpt.isPresent()) {
            Documents doc = docOpt.get();

            doc.setIs_deleted(true);
            documentRepository.save(doc);

            auditLogService.createAuditLog("DOCUMENT_DELETED", id, ip, "SUCCESS");

            notificationService.sendInternalSystemNotification("Document '" + doc.getTitle() + "' has been deleted.");

            return ResponseEntity.noContent().build();
        } else {
            auditLogService.createAuditLog("DOCUMENT_DELETED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }
    }

    //audit document approve
    @PutMapping("/{id}/approve")
    public ResponseEntity<Documents> approveDocument(@PathVariable("id") UUID id, HttpServletRequest request) {
        Optional<Documents> docOpt = documentRepository.findById(id);
        String ip = getClientIp(request); // Extract the String IP

        if (docOpt.isEmpty()) {
            auditLogService.createAuditLog("DOCUMENT_APPROVED", id, ip, "FAILED");
            return ResponseEntity.notFound().build();
        }

        Documents doc = docOpt.get();

        documentRepository.save(doc);
        auditLogService.createAuditLog("DOCUMENT_APPROVED", id, ip, "SUCCESS");

        // send Notification
        notificationService.sendInternalSystemNotification("Document " + doc.getTitle() + " has been approved!");

        return ResponseEntity.ok(doc);
    }

    // helper method
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getHeader("X-Forwarded-For");
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            remoteAddr = request.getRemoteAddr();
        }
        return remoteAddr;
    }
}