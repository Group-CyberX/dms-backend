package com.dms.rest;

import com.dms.models.DocumentVersions;
import com.dms.service.DocumentVersionService;
import com.dms.service.AuditLogService;
import com.dms.models.AuditLog;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/{documentId}/versions")
public class DocumentVersionController {

    private final DocumentVersionService documentVersionService;
    private final AuditLogService auditLogService;

    public DocumentVersionController(DocumentVersionService documentVersionService,
                                     AuditLogService auditLogService) {
        this.documentVersionService = documentVersionService;
        this.auditLogService = auditLogService;
    }

    // 1️⃣ Upload new version + audit
    @PostMapping("/upload")
    public ResponseEntity<?> uploadNewVersion(@PathVariable UUID documentId,
                                              @RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) {
        try {
            DocumentVersions version = documentVersionService.uploadNewVersion(documentId, file);

            createAuditLog("VERSION_UPLOADED", documentId, request, "SUCCESS");

            return ResponseEntity.ok(version);

        } catch (IllegalArgumentException e) {
            createAuditLog("VERSION_UPLOADED", documentId, request, "FAILED");
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (IOException e) {
            createAuditLog("VERSION_UPLOADED", documentId, request, "FAILED");
            return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage());
        }
    }

    // 2️⃣ List versions
    @GetMapping
    public ResponseEntity<List<DocumentVersions>> getAllVersions(@PathVariable UUID documentId) {
        return ResponseEntity.ok(documentVersionService.listVersions(documentId));
    }

    // 3️⃣ Get version
    @GetMapping("/{versionId}")
    public ResponseEntity<DocumentVersions> getVersion(@PathVariable UUID documentId,
                                                       @PathVariable UUID versionId) {

        Optional<DocumentVersions> version =
                documentVersionService.getVersion(documentId, versionId);

        return version.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 4️⃣ Download version (DEV feature kept)
    @GetMapping("/{versionId}/download")
    public ResponseEntity<?> downloadVersion(@PathVariable UUID documentId,
                                             @PathVariable UUID versionId) {
        try {
            byte[] fileBytes = documentVersionService.getVersionFileBytes(documentId, versionId);

            Optional<DocumentVersions> version =
                    documentVersionService.getVersion(documentId, versionId);

            if (version.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String fileName = version.get().getS3_bucket_key();
            if (fileName != null && fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .body(fileBytes);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Download failed: " + e.getMessage());
        }
    }

    // 5️⃣ Restore version + audit
    @PostMapping("/{versionId}/restore")
    public ResponseEntity<?> restoreVersion(@PathVariable UUID documentId,
                                            @PathVariable UUID versionId,
                                            HttpServletRequest request) {
        try {
            DocumentVersions restored =
                    documentVersionService.restoreVersion(documentId, versionId);

            createAuditLog("VERSION_RESTORED", documentId, request, "SUCCESS");

            return ResponseEntity.ok(restored);

        } catch (IllegalArgumentException e) {
            createAuditLog("VERSION_RESTORED", documentId, request, "FAILED");
            return ResponseEntity.notFound().build();
        }
    }

    // 6️⃣ Delete version + audit
    @DeleteMapping("/{versionId}")
    public ResponseEntity<?> deleteVersion(@PathVariable UUID documentId,
                                           @PathVariable UUID versionId,
                                           HttpServletRequest request) {
        try {
            documentVersionService.deleteVersion(documentId, versionId);

            createAuditLog("VERSION_DELETED", documentId, request, "SUCCESS");

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            createAuditLog("VERSION_DELETED", documentId, request, "FAILED");
            return ResponseEntity.notFound().build();

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Delete failed: " + e.getMessage());
        }
    }

    // 🔧 Audit helper
    private void createAuditLog(String action,
                                UUID entityId,
                                HttpServletRequest request,
                                String status) {

        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntity_id(entityId);
        log.setIp(request.getRemoteAddr());
        log.setStatus(status);

        auditLogService.saveLog(log);
    }
}