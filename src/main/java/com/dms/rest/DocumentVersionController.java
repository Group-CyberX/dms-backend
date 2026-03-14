package com.dms.rest;

import com.dms.models.DocumentVersions;
import com.dms.service.DocumentVersionService;
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

    // 1️⃣ Upload new document version
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

    // 2️⃣ Get all versions of a document
    @GetMapping
    public ResponseEntity<List<DocumentVersions>> getAllVersions(@PathVariable("documentId") UUID documentId) {
        List<DocumentVersions> versions = documentVersionService.listVersions(documentId);
        return ResponseEntity.ok(versions);
    }

    // 3️⃣ Get specific version details
    @GetMapping("/{versionId}")
    public ResponseEntity<DocumentVersions> getVersion(@PathVariable("documentId") UUID documentId,
                                                       @PathVariable("versionId") UUID versionId) {
        Optional<DocumentVersions> version = documentVersionService.getVersion(documentId, versionId);
        return version.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 5️⃣ Restore previous version
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

    // 6️⃣ Delete version (optional)
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
}
