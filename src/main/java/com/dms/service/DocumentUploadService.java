package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dao.DocumentVersionRepository;
import com.dms.dao.FolderRepository;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.UploadDocumentRequest;
import com.dms.models.Documents;
import com.dms.models.DocumentVersions;
import com.dms.models.Folders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DocumentUploadService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final FolderRepository folderRepository;
    private final TagService tagService;
    private final NotificationService notificationService;
    private final software.amazon.awssdk.services.s3.S3Client s3Client;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-bytes:104857600}")
    private long maxUploadBytes;

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.s3.bucket:}")
    private String bucket;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/png",
            "image/jpeg"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "xlsx", "png", "jpg", "jpeg"
    );

    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^[A-Za-z0-9_\\- .()]+\\.(pdf|docx|xlsx|png|jpg|jpeg)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "invoice", "contract", "report", "proposal", "other"
    );

    public DocumentUploadService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            FolderRepository folderRepository,
            software.amazon.awssdk.services.s3.S3Client s3Client,
            TagService tagService,
            NotificationService notificationService
    ) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.folderRepository = folderRepository;
        this.s3Client = s3Client;
        this.tagService = tagService;
        this.notificationService = notificationService;
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file, UploadDocumentRequest request) throws IOException {

        String fileName = file != null ? file.getOriginalFilename() : "unknown";

        if (file == null || file.isEmpty()) {
            return new DocumentUploadResponse(null, null, null, fileName, "File is empty", false);
        }

        String title = request.getTitle() == null ? null : request.getTitle().trim();
        if (title == null || title.isEmpty()) {
            return new DocumentUploadResponse(null, null, null, fileName, "Title is required", false);
        }

        UUID effectiveFolderId = request.getFolderId();
        String categoryNormalized = null;

        // Handle category → folder auto creation
        if (effectiveFolderId == null) {
            categoryNormalized = (request.getCategory() == null || request.getCategory().isBlank())
                    ? "other"
                    : request.getCategory().trim().toLowerCase();

            if (!ALLOWED_CATEGORIES.contains(categoryNormalized)) {
                return new DocumentUploadResponse(null, null, null, fileName, "Invalid category", false);
            }

            Folders folder = folderRepository.findByNameIgnoreCase(categoryNormalized)
                    .orElseGet(() -> {
                        Folders f = new Folders();
                        f.setFolder_id(UUID.randomUUID());
                        f.setName(categoryNormalized);
                        f.setPath(categoryNormalized);
                        return folderRepository.save(f);
                    });

            effectiveFolderId = folder.getFolder_id();
        }

        // Duplicate check
        if (documentRepository.existsByTitleInFolder(title, effectiveFolderId)) {
            return new DocumentUploadResponse(null, null, null, fileName,
                    "Document already exists in this folder", false);
        }

        // File validations
        if (file.getSize() > maxUploadBytes) {
            return new DocumentUploadResponse(null, null, null, fileName,
                    "File too large", false);
        }

        String original = sanitizeOriginalFilename(file.getOriginalFilename());
        if (original == null || !SAFE_FILENAME.matcher(original).matches()) {
            return new DocumentUploadResponse(null, null, null, fileName,
                    "Invalid file name", false);
        }

        String ext = getFileExtension(original);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            return new DocumentUploadResponse(null, null, null, fileName,
                    "Unsupported file type", false);
        }

        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        String storedKey = null;
        boolean isS3 = "s3".equalsIgnoreCase(storageType);

        try {
            String checksum = calculateChecksum(file.getBytes());

            // STORAGE
            if (isS3) {
                storedKey = buildStorageKey(documentId, versionId, original, categoryNormalized);
                uploadToS3(storedKey, file);
            } else {
                storedKey = saveLocal(file, documentId, versionId, original, categoryNormalized);
            }

            // SAVE DOCUMENT
            Documents doc = new Documents();
            doc.setDocument_id(documentId);
            doc.setTitle(title);
            doc.setOwner_id(UUID.fromString("00000000-0000-0000-0000-000000000000"));
            doc.setFolder_id(effectiveFolderId);
            doc.setCurrent_version_id(versionId);
            doc.setCreated_at(LocalDateTime.now());
            doc.setIs_deleted(false);
            doc.setIs_locked(false);

            documentRepository.save(doc);

            tagService.saveTags(documentId, request.getTags());

            DocumentVersions version = new DocumentVersions();
            version.setVersion_id(versionId);
            version.setDocument_id(documentId);
            version.setVersion_number("1.0");
            version.setS3_bucket_key(storedKey);
            version.setChecksum(checksum);
            version.setCreated_at(LocalDateTime.now());

            documentVersionRepository.save(version);

            // ✅ NOTIFICATION (your feature added)
            notificationService.sendNotification(
                    UUID.fromString("0b0f8543-672e-4a5a-bb8d-99da74f94f90"),
                    "Document uploaded: " + title
            );

            return new DocumentUploadResponse(documentId, versionId, title,
                    fileName, "Upload successful", true);

        } catch (Exception e) {

            // rollback storage
            if (storedKey != null) {
                try {
                    if (isS3) deleteFromS3(storedKey);
                    else Files.deleteIfExists(Paths.get(resolveUploadDir()).resolve(storedKey));
                } catch (Exception ignore) {}
            }

            notificationService.sendNotification(
                    UUID.fromString("0b0f8543-672e-4a5a-bb8d-99da74f94f90"),
                    "Upload failed: " + e.getMessage()
            );

            return new DocumentUploadResponse(null, null, null,
                    fileName, "Upload failed", false);
        }
    }

    // ---------------- HELPERS ----------------

    private String resolveUploadDir() {
        return (uploadDir == null || uploadDir.isBlank()) ? "uploads" : uploadDir;
    }

    private String sanitizeOriginalFilename(String original) {
        if (original == null) return null;
        return Paths.get(original).getFileName().toString();
    }

    private String getFileExtension(String original) {
        if (original == null) return null;
        int idx = original.lastIndexOf('.');
        return (idx < 0) ? null : original.substring(idx + 1);
    }

    private String saveLocal(MultipartFile file, UUID docId, UUID verId, String original, String category) throws IOException {
        Path dir = (category == null) ? Paths.get(resolveUploadDir())
                : Paths.get(resolveUploadDir(), category);

        Files.createDirectories(dir);

        String fileName = docId + "_" + verId + "_" + original;
        Files.write(dir.resolve(fileName), file.getBytes());

        return (category == null) ? fileName : category + "/" + fileName;
    }

    private String buildStorageKey(UUID docId, UUID verId, String original, String category) {
        return (category == null)
                ? docId + "/" + verId + "/" + original
                : category + "/" + docId + "/" + verId + "/" + original;
    }

    private void uploadToS3(String key, MultipartFile file) throws IOException {
        s3Client.putObject(
                software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes())
        );
    }

    private void deleteFromS3(String key) {
        s3Client.deleteObject(builder -> builder.bucket(bucket).key(key));
    }

    private String calculateChecksum(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}