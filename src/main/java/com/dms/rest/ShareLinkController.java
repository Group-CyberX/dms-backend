package com.dms.rest;

import com.dms.dto.CreateShareLinkRequest;
import com.dms.dto.ShareLinkResponse;
import com.dms.models.ShareLink;
import com.dms.service.ShareLinkService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/share-links")
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkService service;

    // Create share link
    @PostMapping
    public ResponseEntity<ShareLinkResponse> createShareLink(@RequestBody CreateShareLinkRequest request) {
        ShareLinkResponse response = service.createShareLink(request);
        return ResponseEntity.ok(response);
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
        service.revokeLinkByToken(token);
        return ResponseEntity.ok("Share link revoked successfully");
    }
}
