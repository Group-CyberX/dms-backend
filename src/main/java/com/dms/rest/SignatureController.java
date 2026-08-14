package com.dms.rest;

import com.dms.dto.DocumentSignRequest;
import com.dms.dto.UserSignatureRequest;
import com.dms.models.DigitalSignature;
import com.dms.models.UserSignature;
import com.dms.service.DigitalSignatureService;
import com.dms.service.SignatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/api/signatures")
public class SignatureController {

    private final SignatureService signatureService;
    private final DigitalSignatureService digitalSignatureService;

    public SignatureController(SignatureService signatureService, DigitalSignatureService digitalSignatureService) {
        this.signatureService = signatureService;
        this.digitalSignatureService = digitalSignatureService;
    }

    // 1. GET User Saved library profiles
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserSignature>> getMySignatures(@PathVariable UUID userId) {
        return ResponseEntity.ok(signatureService.getUserSignatures(userId));
    }

    // 2. POST Save new signature configurations (drawn, typed, or uploaded)
    // Removed the broken @pragma line and made it a normal comment
    @PostMapping("/user/{userId}")
    public ResponseEntity<UserSignature> createSignature(
            @PathVariable UUID userId,
            @RequestBody UserSignatureRequest request) {
        return ResponseEntity.ok(signatureService.saveUserSignature(userId, request));
    }

    // 3. DELETE Soft Delete profile template
    @DeleteMapping("/{signatureId}")
    public ResponseEntity<Void> removeSignature(@PathVariable UUID signatureId) {
        signatureService.softDeleteSignature(signatureId);
        return ResponseEntity.noContent().build();
    }

    // 4. POST Document sign transaction logger
    @PostMapping("/sign/{userId}")
    public ResponseEntity<DigitalSignature> logDocumentSigning(
            @PathVariable UUID userId,
            @RequestBody DocumentSignRequest request) {
        return ResponseEntity.ok(digitalSignatureService.signDocument(userId, request));
    }
}