package com.dms.rest;

import com.dms.dao.UserRepository;
import com.dms.dto.AddCommentRequest;
import com.dms.dto.CommentResponse;
import com.dms.models.User;
import com.dms.models.Comment;
import com.dms.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService service;
    private final UserRepository userRepository;

    /** The caller's id, or null when the request carries no login. */
    private UUID currentUserId(Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) return null;

        return userRepository.findByEmail(auth.getName())
                .map(User::getUserId)
                .orElse(null);
    }

    // Add new comment for shared document
    @PostMapping("/{token}")
    public Comment add(
            @PathVariable String token,
            @RequestBody AddCommentRequest request,
            Authentication auth
    ) {
        // The service rejects anonymous callers and view-only links.
        return service.addComment(token, currentUserId(auth), request);
    }

    /**
     * All comments on a share link.
     *
     * The caller is resolved so each comment can say whether it is theirs -
     * the share page has no other way to know, and uses it to decide whether
     * to offer Edit and Delete.
     */
    @GetMapping("/{token}")
    public List<CommentResponse> get(@PathVariable String token, Authentication auth) {
        return service.getComments(token, currentUserId(auth));
    }

    // Edit existing comment (only owner allowed)
    @PutMapping("/{commentId}")
    public Comment edit(
            @PathVariable UUID commentId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {

        UUID userId = currentUserId(auth);
        if (userId == null) {
            throw new AccessDeniedException("Login required to edit a comment");
        }

        return service.editComment(commentId, userId, body.get("content"));
    }

    // Delete comment (soft delete, only owner allowed)
    @DeleteMapping("/{commentId}")
    public String delete(
            @PathVariable UUID commentId,
            Authentication auth
    ) {

        UUID userId = currentUserId(auth);
        if (userId == null) {
            throw new AccessDeniedException("Login required to delete a comment");
        }

        service.deleteComment(commentId, userId);

        return "Comment deleted";
    }
}
