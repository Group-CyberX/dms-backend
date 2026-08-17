package com.dms.rest;

import com.dms.models.DocumentVersions;
import com.dms.security.SecurityUtils;
import com.dms.service.AuditLogService;
import com.dms.service.DocumentLockService;
import com.dms.service.DocumentVersionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final DocumentLockService documentLockService;

    public DocumentVersionController(DocumentVersionService documentVersionService,
                                     AuditLogService auditLogService,
                                     DocumentLockService documentLockService) {
        this.documentVersionService = documentVersionService;
        this.auditLogService = auditLogService;
        this.documentLockService = documentLockService;
    }

    @PostMapping("/upload")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditDocument')")
    public ResponseEntity<?> uploadNewVersion(@PathVariable("documentId") UUID documentId,
                                              @RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) {
        // Outside the try: a lock conflict must surface as 409, not be swallowed below.
        documentLockService.assertCanMutate(documentId, SecurityUtils.currentUserId());
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

    @GetMapping
    public ResponseEntity<List<DocumentVersions>> getAllVersions(@PathVariable("documentId") UUID documentId) {
        return ResponseEntity.ok(documentVersionService.listVersions(documentId));
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<DocumentVersions> getVersion(@PathVariable("documentId") UUID documentId,
                                                       @PathVariable("versionId") UUID versionId) {
        Optional<DocumentVersions> version = documentVersionService.getVersion(documentId, versionId);
        return version.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{versionId}/restore")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditDocument')")
    public ResponseEntity<?> restoreVersion(@PathVariable("documentId") UUID documentId,
                                            @PathVariable("versionId") UUID versionId,
                                            HttpServletRequest request) {
        documentLockService.assertCanMutate(documentId, SecurityUtils.currentUserId());
        try {
            DocumentVersions restored = documentVersionService.restoreVersion(documentId, versionId);
            createAuditLog("VERSION_RESTORED", documentId, request, "SUCCESS");
            return ResponseEntity.ok(restored);
        } catch (IllegalArgumentException e) {
            createAuditLog("VERSION_RESTORED", documentId, request, "FAILED");
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{versionId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canDeleteDocument')")
    public ResponseEntity<?> deleteVersion(@PathVariable("documentId") UUID documentId,
                                           @PathVariable("versionId") UUID versionId,
                                           HttpServletRequest request) {
        documentLockService.assertCanMutate(documentId, SecurityUtils.currentUserId());
        try {
            documentVersionService.deleteVersion(documentId, versionId);
            createAuditLog("VERSION_DELETED", documentId, request, "SUCCESS");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            createAuditLog("VERSION_DELETED", documentId, request, "FAILED");
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            createAuditLog("VERSION_DELETED", documentId, request, "FAILED");
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            createAuditLog("VERSION_DELETED", documentId, request, "FAILED");
            return ResponseEntity.internalServerError().body("Delete failed: " + e.getMessage());
        }
    }

    /**
     * Taking a copy of a document out of the system is the event a compliance
     * reviewer cares most about, and it was the one event not being recorded.
     * The file name goes in the details so the row says which copy was taken.
     */
    @GetMapping("/{versionId}/download")
    public ResponseEntity<?> downloadVersion(@PathVariable("documentId") UUID documentId,
                                             @PathVariable("versionId") UUID versionId,
                                             HttpServletRequest request) {
        try {
            byte[] fileBytes = documentVersionService.getVersionFileBytes(documentId, versionId);
            Optional<DocumentVersions> version = documentVersionService.getVersion(documentId, versionId);

            if (version.isEmpty()) {
                createAuditLog("DOCUMENT_DOWNLOADED", documentId, request, "FAILED");
                return ResponseEntity.notFound().build();
            }

            String fileName = version.get().getS3_bucket_key();
            if (fileName != null && fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
            }

            auditLogService.tryRecordCurrentUser("DOCUMENT_DOWNLOADED", documentId,
                    getClientIp(request), "SUCCESS",
                    "downloaded " + fileName + " (version " + version.get().getVersion_number() + ")");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .body(fileBytes);
        } catch (IllegalArgumentException e) {
            createAuditLog("DOCUMENT_DOWNLOADED", documentId, request, "FAILED");
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            createAuditLog("DOCUMENT_DOWNLOADED", documentId, request, "FAILED");
            return ResponseEntity.internalServerError().body("Download failed: " + e.getMessage());
        }
    }

    record PresignedUrlResponse(String url, long expiresInSeconds) {}

    @GetMapping("/{versionId}/download-url")
    public ResponseEntity<?> getDownloadUrl(@PathVariable("documentId") UUID documentId,
                                            @PathVariable("versionId") UUID versionId,
                                            HttpServletRequest request) {
        try {
            String url = documentVersionService.generatePresignedDownloadUrl(documentId, versionId);
            long ttl = documentVersionService.getPresignExpirySeconds();

            // Handing out a presigned URL is handing out the file: S3 serves it
            // directly afterwards, so this is the last moment the system can
            // record that the copy was taken.
            auditLogService.tryRecordCurrentUser("DOCUMENT_DOWNLOADED", documentId,
                    getClientIp(request), "SUCCESS",
                    "issued a presigned download link valid for " + ttl + "s");

            return ResponseEntity.ok(new PresignedUrlResponse(url, ttl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create presigned URL: " + e.getMessage());
        }
    }

    private void createAuditLog(String action, UUID documentId, HttpServletRequest request, String status) {
        String ip = getClientIp(request);
        auditLogService.createAuditLog(action, documentId, ip, status);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        return (ip == null || ip.isEmpty()) ? request.getRemoteAddr() : ip;
    }
}
