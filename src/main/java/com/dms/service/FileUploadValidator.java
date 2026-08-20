package com.dms.service;

import com.dms.dao.DocumentRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shared validation logic for both single and multipart file uploads
 * 
 * File Size Limits:
 * - Single Upload: 100MB (configured via app.upload.max-bytes)
 * - Multipart Upload: 500MB (configured via app.upload.max-bytes-multipart)
 * - Part Size: 10MB per chunk (for multipart uploads)
 */
@Component
public class FileUploadValidator {

    /**
     * What this system can actually store, process and preview.
     *
     * The Allowed File Types setting chooses from within this list; it cannot
     * add to it, because permitting a format nothing downstream can read would
     * only move the failure from upload to preview.
     */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "docx", "xlsx", "png", "jpg", "jpeg"
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/png",
            "image/jpeg"
    );

    /** Name shape only - which extensions are permitted is decided separately. */
    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^[A-Za-z0-9_-]+\\.[A-Za-z0-9]+$"
    );

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "invoice", "contract", "report", "proposal", "other"
    );

    private static final Pattern VALID_TAGS = Pattern.compile(
            "^[A-Za-z0-9_-]+(,[A-Za-z0-9_-]+)*$"
    );

    private final DocumentRepository documentRepository;
    private final SettingsService settingsService;

    public FileUploadValidator(DocumentRepository documentRepository,
                               SettingsService settingsService) {
        this.documentRepository = documentRepository;
        this.settingsService = settingsService;
    }

    /**
     * The extensions currently accepted, from the Allowed File Types setting.
     *
     * Read at upload time, which is the only place it is needed - no listing or
     * page load consults it. Anything the setting names that this system cannot
     * process is ignored, and an empty or unreadable setting falls back to
     * everything supported rather than blocking uploads outright.
     */
    private Set<String> allowedExtensions() {
        try {
            Object configured = settingsService.organisationSettings().get("allowedFileTypes");
            if (configured != null && !String.valueOf(configured).isBlank()) {
                Set<String> chosen = Arrays.stream(String.valueOf(configured).split(","))
                        .map(v -> v.trim().toLowerCase())
                        .filter(v -> !v.isEmpty())
                        .filter(SUPPORTED_EXTENSIONS::contains)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (!chosen.isEmpty()) {
                    return chosen;
                }
            }
        } catch (Exception e) {
            // A settings problem must never stop people uploading.
        }
        return SUPPORTED_EXTENSIONS;
    }

    /**
     * Validate filename - extension, pattern, and safety
     * @return null if valid, or error message if invalid
     */
    public String validateFilename(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Filename cannot be empty";
        }

        String original = sanitizeOriginalFilename(fileName);
        if (original == null || !SAFE_FILENAME.matcher(original).matches()) {
            return "Invalid file name. Use letters, numbers, dashes or underscores, "
                    + "followed by the file extension.";
        }

        Set<String> allowed = allowedExtensions();
        String ext = getFileExtension(original);
        if (ext == null || !allowed.contains(ext.toLowerCase())) {
            // Names the formats actually in force, so the message and the
            // Allowed File Types setting can never disagree.
            return "Unsupported file type. Allowed: "
                    + allowed.stream().sorted().collect(Collectors.joining(", ")) + ".";
        }

        return null; // Valid
    }

    /**
     * Validate file size
     * @return null if valid, or error message if invalid
     */
    public String validateFileSize(long fileSize, long maxUploadBytes) {
        if (fileSize <= 0) {
            return "File size must be greater than 0";
        }
        if (fileSize > maxUploadBytes) {
            return "File exceeds maximum allowed size of " + (maxUploadBytes / (1024 * 1024)) + "MB";
        }
        return null; // Valid
    }

    /**
     * Validate content type
     * @return null if valid, or error message if invalid
     */
    public String validateContentType(String fileName, String contentType) {
        String ext = getFileExtension(fileName);
        
        // If no content type provided, that's OK - just check extension is allowed
        if (contentType == null || contentType.isBlank()) {
            return null;
        }

        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            // If reported content type is not in allowlist, still allow if extension is allowed
            if (ext == null || !SUPPORTED_EXTENSIONS.contains(ext.toLowerCase())) {
                return "Unsupported file type: " + contentType;
            }
        }
        return null; // Valid
    }

    /**
     * Validate title/document name
     * @return null if valid, or error message if invalid
     */
    public String validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Document title is required";
        }
        if (title.length() > 200) {
            return "Title must be at most 200 characters";
        }
        return null; // Valid
    }

    /**
     * Validate description
     * @return null if valid, or error message if invalid
     */
    public String validateDescription(String description) {
        if (description != null && description.length() > 1000) {
            return "Description must be at most 1000 characters";
        }
        return null; // Valid
    }

    /**
     * Validate tags
     * @return null if valid, or error message if invalid
     */
    public String validateTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return null; // Tags are optional
        }
        if (tags.length() > 200) {
            return "Tags must be at most 200 characters";
        }
        if (!VALID_TAGS.matcher(tags).matches()) {
            return "Tags must be comma-separated values using only letters, numbers, dash or underscore";
        }
        return null; // Valid
    }

    /**
     * Validate category
     * @return null if valid, or error message if invalid
     */
    public String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return null; // Category is optional, defaults to "other"
        }
        String normalized = category.trim().toLowerCase();
        if (!ALLOWED_CATEGORIES.contains(normalized)) {
            return "Invalid category. Allowed: invoice, contract, report, proposal, other";
        }
        return null; // Valid
    }

    /**
     * Check if document with same title already exists in folder
     * @return null if valid (no duplicate), or error message if duplicate found
     */
    public String validateNoDuplicate(String title, java.util.UUID folderId) {
        if (documentRepository.existsByTitleInFolder(title, folderId)) {
            return "A document with the same title already exists in this folder";
        }
        return null; // Valid
    }

    /**
     * Sanitize filename by removing path components
     */
    private String sanitizeOriginalFilename(String fileName) {
        if (fileName == null) return null;
        int lastIndex = fileName.lastIndexOf('/');
        if (lastIndex < 0) {
            lastIndex = fileName.lastIndexOf('\\');
        }
        return lastIndex >= 0 ? fileName.substring(lastIndex + 1) : fileName;
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    public static Set<String> getAllowedCategories() {
        return ALLOWED_CATEGORIES;
    }
}
