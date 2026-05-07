package com.dms.rest;

import com.dms.models.DocumentVersions;
import com.dms.service.DocumentVersionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/{documentId}/versions")
public class DocumentVersionController {

    private final DocumentVersionService documentVersionService;

    public DocumentVersionController(DocumentVersionService documentVersionService) {
        this.documentVersionService = documentVersionService;
    }

    // Upload new document version
    @PostMapping("/upload")
    public ResponseEntity<?> uploadNewVersion(@PathVariable("documentId") UUID documentId,
                                              @RequestParam("file") MultipartFile file) {
        try {
            DocumentVersions version = documentVersionService.uploadNewVersion(documentId, file);
            return ResponseEntity.ok(version);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage());
        }
    }

    // Get all versions of a document
    @GetMapping
    public ResponseEntity<List<DocumentVersions>> getAllVersions(@PathVariable("documentId") UUID documentId) {
        List<DocumentVersions> versions = documentVersionService.listVersions(documentId);
        return ResponseEntity.ok(versions);
    }

    // Get specific version details
    @GetMapping("/{versionId}")
    public ResponseEntity<DocumentVersions> getVersion(@PathVariable("documentId") UUID documentId,
                                                       @PathVariable("versionId") UUID versionId) {
        Optional<DocumentVersions> version = documentVersionService.getVersion(documentId, versionId);
        return version.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Restore previous version
    @PostMapping("/{versionId}/restore")
    public ResponseEntity<?> restoreVersion(@PathVariable("documentId") UUID documentId,
                                            @PathVariable("versionId") UUID versionId) {
        try {
            DocumentVersions restored = documentVersionService.restoreVersion(documentId, versionId);
            return ResponseEntity.ok(restored);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Delete version (optional)
    @DeleteMapping("/{versionId}")
    public ResponseEntity<?> deleteVersion(@PathVariable("documentId") UUID documentId,
                                           @PathVariable("versionId") UUID versionId) {
        try {
            documentVersionService.deleteVersion(documentId, versionId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Delete failed: " + e.getMessage());
        }
    }

    // Download specific version (existing proxied download)
    @GetMapping("/{versionId}/download")
    public ResponseEntity<?> downloadVersion(@PathVariable("documentId") UUID documentId,
                                             @PathVariable("versionId") UUID versionId) {
        try {
            byte[] fileBytes = documentVersionService.getVersionFileBytes(documentId, versionId);
            Optional<DocumentVersions> version = documentVersionService.getVersion(documentId, versionId);

            if (version.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String fileName = version.get().getS3_bucket_key();
            if (fileName != null && fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .body(fileBytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Download failed: " + e.getMessage());
        }
    }

    // Generate pre-signed S3 download URL
    record PresignedUrlResponse(String url, long expiresInSeconds) {}

    @GetMapping("/{versionId}/download-url")
    public ResponseEntity<?> getDownloadUrl(@PathVariable("documentId") UUID documentId,
                                            @PathVariable("versionId") UUID versionId) {
        try {
            String url = documentVersionService.generatePresignedDownloadUrl(documentId, versionId);
            long ttl = documentVersionService.getPresignExpirySeconds();
            return ResponseEntity.ok(new PresignedUrlResponse(url, ttl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create presigned URL: " + e.getMessage());
        }
    }
}
