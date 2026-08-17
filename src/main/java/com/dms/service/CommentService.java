package com.dms.service;

import com.dms.dao.CommentRepository;
import com.dms.dao.DocumentRepository;
import com.dms.dao.ShareAccessLogRepository;
import com.dms.dao.ShareLinkRepository;
import com.dms.dao.UserRepository;
import com.dms.dto.AddCommentRequest;
import com.dms.dto.CommentResponse;
import com.dms.enums.AccessLevel;
import com.dms.models.Comment;
import com.dms.models.Documents;
import com.dms.models.ShareAccessLog;
import com.dms.models.ShareLink;
import com.dms.models.User;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository repository;
    private final ShareAccessLogRepository logRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
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

    /**
     * All live comments on a share link, told from the caller's point of view.
     *
     * The caller's id decides the {@code mine} flag, which is what the share
     * page uses to decide whether to offer Edit and Delete. Author names are
     * looked up in one query rather than per comment, so a busy review thread
     * does not turn into a row of round trips.
     */
    public List<CommentResponse> getComments(String token, UUID currentUserId) {

        List<Comment> comments = repository.findByTokenAndIsDeletedFalse(token);

        Set<UUID> authorIds = comments.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // A user row with no username would make Collectors.toMap throw, so
        // those are dropped and fall back to the generic label on the page.
        Map<UUID, String> names = authorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(authorIds).stream()
                        .filter(u -> u.getUsername() != null)
                        .collect(Collectors.toMap(User::getUserId, User::getUsername, (a, b) -> a));

        return comments.stream()
                .map(c -> CommentResponse.of(
                        c,
                        c.getUserId() == null ? null : names.get(c.getUserId()),
                        currentUserId))
                .collect(Collectors.toList());
    }

    // Edit existing comment (only owner allowed)
    public Comment editComment(UUID commentId, UUID userId, String content) {

        Comment comment = repository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        // Only the owner can edit. Compared from the caller's id, which the
        // controller has already established is non-null - an unowned comment
        // has no owner to match, so it is refused rather than crashing.
        if (!userId.equals(comment.getUserId())) {
            // 403 rather than the catch-all 500, so the page can say why.
            throw new AccessDeniedException("Not allowed to edit this comment");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Comment cannot be empty");
        }

        comment.setContent(content.trim());
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
        if (!userId.equals(comment.getUserId())) {
            throw new AccessDeniedException("Not allowed to delete this comment");
        }

        comment.setDeleted(true);
        comment.setUpdatedAt(LocalDateTime.now());
        comment.setLastEditedBy(userId);

        auditLogService.tryRecord("COMMENT_DELETED", userId, comment.getDocumentId(),
                null, "SUCCESS", "a comment was removed from the review");

        repository.save(comment);
    }
}
