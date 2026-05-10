package com.dms.rest;

import com.dms.dao.DocumentRepository;
import com.dms.dao.UserRepository;
import com.dms.dto.DocumentResponse;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.UploadDocumentRequest;
import com.dms.models.Documents;
import com.dms.models.User;
import com.dms.service.DocumentUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
    private final UserRepository userRepository;

    public DocumentController(DocumentRepository documentRepository, DocumentUploadService documentUploadService, UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.documentUploadService = documentUploadService;
        this.userRepository = userRepository;
    }

    @GetMapping
public List<DocumentResponse> getAll(Authentication auth) {

    if (auth == null || !auth.isAuthenticated()) {
        throw new RuntimeException("User not authenticated");
    }

    return documentRepository.findAllActiveByOwner(
                    com.dms.security.SecurityUtils.currentUserId()
            )
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
}

//Convert Documents entity to DocumentResponse with owner name

private DocumentResponse convertToDTO(Documents doc) {

    String ownerName = "Unknown";

    if (doc.getOwner_id() != null) {

        if (doc.getOwner_id().toString()
                .equals("00000000-0000-0000-0000-000000000000")) {

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
    public ResponseEntity<DocumentResponse> getById(@PathVariable("id") UUID id) {
        return documentRepository.findActiveByIdAndOwner(id, com.dms.security.SecurityUtils.currentUserId())
                .map(doc -> ResponseEntity.ok(convertToDTO(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/trash")
public List<DocumentResponse> getDeleted(Authentication auth) {

    if (auth == null || !auth.isAuthenticated()) {
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "User not authenticated"
        );
    }

    return documentRepository.findAllDeletedByOwner(
                    com.dms.security.SecurityUtils.currentUserId()
            )
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
}

    @PostMapping
    public ResponseEntity<DocumentResponse> create(@RequestBody Documents doc) {
        if (doc.getDocument_id() == null) {
            doc.setDocument_id(UUID.randomUUID());
        }
        if (doc.getCreated_at() == null) {
            doc.setCreated_at(LocalDateTime.now());
        }
        // Ensure new documents are not created as deleted inadvertently
        doc.setIs_deleted(false);
        // Force owner to current user regardless of payload
        doc.setOwner_id(com.dms.security.SecurityUtils.currentUserId());
        Documents saved = documentRepository.save(doc);
        return ResponseEntity.created(URI.create("/api/documents/" + saved.getDocument_id())).body(convertToDTO(saved));
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "description", required = false) String description,
            Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                DocumentUploadResponse errorResponse = new DocumentUploadResponse(
                        null, null, null, file.getOriginalFilename(), "User not authenticated", false
                );
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            String email = auth.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            UploadDocumentRequest request = new UploadDocumentRequest(title, folderId, category, tags, description);
            DocumentUploadResponse response = documentUploadService.uploadDocument(file, request, user.getUserId());
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
    public ResponseEntity<DocumentResponse> update(@PathVariable("id") UUID id, @RequestBody Documents update) {
        UUID userId = com.dms.security.SecurityUtils.currentUserId();
        Optional<Documents> existingOpt = documentRepository.findActiveByIdAndOwner(id, userId);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Documents existing = existingOpt.get();
        // Update mutable fields (do not allow toggling is_deleted here and do not allow changing owner)
        existing.setTitle(update.getTitle());
        existing.setFolder_id(update.getFolder_id());
        existing.setCurrent_version_id(update.getCurrent_version_id());
        existing.setIs_locked(update.isIs_locked());
        if (update.getCreated_at() != null) {
            existing.setCreated_at(update.getCreated_at());
        }
        Documents saved = documentRepository.save(existing);
        return ResponseEntity.ok(convertToDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        // Soft delete: mark as deleted without removing S3 files, only if owned by current user
        int updated = documentRepository.softDeleteByIdAndOwner(id, com.dms.security.SecurityUtils.currentUserId());
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable("id") UUID id) {
        int updated = documentRepository.restoreByIdAndOwner(id, com.dms.security.SecurityUtils.currentUserId());
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
