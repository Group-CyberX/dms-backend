package com.dms.rest;

import com.dms.dto.DocumentSignRequest;
import com.dms.dto.SignAndApproveRequest;
import com.dms.dto.SignAndApproveResponse;
import com.dms.dto.UserSignatureRequest;
import com.dms.models.DigitalSignature;
import com.dms.models.UserSignature;
import com.dms.security.SecurityUtils;
import com.dms.service.DigitalSignatureService;
import com.dms.service.DocumentSigningService;
import com.dms.service.SignatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/api/signatures")
public class SignatureController {

    private final SignatureService signatureService;
    private final DigitalSignatureService digitalSignatureService;
    private final DocumentSigningService documentSigningService;

    public SignatureController(SignatureService signatureService,
                               DigitalSignatureService digitalSignatureService,
                               DocumentSigningService documentSigningService) {
        this.signatureService = signatureService;
        this.digitalSignatureService = digitalSignatureService;
        this.documentSigningService = documentSigningService;
    }

    /**
     * A signature identifies a person, so the signer is always taken from the
     * JWT - never from the URL. The {userId} path variable is kept only so the
     * existing client URLs still resolve; supplying someone else's id is a 403,
     * not a way to act as them. Requirements 11.3 (non-repudiation) depends on
     * this being true.
     */
    private UUID requireSelf(UUID userIdFromPath) {
        UUID caller = SecurityUtils.currentUserId();
        if (userIdFromPath != null && !caller.equals(userIdFromPath)) {
            throw new AccessDeniedException("A signature may only be created or read by its own owner.");
        }
        return caller;
    }

    // 1. GET User Saved library profiles
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserSignature>> getMySignatures(@PathVariable UUID userId) {
        return ResponseEntity.ok(signatureService.getUserSignatures(requireSelf(userId)));
    }

    // 2. POST Save new signature configurations (drawn, typed, or uploaded)
    // Removed the broken @pragma line and made it a normal comment
    @PostMapping("/user/{userId}")
    public ResponseEntity<UserSignature> createSignature(
            @PathVariable UUID userId,
            @RequestBody UserSignatureRequest request) {
        return ResponseEntity.ok(signatureService.saveUserSignature(requireSelf(userId), request));
    }

    // 3. DELETE Soft Delete profile template - owner only.
    @DeleteMapping("/{signatureId}")
    public ResponseEntity<Void> removeSignature(@PathVariable UUID signatureId) {
        signatureService.softDeleteSignature(signatureId, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    // 4. POST Document sign transaction logger
    @PostMapping("/sign/{userId}")
    public ResponseEntity<DigitalSignature> logDocumentSigning(
            @PathVariable UUID userId,
            @RequestBody DocumentSignRequest request) {
        return ResponseEntity.ok(digitalSignatureService.signDocument(requireSelf(userId), request));
    }

    // 5. POST Stamp the placed signatures into the PDF, store it as a new
    //    version, log the signature, and approve the workflow task if given.
    @PostMapping("/sign-and-approve")
    public ResponseEntity<SignAndApproveResponse> signAndApprove(
            @RequestBody SignAndApproveRequest request) throws IOException {

        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(documentSigningService.signAndApprove(userId, request));
    }
}