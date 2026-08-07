package com.dms.rest;

import com.dms.dao.UserRepository;
import com.dms.models.User;
import com.dms.models.Comment;
import com.dms.service.CommentService;
import lombok.RequiredArgsConstructor;
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

    // Add new comment for shared document
    @PostMapping("/{token}")
    public Comment add(
            @PathVariable String token,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {

        UUID userId = null;

        // Get user ID if authenticated
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            userId = user.getUserId();
        }

        // Add comment with optional user ID (null for anonymous)
        return service.addComment(
                token,
                userId,
                body.get("content")
        );
    }

    // Get all comments for a share link
    @GetMapping("/{token}")
    public List<Comment> get(@PathVariable String token) {
        return service.getComments(token);
    }

    // Edit existing comment (only owner allowed)
    @PutMapping("/{commentId}")
    public Comment edit(
            @PathVariable UUID commentId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {

        // Ensure user is authenticated
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return service.editComment(
                commentId,
                user.getUserId(),
                body.get("content")
        );
    }

    // Delete comment (soft delete, only owner allowed)
    @DeleteMapping("/{commentId}")
    public String delete(
            @PathVariable UUID commentId,
            Authentication auth
    ) {

        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        service.deleteComment(commentId, user.getUserId());

        return "Comment deleted";
    }
}
