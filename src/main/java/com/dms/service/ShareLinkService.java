package com.dms.service;

import com.dms.dao.ShareLinkRepository;
import com.dms.dto.CreateShareLinkRequest;
import com.dms.dto.ShareLinkResponse;
import com.dms.models.ShareLink;
import com.dms.models.ShareAccessLog;
import com.dms.models.Documents;
import com.dms.enums.AccessLevel;
import com.dms.dao.ShareAccessLogRepository;
import com.dms.dao.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShareLinkService {

    private final ShareLinkRepository repository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ShareAccessLogRepository accessLogRepository;
    private final DocumentRepository documentRepository;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    // Creates a secure share link for a document
    public ShareLinkResponse createShareLink(CreateShareLinkRequest request, UUID userId) {

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
                .createdBy(userId)
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found"));
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

    // Handles accessing a share link
    public Map<String, Object> accessLink(
            String token,
            String password,
            UUID userId
    ) {
        //validate link
        ShareLink link = validateLink(token, password);

        // check authentication requirement
        if (link.isRequireAuth() && userId == null) {
            throw new RuntimeException("Authentication required");
        }

        // Fetch document title
        Documents doc = documentRepository.findById(link.getDocumentId())
            .orElse(null);

        // Save access log
        ShareAccessLog log = new ShareAccessLog();
        log.setToken(token);
        log.setDocumentId(link.getDocumentId());
        if (userId != null) {
            log.setUserId(userId);
        }
        log.setAccessedAt(LocalDateTime.now());

        accessLogRepository.save(log);

        return Map.of(
                "documentId", link.getDocumentId(),
                "documentName", doc != null ? doc.getTitle() : "Document",
                "allowDownload", link.isAllowDownload(),
                "allowComments", link.isAllowComments()
        );
    }

    public ResponseEntity<byte[]> downloadFile(
        String token,
        String password,
        UUID userId
    ) {

        ShareLink link = validateLink(token, password);

        if (link.isRequireAuth() && userId == null) {
            throw new RuntimeException("Authentication required");
        }

        if (!link.isAllowDownload()) {
            throw new RuntimeException("Download not allowed");
        }

        UUID documentId = link.getDocumentId();

        byte[] fileData = ("File for document: " + documentId).getBytes();

        ShareAccessLog log = new ShareAccessLog();
        log.setToken(token);
        log.setUserId(userId);
        log.setDownloaded(true);
        log.setAccessedAt(LocalDateTime.now());

        accessLogRepository.save(log);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=document.txt")
                .body(fileData);
    }
}