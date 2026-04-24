package com.dms.service;

import com.dms.dao.ShareLinkRepository;
import com.dms.dto.CreateShareLinkRequest;
import com.dms.dto.ShareLinkResponse;
import com.dms.models.ShareLink;
import com.dms.enums.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareLinkService {

    private final ShareLinkRepository repository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // Creates a secure share link for a document
    public ShareLinkResponse createShareLink(CreateShareLinkRequest request) {

        String token = UUID.randomUUID().toString().replace("-", "");

        LocalDateTime expiryDate = null;
        if (request.getExpiryDays() > 0) {
        expiryDate = LocalDateTime.now().plusDays(request.getExpiryDays());
        }

        
        String hashedPassword = null;
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            hashedPassword = passwordEncoder.encode(request.getPassword());
        }

        ShareLink shareLink = ShareLink.builder()
                .documentId(request.getDocumentId())
                .token(token)
                .expiryDate(expiryDate)
                .accessLevel(AccessLevel.valueOf(request.getAccessLevel()))                
                .passwordHash(hashedPassword)
                .requireAuth(request.isRequireAuth())
                .allowDownload(request.isAllowDownload())
                .allowComments(request.isAllowComments())
                .isActive(true)
                .createdBy(UUID.randomUUID()) 
                .build();

        repository.save(shareLink);

        return ShareLinkResponse.builder()
                .url(frontendUrl + "/share/" + token)
                .expiresAt(expiryDate)
                .accessLevel(request.getAccessLevel())
                .build();
    }

    // Get share link details by token
    public ShareLink getByToken(String token) {
        return repository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Share link not found"));
    }

    // Validates a share link
    public ShareLink validateLink(String token, String password) {
        ShareLink link = getByToken(token);

        if (!link.isActive()) {
            throw new RuntimeException("Link is revoked");
        }

        // Auto-expire links that have passed their expiry date
        if (link.getExpiryDate() != null &&
                link.getExpiryDate().isBefore(LocalDateTime.now())) {
            link.setActive(false);
            repository.save(link);
            throw new RuntimeException("Link expired");
        }

        // Validate password if required
        if (link.getPasswordHash() != null && !link.getPasswordHash().isEmpty()) {
            if (password == null || password.trim().isEmpty()) {
                throw new RuntimeException("Password required");
            }

            boolean matches = passwordEncoder.matches(password.trim(), link.getPasswordHash());
            if (!matches) {
                throw new RuntimeException("Invalid password");
            }
        }
        return link;
    }

    // Revoke a share link using token
    public void revokeLinkByToken(String token) {
        ShareLink link = repository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Share link not found"));
        
        link.setActive(false);
        repository.save(link);
    }
}