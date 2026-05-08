package com.dms.service;

import com.dms.dao.*;
import com.dms.dto.DocumentUploadResponse;
import com.dms.dto.MultipartUploadInitResponse;
import com.dms.dto.MultipartUploadPartResponse;
import com.dms.dto.MultipartUploadProgressResponse;
import com.dms.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MultipartUploadService {

    private final MultipartUploadSessionRepository sessionRepository;
    private final UploadedPartRepository partRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final FolderRepository folderRepository;
    private final TagService tagService;
    private final software.amazon.awssdk.services.s3.S3Client s3Client;

    @Value("${app.s3.bucket:}")
    private String bucket;

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final long PART_SIZE = 10 * 1024 * 1024; // 10MB

    public MultipartUploadService(MultipartUploadSessionRepository sessionRepository,
                                 UploadedPartRepository partRepository,
                                 DocumentRepository documentRepository,
                                 DocumentVersionRepository versionRepository,
                                 FolderRepository folderRepository,
                                 TagService tagService,
                                 software.amazon.awssdk.services.s3.S3Client s3Client) {
        this.sessionRepository = sessionRepository;
        this.partRepository = partRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.folderRepository = folderRepository;
        this.tagService = tagService;
        this.s3Client = s3Client;
    }

    /**
     * STEP 1: Initiate multipart upload
     * Creates session and starts S3 multipart upload
     */
    @Transactional
    public MultipartUploadInitResponse initiateMultipartUpload(UUID documentId, String fileName, 
                                                               Long totalSize, UUID userId) throws IOException {
        UUID sessionId = UUID.randomUUID();
        
        String s3UploadId = null;
        if ("s3".equalsIgnoreCase(storageType)) {
            // Initiate S3 multipart upload
            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(sessionId.toString())
                    .contentType("application/octet-stream")
                    .build();

            CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
            s3UploadId = createResponse.uploadId();
        }

        // Create session record
        MultipartUploadSession session = new MultipartUploadSession(
                sessionId,
                documentId,
                userId,
                fileName,
                totalSize,
                s3UploadId
        );
        sessionRepository.save(session);

        return new MultipartUploadInitResponse(sessionId, s3UploadId, PART_SIZE);
    }

    /**
     * STEP 2: Upload individual part
     * Stream chunk directly without loading entire file
     */
    @Transactional
    public MultipartUploadPartResponse uploadPart(UUID sessionId, Integer partNumber, 
                                                   byte[] partData) throws IOException, NoSuchAlgorithmException {
        MultipartUploadSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        String eTag = null;
        if ("s3".equalsIgnoreCase(storageType)) {
            // Upload part to S3
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucket)
                    .key(sessionId.toString())
                    .uploadId(session.getS3UploadId())
                    .partNumber(partNumber)
                    .build();

            UploadPartResponse uploadPartResponse = s3Client.uploadPart(
                    uploadPartRequest,
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(partData)
            );
            eTag = uploadPartResponse.eTag();
        } else {
            // Local storage
            eTag = calculateLocalETag(partData);
            saveLocalPart(sessionId, partNumber, partData);
        }

        // Save part metadata
        UploadedPart part = new UploadedPart(
                UUID.randomUUID(),
                session,
                partNumber,
                eTag,
                (long) partData.length
        );
        partRepository.save(part);

        // Update session progress
        Long newUploaded = session.getUploadedBytes() + partData.length;
        session.setUploadedBytes(newUploaded);
        session.setLastActivity(LocalDateTime.now());
        sessionRepository.save(session);

        return new MultipartUploadPartResponse(partNumber, eTag, newUploaded, session.getTotalSize());
    }

    /**
     * STEP 3: Complete multipart upload
     * Finalize S3 upload and create Document/DocumentVersion records
     */
    @Transactional
    public DocumentUploadResponse completeMultipartUpload(UUID sessionId, String title, 
                                                          UUID userId, UUID folderId, 
                                                          String category, String tags, 
                                                          String description) throws IOException, NoSuchAlgorithmException {
        MultipartUploadSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getStatus().equals(MultipartUploadSession.UploadStatus.IN_PROGRESS)) {
            throw new IllegalArgumentException("Session is not in progress");
        }

        List<UploadedPart> uploadedParts = partRepository.findBySessionSessionId(sessionId);
        if (uploadedParts.isEmpty()) {
            throw new IllegalArgumentException("No parts uploaded");
        }

        // Build S3 bucket key
        String original = session.getFileName();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String bucketKey = buildStorageKey(documentId, versionId, original, category);

        String s3Key = null;
        if ("s3".equalsIgnoreCase(storageType)) {
            // Complete multipart upload on S3
            List<CompletedPart> completedParts = uploadedParts.stream()
                    .sorted(Comparator.comparingInt(UploadedPart::getPartNumber))
                    .map(part -> CompletedPart.builder()
                            .partNumber(part.getPartNumber())
                            .eTag(part.getETag())
                            .build())
                    .collect(Collectors.toList());

            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(sessionId.toString())
                    .uploadId(session.getS3UploadId())
                    .multipartUpload(CompletedMultipartUpload.builder()
                            .parts(completedParts)
                            .build())
                    .build();

            s3Client.completeMultipartUpload(completeRequest);
            
            // Rename in S3 from sessionId to final key
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(sessionId.toString())
                    .destinationBucket(bucket)
                    .destinationKey(bucketKey)
                    .build());
            
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(sessionId.toString())
                    .build());
            
            s3Key = bucketKey;
        } else {
            // Local storage: consolidate parts
            s3Key = consolidateLocalParts(sessionId, bucketKey);
        }

        // Get/Create folder
        UUID effectiveFolderId = folderId != null ? folderId : getOrCreateFolder(category);

        // Calculate final checksum from all parts
        String checksum = calculateFinalChecksum(sessionId);

        // Create Document
        Documents document = new Documents();
        document.setDocument_id(documentId);
        document.setTitle(title);
        document.setOwner_id(userId);
        document.setFolder_id(effectiveFolderId);
        document.setCurrent_version_id(versionId);
        document.setCreated_at(LocalDateTime.now());
        document.setFile_size(session.getTotalSize());
        document.setIs_locked(false);
        document.setIs_deleted(false);
        documentRepository.save(document);

        // Create DocumentVersion
        DocumentVersions version = new DocumentVersions();
        version.setVersion_id(versionId);
        version.setDocument_id(documentId);
        version.setVersion_number("1.0");
        version.setS3_bucket_key(s3Key);
        version.setChecksum(checksum);
        version.setOcr_content("");
        version.setCreated_at(LocalDateTime.now());
        versionRepository.save(version);

        // Save tags
        if (tags != null && !tags.isBlank()) {
            tagService.saveTags(documentId, tags);
        }

        // Mark session complete
        session.setStatus(MultipartUploadSession.UploadStatus.COMPLETED);
        session.setS3BucketKey(s3Key);
        sessionRepository.save(session);

        return new DocumentUploadResponse(
                documentId,
                versionId,
                title,
                session.getFileName(),
                "Multipart upload completed successfully",
                true
        );
    }

    /**
     * STEP 4: Abort/Cancel multipart upload
     * Cleanup S3 and database on failure
     */
    @Transactional
    public void abortMultipartUpload(UUID sessionId) throws IOException {
        MultipartUploadSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if ("s3".equalsIgnoreCase(storageType) && session.getS3UploadId() != null) {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(sessionId.toString())
                    .uploadId(session.getS3UploadId())
                    .build();
            
            try {
                s3Client.abortMultipartUpload(abortRequest);
            } catch (Exception e) {
                System.err.println("Failed to abort S3 multipart: " + e.getMessage());
            }
        } else {
            // Clean up local parts
            cleanupLocalParts(sessionId);
        }

        // Mark as aborted
        session.setStatus(MultipartUploadSession.UploadStatus.ABORTED);
        sessionRepository.save(session);
    }

    /**
     * Get upload progress
     */
    public MultipartUploadProgressResponse getProgress(UUID sessionId) throws IOException {
        MultipartUploadSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        long uploadedBytes = session.getUploadedBytes();
        long totalBytes = session.getTotalSize();
        double percentComplete = totalBytes > 0 ? (uploadedBytes * 100.0) / totalBytes : 0.0;

        return new MultipartUploadProgressResponse(
                sessionId,
                uploadedBytes,
                totalBytes,
                percentComplete,
                session.getStatus().toString()
        );
    }

    // ============= Helper Methods =============

    private String buildStorageKey(UUID documentId, UUID versionId, String original, String category) {
        if (category == null || category.isBlank()) {
            return documentId + "/" + versionId + "/" + original;
        }
        return category + "/" + documentId + "/" + versionId + "/" + original;
    }

    private UUID getOrCreateFolder(String category) {
        if (category == null || category.isBlank()) {
            category = "other";
        }
        String finalCategory = category;
        Folders folder = folderRepository.findByNameIgnoreCase(category)
                .orElseGet(() -> {
                    Folders f = new Folders();
                    f.setFolder_id(UUID.randomUUID());
                    f.setName(finalCategory);
                    f.setParent_folder_id(null);
                    f.setPath(finalCategory);
                    return folderRepository.save(f);
                });
        return folder.getFolder_id();
    }

    private String calculateLocalETag(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(data);
        return bytesToHex(hash);
    }

    private String calculateFinalChecksum(UUID sessionId) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<UploadedPart> parts = partRepository.findBySessionSessionId(sessionId);
        
        for (UploadedPart part : parts.stream()
                .sorted(Comparator.comparingInt(UploadedPart::getPartNumber))
                .collect(Collectors.toList())) {
            // For S3, we'd need to download part, for local we read from disk
            if (!"s3".equalsIgnoreCase(storageType)) {
                Path partPath = getLocalPartPath(sessionId, part.getPartNumber());
                if (Files.exists(partPath)) {
                    byte[] partData = Files.readAllBytes(partPath);
                    digest.update(partData);
                }
            }
        }
        
        return bytesToHex(digest.digest());
    }

    private void saveLocalPart(UUID sessionId, Integer partNumber, byte[] data) throws IOException {
        Path partDir = Paths.get(resolveUploadDir()).resolve(sessionId.toString());
        Files.createDirectories(partDir);
        
        Path partFile = partDir.resolve(String.format("%03d.part", partNumber));
        Files.write(partFile, data);
    }

    private Path getLocalPartPath(UUID sessionId, Integer partNumber) {
        return Paths.get(resolveUploadDir()).resolve(sessionId.toString())
                .resolve(String.format("%03d.part", partNumber));
    }

    private void cleanupLocalParts(UUID sessionId) throws IOException {
        Path sessionDir = Paths.get(resolveUploadDir()).resolve(sessionId.toString());
        if (Files.exists(sessionDir)) {
            Files.walk(sessionDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Failed to delete: " + path);
                        }
                    });
        }
    }

    private String consolidateLocalParts(UUID sessionId, String bucketKey) throws IOException {
        Path uploadPath = Paths.get(resolveUploadDir()).resolve(sessionId.toString());
        Path outputDir = Paths.get(resolveUploadDir()).resolve(bucketKey).getParent();
        Files.createDirectories(outputDir);
        
        Path outputPath = outputDir.resolve(bucketKey.substring(bucketKey.lastIndexOf("/") + 1));
        Files.createFile(outputPath);

        List<Path> partFiles = Files.list(uploadPath)
                .sorted()
                .collect(Collectors.toList());

        for (Path partFile : partFiles) {
            byte[] partData = Files.readAllBytes(partFile);
            Files.write(outputPath, partData);
        }

        cleanupLocalParts(sessionId);
        return bucketKey;
    }

    private String resolveUploadDir() {
        String dir = uploadDir;
        if (dir == null || dir.isBlank()) {
            dir = "uploads";
        }
        return dir;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
