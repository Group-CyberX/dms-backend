package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dao.DocumentVersionRepository;
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
import java.util.*;
import java.util.regex.Pattern;

@Service
public class DocumentVersionService {

    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-bytes:104857600}") // default 100MB
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

    public DocumentVersionService(DocumentVersionRepository documentVersionRepository,
                                  DocumentRepository documentRepository) {
        this.documentVersionRepository = documentVersionRepository;
        this.documentRepository = documentRepository;
    }

    public List<DocumentVersions> listVersions(UUID documentId) {
        ensureDocumentExists(documentId);
        return documentVersionRepository.findByDocument_idOrderByCreated_atDesc(documentId);
    }

    public Optional<DocumentVersions> getVersion(UUID documentId, UUID versionId) {
        Optional<DocumentVersions> v = documentVersionRepository.findById(versionId);
        return v.filter(dv -> dv.getDocument_id().equals(documentId));
    }

    @Transactional
    public DocumentVersions restoreVersion(UUID documentId, UUID versionId) {
        Documents doc = ensureDocumentExists(documentId);
        DocumentVersions version = documentVersionRepository.findById(versionId)
                .filter(v -> v.getDocument_id().equals(documentId))
                .orElseThrow(() -> new IllegalArgumentException("Version not found for document"));
        doc.setCurrent_version_id(version.getVersion_id());
        documentRepository.save(doc);
        return version;
    }

    @Transactional
    public DocumentVersions uploadNewVersion(UUID documentId, MultipartFile file) throws IOException {
        Documents document = ensureDocumentExists(documentId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("File exceeds maximum allowed size");
        }

        String original = sanitizeOriginalFilename(file.getOriginalFilename());
        if (original == null || !SAFE_FILENAME.matcher(original).matches()) {
            throw new IllegalArgumentException("Invalid file name. Use only letters, numbers, dash, underscore and a supported extension (pdf, docx, xlsx, png, jpg, jpeg)");
        }
        String ext = getFileExtension(original);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file extension: ." + ext);
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
                throw new IllegalArgumentException("Unsupported file type: " + contentType);
            }
        }

        String savedFileName = null;
        try {
            String checksum = calculateChecksum(file.getBytes());

            UUID versionId = UUID.randomUUID();
            savedFileName = saveFileWithProvidedName(file, documentId, versionId, original);

            String newVersionNumber = nextVersionNumber(documentId);

            DocumentVersions version = new DocumentVersions();
            version.setVersion_id(versionId);
            version.setDocument_id(documentId);
            version.setVersion_number(newVersionNumber);
            version.setS3_bucket_key(savedFileName);
            version.setChecksum(checksum);
            version.setOcr_content("");
            version.setCreated_at(LocalDateTime.now());

            documentVersionRepository.save(version);

            // update current version pointer
            document.setCurrent_version_id(versionId);
            documentRepository.save(document);

            return version;
        } catch (NoSuchAlgorithmException e) {
            if (savedFileName != null) {
                try { Files.deleteIfExists(Paths.get(resolveUploadDir()).resolve(savedFileName)); } catch (IOException ignore) {}
            }
            throw new IOException("Error calculating file checksum: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (savedFileName != null) {
                try { Files.deleteIfExists(Paths.get(resolveUploadDir()).resolve(savedFileName)); } catch (IOException ignore) {}
            }
            throw e;
        }
    }

    @Transactional
    public void deleteVersion(UUID documentId, UUID versionId) throws IOException {
        Documents doc = ensureDocumentExists(documentId);
        DocumentVersions version = documentVersionRepository.findById(versionId)
                .filter(v -> v.getDocument_id().equals(documentId))
                .orElseThrow(() -> new IllegalArgumentException("Version not found for document"));

        // Count total versions
        List<DocumentVersions> all = documentVersionRepository.findByDocument_idOrderByCreated_atDesc(documentId);
        if (all.size() <= 1) {
            throw new IllegalStateException("Cannot delete the only version of a document");
        }

        // If deleting current, switch to the latest remaining other version
        if (doc.getCurrent_version_id() != null && doc.getCurrent_version_id().equals(versionId)) {
            UUID replacement = null;
            for (DocumentVersions dv : all) {
                if (!dv.getVersion_id().equals(versionId)) { replacement = dv.getVersion_id(); break; }
            }
            if (replacement == null) {
                throw new IllegalStateException("No alternative version available to set as current");
            }
            doc.setCurrent_version_id(replacement);
            documentRepository.save(doc);
        }

        // delete file from storage if present
        if (version.getS3_bucket_key() != null) {
            try { Files.deleteIfExists(Paths.get(resolveUploadDir()).resolve(version.getS3_bucket_key())); } catch (IOException ignore) {}
        }
        documentVersionRepository.deleteById(versionId);
    }

    private Documents ensureDocumentExists(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    private String resolveUploadDir() {
        String dir = uploadDir;
        if (dir == null || dir.isBlank()) dir = "uploads";
        return dir;
    }

    private String sanitizeOriginalFilename(String original) {
        if (original == null) return null;
        String base = Paths.get(original).getFileName().toString();
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

    private String nextVersionNumber(UUID documentId) {
        // Determine next integer sequence based on count or latest version_number
        List<DocumentVersions> versions = documentVersionRepository.findByDocument_idOrderByCreated_atDesc(documentId);
        int next = 1;
        if (!versions.isEmpty()) {
            String latest = versions.get(0).getVersion_number();
            try {
                if (latest != null && latest.contains(".")) {
                    String major = latest.substring(0, latest.indexOf('.'));
                    next = Integer.parseInt(major) + 1;
                } else if (latest != null) {
                    next = Integer.parseInt(latest) + 1;
                } else {
                    next = versions.size() + 1;
                }
            } catch (NumberFormatException e) {
                next = versions.size() + 1;
            }
        }
        return next + ".0";
    }
}
