package com.dms.rest;

import com.dms.dao.UserRepository;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.MultipartUploadInitResponse;
import com.dms.dto.MultipartUploadPartResponse;
import com.dms.dto.MultipartUploadProgressResponse;
import com.dms.models.User;
import com.dms.service.MultipartUploadService;
import com.dms.service.FileUploadValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/multipart-uploads")
public class MultipartUploadController {

    private final MultipartUploadService multipartUploadService;
    private final UserRepository userRepository;
    private final FileUploadValidator fileUploadValidator;

    @Value("${app.upload.max-bytes:104857600}") // 100MB for single upload
    private long maxUploadBytes;

    @Value("${app.upload.max-bytes-multipart:524288000}") // 500MB for multipart
    private long maxMultipartBytes;

    public MultipartUploadController(MultipartUploadService multipartUploadService, 
                                    UserRepository userRepository,
                                    FileUploadValidator fileUploadValidator) {
        this.multipartUploadService = multipartUploadService;
        this.userRepository = userRepository;
        this.fileUploadValidator = fileUploadValidator;
    }

    /**
     * STEP 1: Initiate multipart upload session
     * POST /api/multipart-uploads/initiate
     * 
     * Validates all metadata and file info UPFRONT before creating session
     */
    @PostMapping("/initiate")
    public ResponseEntity<?> initiateUpload(
            @RequestParam("fileName") String fileName,
            @RequestParam("totalSize") Long totalSize,
            @RequestParam("title") String title,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "documentId", required = false) UUID documentId,
            Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(401).body("User not authenticated");
            }

            String email = auth.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // ===== VALIDATE FILENAME =====
            String filenameError = fileUploadValidator.validateFilename(fileName);
            if (filenameError != null) {
                return ResponseEntity.badRequest().body(filenameError);
            }

            // ===== VALIDATE FILE SIZE (Multipart: 500MB max) =====
            if (totalSize == null || totalSize <= 0) {
                return ResponseEntity.badRequest().body("Total file size must be greater than 0");
            }
            String sizeError = fileUploadValidator.validateFileSize(totalSize, maxMultipartBytes);
            if (sizeError != null) {
                return ResponseEntity.badRequest().body(sizeError);
            }

            // ===== VALIDATE TITLE =====
            String titleError = fileUploadValidator.validateTitle(title);
            if (titleError != null) {
                return ResponseEntity.badRequest().body(titleError);
            }

            // ===== VALIDATE DESCRIPTION =====
            String descError = fileUploadValidator.validateDescription(description);
            if (descError != null) {
                return ResponseEntity.badRequest().body(descError);
            }

            // ===== VALIDATE TAGS =====
            String tagsError = fileUploadValidator.validateTags(tags);
            if (tagsError != null) {
                return ResponseEntity.badRequest().body(tagsError);
            }

            // ===== VALIDATE CATEGORY =====
            String categoryError = fileUploadValidator.validateCategory(category);
            if (categoryError != null) {
                return ResponseEntity.badRequest().body(categoryError);
            }

            UUID docId = documentId != null ? documentId : UUID.randomUUID();

            MultipartUploadInitResponse response = multipartUploadService
                    .initiateMultipartUpload(docId, fileName, totalSize, user.getUserId(), 
                            title, folderId, category, tags, description);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to initiate upload: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * STEP 2: Upload single part/chunk
     * POST /api/multipart-uploads/{sessionId}/parts/{partNumber}
     */
    @PostMapping("/{sessionId}/parts/{partNumber}")
    public ResponseEntity<?> uploadPart(
            @PathVariable UUID sessionId,
            @PathVariable Integer partNumber,
            @RequestParam("chunk") MultipartFile chunk,
            Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.badRequest().body("User not authenticated");
            }

            MultipartUploadPartResponse response = multipartUploadService
                    .uploadPart(sessionId, partNumber, chunk.getBytes());

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to upload part: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            return ResponseEntity.internalServerError()
                    .body("Checksum calculation failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * STEP 3: Complete multipart upload (create Document/Version records)
     * POST /api/multipart-uploads/{sessionId}/complete
     */
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<?> completeUpload(
            @PathVariable UUID sessionId,
            @RequestParam("title") String title,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "description", required = false) String description,
            Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.badRequest().body("User not authenticated");
            }

            String email = auth.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            DocumentUploadResponse response = multipartUploadService
                    .completeMultipartUpload(sessionId, title, user.getUserId(), 
                            folderId, category, tags, description);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to complete upload: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            return ResponseEntity.internalServerError()
                    .body("Checksum calculation failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * STEP 4: Get upload progress with S3 verification
     * GET /api/multipart-uploads/{sessionId}/progress
     * 
     * Returns actual bytes from S3 (source of truth)
     * Falls back to database if S3 verification fails
     */
    @GetMapping("/{sessionId}/progress")
    public ResponseEntity<?> getProgress(
            @PathVariable UUID sessionId,
            Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.badRequest().body("User not authenticated");
            }

            // Uses S3 verification internally
            MultipartUploadProgressResponse response = multipartUploadService.getProgress(sessionId);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to get progress: " + e.getMessage());
        }
    }

    /**
     * STEP 5: Abort/Cancel upload
     * POST /api/multipart-uploads/{sessionId}/abort
     */
    @PostMapping("/{sessionId}/abort")
    public ResponseEntity<?> abortUpload(
            @PathVariable UUID sessionId,
            Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.badRequest().body("User not authenticated");
            }

            multipartUploadService.abortMultipartUpload(sessionId);
            return ResponseEntity.ok("Upload aborted successfully");
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to abort upload: " + e.getMessage());
        }
    }
}
