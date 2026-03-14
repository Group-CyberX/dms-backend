package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dao.DocumentVersionRepository;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.UploadDocumentRequest;
import com.dms.models.Documents;
import com.dms.models.DocumentVersions;
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

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-bytes:104857600}") // 100MB default
    private long maxUploadBytes;

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
            "^[A-Za-z0-9_-]+\\.(pdf|docx|xlsx|png|jpg|jpeg)$",
            Pattern.CASE_INSENSITIVE
    );

    public DocumentUploadService(DocumentRepository documentRepository, DocumentVersionRepository documentVersionRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file, UploadDocumentRequest request) throws IOException {
        if (file == null || file.isEmpty()) {
            return new DocumentUploadResponse(null, null, null, "File is empty", false);
        }

        // Basic metadata validations
        String title = request.getTitle() == null ? null : request.getTitle().trim();
        if (title == null || title.isEmpty()) {
            return new DocumentUploadResponse(null, null, null, "Title is required", false);
        }
        if (title.length() > 200) {
            return new DocumentUploadResponse(null, null, null, "Title must be at most 200 characters", false);
        }
        if (request.getDescription() != null && request.getDescription().length() > 1000) {
            return new DocumentUploadResponse(null, null, null, "Description must be at most 1000 characters", false);
        }
        if (request.getTags() != null) {
            String tags = request.getTags();
            if (tags.length() > 200) {
                return new DocumentUploadResponse(null, null, null, "Tags must be at most 200 characters", false);
            }
            if (!isValidTags(tags)) {
                return new DocumentUploadResponse(null, null, null, "Tags must be comma-separated values using only letters, numbers, dash or underscore", false);
            }
        }

        // File validations
        if (file.getSize() > maxUploadBytes) {
            return new DocumentUploadResponse(null, null, null, "File exceeds maximum allowed size", false);
        }

        // Validate original filename and extension
        String original = sanitizeOriginalFilename(file.getOriginalFilename());
        if (original == null || !SAFE_FILENAME.matcher(original).matches()) {
            return new DocumentUploadResponse(null, null, null, "Invalid file name. Use only letters, numbers, dash, underscore and a supported extension (pdf, docx, xlsx, png, jpg, jpeg)", false);
        }
        String ext = getFileExtension(original);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            return new DocumentUploadResponse(null, null, null, "Unsupported file extension: ." + ext, false);
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            // If reported content type is not in allowlist, still allow if extension is allowed and content type is generic
            if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
                return new DocumentUploadResponse(null, null, null, "Unsupported file type: " + contentType, false);
            }
        }

        // Duplicate check by title in same folder
        if (documentRepository.existsByTitleInFolder(title, request.getFolderId())) {
            return new DocumentUploadResponse(null, null, null, "A document with the same title already exists in this folder", false);
        }

        // Generate IDs
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        String savedFileName = null;
        try {
            // Calculate checksum
            String checksum = calculateChecksum(file.getBytes());  

            // Save file locally (you can replace this with S3 upload)
            savedFileName = saveFileWithProvidedName(file, documentId, versionId, original);

            // Create Document record
            Documents document = new Documents();
            document.setDocument_id(documentId);
            document.setTitle(title);
            document.setOwner_id(UUID.fromString("00000000-0000-0000-0000-000000000000")); // TODO: Get from current user
            document.setFolder_id(request.getFolderId());
            document.setCurrent_version_id(versionId);
            document.setCreated_at(LocalDateTime.now());
            document.setIs_locked(false);
            document.setIs_deleted(false);

            documentRepository.save(document);

            // Create DocumentVersion record
            DocumentVersions version = new DocumentVersions();
            version.setVersion_id(versionId);
            version.setDocument_id(documentId);
            version.setVersion_number("1.0");
            version.setS3_bucket_key(savedFileName);
            version.setChecksum(checksum);
            version.setOcr_content(""); // OCR content can be populated later
            version.setCreated_at(LocalDateTime.now());

            documentVersionRepository.save(version);

            return new DocumentUploadResponse(
                    documentId,
                    versionId,
                    title,
                    "Document uploaded successfully",
                    true
            );
        } catch (NoSuchAlgorithmException e) {
            // Cleanup saved file if checksum/file processing failed after save
            if (savedFileName != null) {
                try { Files.deleteIfExists(Paths.get(resolveUploadDir()).resolve(savedFileName)); } catch (IOException ignore) {}
            }
            return new DocumentUploadResponse(null, null, null, "Error calculating file checksum: " + e.getMessage(), false);
        } catch (RuntimeException e) {
            // On any unchecked exception, attempt to remove file to keep FS consistent with rolled-back DB
            if (savedFileName != null) {
                try { Files.deleteIfExists(Paths.get(resolveUploadDir()).resolve(savedFileName)); } catch (IOException ignore) {}
            }
            throw e;
        }
    }

    /**
     * Save file to local storage or S3
     * TODO: Integrate with actual S3 storage
     */
    private String saveFile(MultipartFile file, UUID documentId, UUID versionId) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(resolveUploadDir());
        Files.createDirectories(uploadPath);

        // Generate file name
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String fileName = documentId + "_" + versionId + "_" + original;
        Path filePath = uploadPath.resolve(fileName);

        // Save file
        Files.write(filePath, file.getBytes());

        // Return the S3 bucket key (or file path for local storage)
        return fileName;
    }

    private String resolveUploadDir() {
        // Normalize to ensure no trailing separators issues
        String dir = uploadDir;
        if (dir == null || dir.isBlank()) {
            dir = "uploads";
        }
        return dir;
    }

    // Validate tags: comma-separated tokens with [A-Za-z0-9_-]+
    private boolean isValidTags(String tags) {
        String[] parts = tags.split(",");
        for (String raw : parts) {
            String t = raw.trim();
            if (t.isEmpty()) return false;
            if (!t.matches("[A-Za-z0-9_-]+")) return false;
        }
        return true;
    }

    // Ensure we only keep the base filename (no path parts). Do not mutate characters here.
    private String sanitizeOriginalFilename(String original) {
        if (original == null) return null;
        String base = Paths.get(original).getFileName().toString();
        // Disallow any remaining path separators just in case
        if (base.contains("/") || base.contains("\\\\")) {
            return null;
        }
        return base;
    }

    private String getFileExtension(String original) {
        if (original == null) return null;
        int idx = original.lastIndexOf('.');
        if (idx < 0 || idx == original.length() - 1) return null;
        return original.substring(idx + 1);
    }

    private String saveFileWithProvidedName(MultipartFile file, UUID documentId, UUID versionId, String original) throws IOException {
        Path uploadPath = Paths.get(resolveUploadDir());
        Files.createDirectories(uploadPath);
        String fileName = documentId + "_" + versionId + "_" + original;
        Path filePath = uploadPath.resolve(fileName);
        Files.write(filePath, file.getBytes());
        return fileName;
    }

    /**
     * Calculate SHA-256 checksum of file
     */
    private String calculateChecksum(byte[] fileBytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(fileBytes);
        StringBuilder hexString = new StringBuilder();

        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }
}
