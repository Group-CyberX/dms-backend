package com.dms.service;

import com.dms.dao.CommentRepository;
import com.dms.dao.DocumentRepository;
import com.dms.dao.ShareLinkRepository;
import com.dms.dao.UserRepository;
import com.dms.dto.SaveAnnotatedVersionResponse;
import com.dms.enums.AccessLevel;
import com.dms.exceptions.ResourceNotFoundException;
import com.dms.models.Comment;
import com.dms.models.DocumentVersions;
import com.dms.models.Documents;
import com.dms.models.ShareLink;
import com.dms.util.InMemoryMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the review discussion on a shared document into a new document version.
 *
 * Reviewers annotate through the share link; when they are done, the comments
 * are written into the PDF and stored as version N+1. The unannotated original
 * stays in the version history, so the review is additive rather than
 * destructive.
 */
@Service
public class ShareAnnotationService {

    private final ShareLinkRepository shareLinkRepository;
    private final CommentRepository commentRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentVersionService documentVersionService;
    private final PdfAnnotationStampingService stampingService;
    private final DocumentLockService documentLockService;

    public ShareAnnotationService(ShareLinkRepository shareLinkRepository,
                                  CommentRepository commentRepository,
                                  DocumentRepository documentRepository,
                                  UserRepository userRepository,
                                  DocumentVersionService documentVersionService,
                                  PdfAnnotationStampingService stampingService,
                                  DocumentLockService documentLockService) {
        this.shareLinkRepository = shareLinkRepository;
        this.commentRepository = commentRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.documentVersionService = documentVersionService;
        this.stampingService = stampingService;
        this.documentLockService = documentLockService;
    }

    @Transactional
    public SaveAnnotatedVersionResponse saveAnnotatedVersion(String token, UUID userId) throws IOException {

        ShareLink link = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid share link"));

        if (!link.isActive()) {
            throw new IllegalStateException("This share link has been revoked");
        }
        if (link.getAccessLevel() != AccessLevel.EDIT) {
            throw new IllegalStateException("This link does not allow saving changes");
        }
        if (userId == null) {
            throw new IllegalStateException("Sign in to save a new version");
        }

        UUID documentId = link.getDocumentId();

        // Someone else editing the document blocks the save, same as internally.
        documentLockService.assertCanMutate(documentId, userId);

        Documents document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        UUID currentVersionId = document.getCurrent_version_id();
        if (currentVersionId == null) {
            throw new IllegalStateException("Document has no stored version to annotate");
        }

        List<Comment> comments = commentRepository.findByTokenAndIsDeletedFalse(token);
        if (comments.isEmpty()) {
            throw new IllegalStateException("Add at least one comment before saving a version");
        }

        byte[] original = documentVersionService.getVersionFileBytes(documentId, currentVersionId);

        // Annotations are written with PDFBox, so the stored file has to be a
        // PDF. Documents can be titled ".pdf" while holding something else
        // entirely, and without this check PDFBox fails deep inside the parser
        // with "Missing root object specification in trailer" - a 500 that tells
        // the reviewer nothing about what went wrong or what to do about it.
        if (!looksLikePdf(original)) {
            throw new IllegalStateException(
                    "Only PDF documents can be annotated. The stored file for this document is not a PDF.");
        }

        byte[] annotated = stampingService.stamp(original, comments, displayNames(comments));

        String filename = buildFilename(document.getTitle());
        DocumentVersions newVersion = documentVersionService.uploadNewVersion(
                documentId,
                new InMemoryMultipartFile("file", filename, "application/pdf", annotated));

        // The review is captured in the file now, so release the hold.
        documentLockService.releaseIfHeldBy(documentId, userId);

        return new SaveAnnotatedVersionResponse(
                documentId,
                newVersion.getVersion_id(),
                newVersion.getVersion_number(),
                comments.size());
    }

    /** Every PDF begins with the five bytes %PDF-, whatever it is named. */
    private boolean looksLikePdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5) {
            return false;
        }
        return bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }

    /** Maps author ids to display names for the labels drawn into the PDF. */
    private Map<String, String> displayNames(List<Comment> comments) {
        Map<String, String> names = new HashMap<>();
        for (Comment comment : comments) {
            if (comment.getUserId() == null) continue;
            String key = comment.getUserId().toString();
            if (names.containsKey(key)) continue;
            userRepository.findById(comment.getUserId())
                    .ifPresent(user -> names.put(key, user.getUsername()));
        }
        return names;
    }

    private String buildFilename(String title) {
        String base = (title == null || title.isBlank()) ? "document" : title;
        if (base.toLowerCase().endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        return base + "-reviewed.pdf";
    }
}
