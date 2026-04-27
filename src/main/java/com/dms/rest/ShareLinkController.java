package com.dms.rest;

import com.dms.dto.CreateShareLinkRequest;
import com.dms.dto.ShareLinkResponse;
import com.dms.models.ShareLink;
import com.dms.models.User;
import com.dms.dao.UserRepository;
import com.dms.service.ShareLinkService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/share-links")
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkService service;
    private final UserRepository userRepository;

    // Create share link
    @PostMapping
    public ResponseEntity<ShareLinkResponse> createShareLink(
            @RequestBody CreateShareLinkRequest request,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UUID userId = user.getUserId();

        return ResponseEntity.ok(
                service.createShareLink(request, userId)
        );
    }

    // Validate link access
    @GetMapping("/{token}")
    public ShareLink validateShareLink(@PathVariable String token,
                                       @RequestParam(required = false) String password) {
        return service.validateLink(token, password);
    }

    // Revoke link
    @DeleteMapping("/{token}")
    public ResponseEntity<?> revokeByToken(@PathVariable String token) {
        try {
            service.revokeLinkByToken(token);
            return ResponseEntity.ok("Share link revoked successfully");
        } catch (Exception e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    //Link Access Endpoint
    @PostMapping("/{token}/access")
    public ResponseEntity<?> access(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth
    ) {

        String password = body != null ? body.get("password") : null;

        UUID userId = null;

        if (auth != null && auth.isAuthenticated()) {

            String email = auth.getName();

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            userId = user.getUserId();
        }

        return ResponseEntity.ok(
                service.accessLink(token, password, userId)
        );
    }


        @GetMapping("/{token}/download")
        public ResponseEntity<byte[]> download(
                @PathVariable String token,
                @RequestParam(required = false) String password,
                Authentication auth
        ) {

            UUID userId = null;

            if (auth != null && auth.isAuthenticated()) {
                String email = auth.getName();
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                userId = user.getUserId();
            }

            return service.downloadFile(token, password, userId);
        }
}
