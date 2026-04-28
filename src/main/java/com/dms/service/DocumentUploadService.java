package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dao.DocumentVersionRepository;
import com.dms.dao.FolderRepository;
import com.dms.dao.DocumentMetadataRepository;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.UploadDocumentRequest;
import com.dms.models.Documents;
import com.dms.models.DocumentMetadata;
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
    private final DocumentMetadataRepository documentMetadataRepository;
    private final MetadataExtractorService metadataExtractorService;
    private final TagService tagService;
    private final ProcessingJobService processingJobService; // Added job service

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-bytes:104857600}") // 100MB default
    private long maxUploadBytes;

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.s3.bucket:}")
    private String bucket;

    private final software.amazon.awssdk.services.s3.S3Client s3Client;

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
            "^.+\\.(pdf|docx|xlsx|png|jpg|jpeg)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "invoice", "contract", "report", "proposal", "other"
    );

    public DocumentUploadService(DocumentRepository documentRepository, DocumentVersionRepository documentVersionRepository, FolderRepository folderRepository, software.amazon.awssdk.services.s3.S3Client s3Client, TagService tagService, DocumentMetadataRepository documentMetadataRepository, MetadataExtractorService metadataExtractorService, ProcessingJobService processingJobService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.folderRepository = folderRepository;
        this.s3Client = s3Client;
        this.tagService = tagService;
        this.documentMetadataRepository = documentMetadataRepository;
        this.metadataExtractorService = metadataExtractorService;
        this.processingJobService = processingJobService;
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file, UploadDocumentRequest request) throws IOException {
        String fileName = file != null ? file.getOriginalFilename() : "unknown";
        
        if (file == null || file.isEmpty()) {
            return new DocumentUploadResponse(null, null, null, fileName, "File is empty", false);
        }

        // Basic metadata validations
        String title = request.getTitle() == null ? null : request.getTitle().trim();
        if (title == null || title.isEmpty()) {
            return new DocumentUploadResponse(null, null, null, fileName, "Title is required", false);
        }
        if (title.length() > 200) {
            return new DocumentUploadResponse(null, null, null, fileName, "Title must be at most 200 characters", false);
        }
        if (request.getDescription() != null && request.getDescription().length() > 1000) {
            return new DocumentUploadResponse(null, null, null, fileName, "Description must be at most 1000 characters", false);
        }
        if (request.getTags() != null) {
            String tags = request.getTags();
            if (tags.length() > 200) {
                return new DocumentUploadResponse(null, null, null, fileName, "Tags must be at most 200 characters", false);
            }
            if (!isValidTags(tags)) {
                return new DocumentUploadResponse(null, null, null, fileName, "Tags must be comma-separated values using only letters, numbers, dash or underscore", false);
            }
        }

        // File validations
        if (file.getSize() > maxUploadBytes) {
            return new DocumentUploadResponse(null, null, null, fileName, "File exceeds maximum allowed size", false);
        }

        // Validate original filename and extension
        String original = sanitizeOriginalFilename(file.getOriginalFilename());
        if (original == null || !SAFE_FILENAME.matcher(original).matches()) {
            return new DocumentUploadResponse(null, null, null, fileName, "Invalid file name. Extension must be pdf, docx, xlsx, png, jpg, jpeg.", false);
        }
        String ext = getFileExtension(original);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            return new DocumentUploadResponse(null, null, null, fileName, "Unsupported file extension: ." + ext, false);
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            // If reported content type is not in allowlist, still allow if extension is allowed and content type is generic
            if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
                return new DocumentUploadResponse(null, null, null, fileName, "Unsupported file type: " + contentType, false);
            }
        }

        // Resolve target folder by folderId or category
        UUID effectiveFolderId = request.getFolderId();
        String category = request.getCategory();
        String categoryNormalized = null;
        if (effectiveFolderId == null) {
            if (category == null || category.isBlank()) {
                categoryNormalized = "other";
            } else {
                categoryNormalized = category.trim().toLowerCase();
            }
            if (!ALLOWED_CATEGORIES.contains(categoryNormalized)) {
                return new DocumentUploadResponse(null, null, null, fileName, "Invalid category. Allowed: invoice, contract, report, proposal, other", false);
            }
            // Find or create folder with this category name
            final String folderName = categoryNormalized;
            Folders folder = folderRepository.findByNameIgnoreCase(folderName).orElseGet(() -> {
                Folders f = new Folders();
                f.setFolder_id(UUID.randomUUID());
                f.setName(folderName);
                f.setParent_folder_id(null);
                f.setPath(folderName);
                return folderRepository.save(f);
            });
            effectiveFolderId = folder.getFolder_id();
        } else {
            // If a specific folder is provided, still consider category for storage path if valid
            if (category != null && !category.isBlank()) {
                String tmp = category.trim().toLowerCase();
                if (ALLOWED_CATEGORIES.contains(tmp)) {
                    categoryNormalized = tmp;
                }
            }
        }

        // Duplicate check by title in the resolved folder
        if (documentRepository.existsByTitleInFolder(title, effectiveFolderId)) {
            return new DocumentUploadResponse(null, null, null, fileName, "A document with the same title already exists in this folder", false);
        }

        // Generate IDs
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        String savedFileName = null;
        boolean storedInS3 = false;
        try {
            // Calculate checksum
            String checksum = calculateChecksum(file.getBytes());  

            // Save to selected storage (S3 or local)
            if ("s3".equalsIgnoreCase(storageType)) {
                savedFileName = buildStorageKey(documentId, versionId, original, categoryNormalized);
                uploadToS3(savedFileName, file);
                storedInS3 = true;
            } else {
                savedFileName = saveFileWithProvidedName(file, documentId, versionId, original, categoryNormalized);
            }

            // Create Document record
            Documents document = new Documents();
            document.setDocument_id(documentId);
            document.setTitle(title);
            document.setOwner_id(UUID.fromString("00000000-0000-0000-0000-000000000000")); // TODO: Get from current user
            document.setFolder_id(effectiveFolderId);
            document.setCurrent_version_id(versionId);
            document.setCreated_at(LocalDateTime.now());
            document.setIs_locked(false);
            document.setIs_deleted(false);

            document = documentRepository.save(document);

            // Save tags
            tagService.saveTags(documentId, request.getTags());

            // Create DocumentVersion record WITHOUT extracted text initially
            DocumentVersions version = new DocumentVersions();
            version.setVersion_id(versionId);
            version.setDocument_id(documentId);
            version.setVersion_number("1.0");
            version.setS3_bucket_key(savedFileName);
            version.setChecksum(checksum);
            version.setOcr_content(null); // Text will be applied async
            version.setCreated_at(LocalDateTime.now());

            documentVersionRepository.saveAndFlush(version);

            // Queue the Processing Job!
            com.dms.models.ProcessingJob job = processingJobService.enqueueJob(versionId, "OCR");
            processingJobService.triggerOcrJobSafely(job.getJobId());

            // Save Automatic Metadata (Content-Type and File Size)
            if (file.getContentType() != null) {
                documentMetadataRepository.save(new DocumentMetadata(document, "Content-Type", file.getContentType()));
            }
            documentMetadataRepository.save(new DocumentMetadata(document, "File-Size", String.valueOf(file.getSize()) + " bytes"));
            
            // Save Document Type (Category) to Metadata exactly matching the UI search key
            if (categoryNormalized != null && !categoryNormalized.isBlank()) {
                documentMetadataRepository.save(new DocumentMetadata(document, "documentType", categoryNormalized));
            }

            // We do a fast digital signature check natively on the file without OCR
            boolean hasSignature = metadataExtractorService.hasDigitalSignature(file);
            String sigStatusValue = hasSignature ? "signed" : "unsigned";
            documentMetadataRepository.save(new DocumentMetadata(document, "signatureStatus", sigStatusValue));

            return new DocumentUploadResponse(
                    documentId,
                    versionId,
                    title,
                    fileName,
                    "Document uploaded successfully",
                    true
            );
        } catch (NoSuchAlgorithmException e) {
            // Cleanup saved file if checksum/file processing failed after save
            if (savedFileName != null) {
                try {
                    if (storedInS3) deleteFromS3(savedFileName); else Files.deleteIfExists(Paths.get(resolveUploadDir()).resolve(savedFileName));
                } catch (IOException ignore) {}
            }
            return new DocumentUploadResponse(null, null, null, fileName, "Error calculating file checksum: " + e.getMessage(), false);
        } catch (RuntimeException e) {
            // Log the stack trace so we can debug database/constraint errors
            e.printStackTrace();
            
            // On any unchecked exception, attempt to remove file to keep FS consistent with rolled-back DB
            if (savedFileName != null) {
                try {
                    if (storedInS3) deleteFromS3(savedFileName); else Files.deleteIfExists(Paths.get(resolveUploadDir()).resolve(savedFileName));
                } catch (IOException ignore) {}
            }
            throw e; 
        }
    }

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

    private String saveFileWithProvidedName(MultipartFile file, UUID documentId, UUID versionId, String original, String category) throws IOException {
        String baseDir = resolveUploadDir();
        Path uploadPath = category == null || category.isBlank() ? Paths.get(baseDir) : Paths.get(baseDir, category);
        Files.createDirectories(uploadPath);
        String fileName = documentId + "_" + versionId + "_" + original;
        Path filePath = uploadPath.resolve(fileName);
        Files.write(filePath, file.getBytes());
        // Return relative path for local storage to reflect category folder
        return (category == null || category.isBlank()) ? fileName : category + "/" + fileName;
    }

    private String buildStorageKey(UUID documentId, UUID versionId, String original) {
        return documentId + "/" + versionId + "/" + original;
    }

    private String buildStorageKey(UUID documentId, UUID versionId, String original, String category) {
        if (category == null || category.isBlank()) {
            return buildStorageKey(documentId, versionId, original);
        }
        return category + "/" + documentId + "/" + versionId + "/" + original;
    }

    private void uploadToS3(String key, MultipartFile file) throws IOException {
        if (bucket == null || bucket.isBlank()) {
            throw new IOException("S3 bucket is not configured");
        }
        software.amazon.awssdk.services.s3.model.PutObjectRequest req =
                software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .build();
        s3Client.putObject(req, software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));
    }

    private void deleteFromS3(String key) throws IOException {
        try {
            if (bucket == null || bucket.isBlank()) return;
            software.amazon.awssdk.services.s3.model.DeleteObjectRequest del =
                    software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build();
            s3Client.deleteObject(del);
        } catch (software.amazon.awssdk.core.exception.SdkException e) {
            throw new IOException("Failed to delete S3 object: " + e.getMessage(), e);
        }
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
