package com.dms.rest;

import com.dms.dao.DocumentRepository;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.UploadDocumentRequest;
import com.dms.models.Documents;
import com.dms.service.DocumentUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    public DocumentController(DocumentRepository documentRepository, DocumentUploadService documentUploadService) {
        this.documentRepository = documentRepository;
        this.documentUploadService = documentUploadService;
    }

    @GetMapping
    public List<Documents> getAll() {
        return documentRepository.findAllActive();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Documents> getById(@PathVariable("id") UUID id) {
        return documentRepository.findActiveById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/trash")
    public List<Documents> getDeleted() {
        return documentRepository.findAllDeleted();
    }

    @PostMapping
    public ResponseEntity<Documents> create(@RequestBody Documents doc) {
        if (doc.getDocument_id() == null) {
            doc.setDocument_id(UUID.randomUUID());
        }
        if (doc.getCreated_at() == null) {
            doc.setCreated_at(LocalDateTime.now());
        }
        // Ensure new documents are not created as deleted inadvertently
        doc.setIs_deleted(false);
        Documents saved = documentRepository.save(doc);
        return ResponseEntity.created(URI.create("/api/documents/" + saved.getDocument_id())).body(saved);
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "description", required = false) String description) {
        try {
            UploadDocumentRequest request = new UploadDocumentRequest(title, folderId, category, tags, description);
            DocumentUploadResponse response = documentUploadService.uploadDocument(file, request);
            if (!response.isSuccess()) {
                return ResponseEntity.badRequest().body(response);
            }
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            DocumentUploadResponse errorResponse = new DocumentUploadResponse(
                    null, null, null, file.getOriginalFilename(), "Upload failed: " + e.getMessage(), false
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Documents> update(@PathVariable("id") UUID id, @RequestBody Documents update) {
        Optional<Documents> existingOpt = documentRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Documents existing = existingOpt.get();
        // Update mutable fields (do not allow toggling is_deleted here)
        existing.setTitle(update.getTitle());
        existing.setOwner_id(update.getOwner_id());
        existing.setFolder_id(update.getFolder_id());
        existing.setCurrent_version_id(update.getCurrent_version_id());
        existing.setIs_locked(update.isIs_locked());
        if (update.getCreated_at() != null) {
            existing.setCreated_at(update.getCreated_at());
        }
        Documents saved = documentRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        // Soft delete: mark as deleted without removing S3 files
        int updated = documentRepository.softDeleteById(id);
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable("id") UUID id) {
        int updated = documentRepository.restoreById(id);
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
