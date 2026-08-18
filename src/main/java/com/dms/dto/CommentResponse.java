package com.dms.dto;

import com.dms.models.Comment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A comment as the share page needs to display it.
 *
 * The entity used to be returned as-is, which left the browser to work out
 * whose comment it was by comparing user ids - something a share-link visitor
 * cannot do, because neither the login response nor the JWT carries their own
 * id. Every comment therefore read as somebody else's, and the Edit and Delete
 * controls never appeared.
 *
 * The ownership rule lives in CommentService; {@code mine} reports the answer
 * that rule would give, so the controls show up exactly where an edit or a
 * delete would be accepted.
 */
public record CommentResponse(
        UUID id,
        UUID documentId,
        UUID userId,
        String content,
        /** Display name of whoever wrote it, or null if the account is gone. */
        String authorName,
        /** True when the caller may edit or delete this comment. */
        boolean mine,
        Integer pageNumber,
        Double anchorX,
        Double anchorY,
        UUID parentCommentId,
        String annotationType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CommentResponse of(Comment comment, String authorName, UUID currentUserId) {
        return new CommentResponse(
                comment.getId(),
                comment.getDocumentId(),
                comment.getUserId(),
                comment.getContent(),
                authorName,
                currentUserId != null && currentUserId.equals(comment.getUserId()),
                comment.getPageNumber(),
                comment.getAnchorX(),
                comment.getAnchorY(),
                comment.getParentCommentId(),
                comment.annotationTypeOrDefault(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
