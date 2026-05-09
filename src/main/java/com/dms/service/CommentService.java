package com.dms.service;

import com.dms.dao.CommentRepository;
import com.dms.dao.ShareAccessLogRepository;
import com.dms.dao.ShareLinkRepository;
import com.dms.models.Comment;
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

    // Add comment to shared document
    public Comment addComment(String token, UUID userId, String content) {

        // Validate share link
        ShareLink link = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Invalid share link"));

        // Check if comments are allowed
        if (!link.isAllowComments()) {
            throw new RuntimeException("Comments are not allowed");
        }

        // Check authentication requirement
        if (link.isRequireAuth() && userId == null) {
            throw new RuntimeException("Login required to comment");
        }

        Comment comment = new Comment();
        comment.setToken(token);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setDocumentId(link.getDocumentId());
        comment.setCreatedAt(LocalDateTime.now());

        // Update log
        ShareAccessLog log = new ShareAccessLog();
        log.setToken(token);
        log.setUserId(userId);
        log.setCommented(true);
        log.setAccessedAt(LocalDateTime.now());

        logRepository.save(log);

        return repository.save(comment);
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

        repository.save(comment);
    }
}
