package com.dms.service;

import com.dms.dao.CommentRepository;
import com.dms.dao.DocumentRepository;
import com.dms.dao.ShareAccessLogRepository;
import com.dms.dao.ShareLinkRepository;
import com.dms.dto.AddCommentRequest;
import com.dms.enums.AccessLevel;
import com.dms.models.Comment;
import com.dms.models.Documents;
import com.dms.models.ShareAccessLog;
import com.dms.models.ShareLink;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository repository;
    private final ShareAccessLogRepository logRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    // Add comment to shared document
    public Comment addComment(String token, UUID userId, String content) {
        return addComment(token, userId, new AddCommentRequest(content, null, null, null, null));
    }

    /**
     * Adds a comment, optionally anchored to a point on a page so it can be
     * drawn as a pin on the document as well as listed in the drawer.
     */
    public Comment addComment(String token, UUID userId, AddCommentRequest request) {

        // Validate share link
        ShareLink link = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Invalid share link"));

        // The owner's "Allow comments" choice decides this, on its own. An EDIT
        // link always allows it, because annotating is the point of one.
        //
        // There used to be a second, hidden rule that refused any VIEW-level
        // link even when comments had been explicitly enabled on it. The two
        // rules disagreed, and the share page had no way to explain why the
        // comment box did nothing.
        boolean commentingAllowed = link.isAllowComments() || link.getAccessLevel() == AccessLevel.EDIT;
        if (!commentingAllowed) {
            throw new RuntimeException("Comments are not enabled on this link");
        }

        // Check authentication requirement (Always required)
        if (userId == null) {
            throw new RuntimeException("Login required to comment");
        }

        if (request.content() == null || request.content().isBlank()) {
            throw new RuntimeException("Comment cannot be empty");
        }

        Comment comment = new Comment();
        comment.setToken(token);
        comment.setUserId(userId);
        comment.setContent(request.content().trim());
        comment.setDocumentId(link.getDocumentId());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setPageNumber(request.pageNumber());
        comment.setAnchorX(request.anchorX());
        comment.setAnchorY(request.anchorY());
        comment.setParentCommentId(request.parentCommentId());

        // Typewriter text has to land somewhere on a page; without an anchor
        // there is nowhere to draw it, so it degrades to an ordinary comment.
        boolean wantsTypewriter = Comment.TYPE_TEXT.equalsIgnoreCase(
                request.annotationType() == null ? "" : request.annotationType().trim());
        comment.setAnnotationType(wantsTypewriter && comment.isAnchored()
                ? Comment.TYPE_TEXT
                : Comment.TYPE_COMMENT);

        // Update log
        ShareAccessLog log = new ShareAccessLog();
        log.setToken(token);
        log.setUserId(userId);
        log.setCommented(true);
        log.setAccessedAt(LocalDateTime.now());

        logRepository.save(log);

        Comment saved = repository.save(comment);

        auditLogService.tryRecord("COMMENT_ADDED", userId, link.getDocumentId(), null, "SUCCESS");

        // Requirements 8.4 step 4: the document owner is notified when a
        // collaborator comments. Skipped when the owner is the commenter.
        Documents doc = documentRepository.findById(link.getDocumentId()).orElse(null);
        if (doc != null && doc.getOwner_id() != null && !doc.getOwner_id().equals(userId)) {
            notificationService.sendNotification(doc.getOwner_id(),
                    "Someone commented on '" + doc.getTitle() + "'");
        }

        return saved;
    }

    // Get all comments for a share link
    public List<Comment> getComments(String token) {
        return repository.findByTokenAndIsDeletedFalse(token);
    }

    // Edit existing comment (only owner allowed)
    public Comment editComment(UUID commentId, UUID userId, String content) {

        Comment comment = repository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        // only owner can edit
        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("Not allowed to edit this comment");
        }

        comment.setContent(content);
        comment.setUpdatedAt(LocalDateTime.now());
        comment.setLastEditedBy(userId);

        // Changing what a review said after the fact must leave a trace, or the
        // discussion recorded in a saved version cannot be trusted.
        auditLogService.tryRecord("COMMENT_EDITED", userId, comment.getDocumentId(),
                null, "SUCCESS", "a comment was edited");

        return repository.save(comment);
    }

    // Soft delete comment (only owner can delete)
    public void deleteComment(UUID commentId, UUID userId) {

        Comment comment = repository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        // only owner can delete
        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("Not allowed to delete this comment");
        }

        comment.setDeleted(true);
        comment.setUpdatedAt(LocalDateTime.now());
        comment.setLastEditedBy(userId);

        auditLogService.tryRecord("COMMENT_DELETED", userId, comment.getDocumentId(),
                null, "SUCCESS", "a comment was removed from the review");

        repository.save(comment);
    }
}
