package com.dms.service;

import com.dms.dto.UserSignatureRequest;
import com.dms.models.UserSignature;
import com.dms.dao.UserSignatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class SignatureService {

    private final UserSignatureRepository userSignatureRepository;

    public SignatureService(UserSignatureRepository userSignatureRepository) {
        this.userSignatureRepository = userSignatureRepository;
    }

    public List<UserSignature> getUserSignatures(UUID userId) {
        return userSignatureRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    @Transactional
    public UserSignature saveUserSignature(UUID userId, UserSignatureRequest request) {
        // CHANGED: request.isDefault() to request.isDefault() [matches boolean getter naming convention]
        if (request.isDefault()) {
            userSignatureRepository.clearDefaultStatusForUser(userId);
        }

        UserSignature signature = new UserSignature();
        signature.setUserId(userId);

        // CHANGED: request.label() -> request.getLabel()
        signature.setLabel(request.getLabel());

        // CHANGED: request.signatureType() -> request.getSignatureType()
        if (request.getSignatureType() != null) {
            signature.setSignatureType(request.getSignatureType().toUpperCase());
        }

        // CHANGED: request.isDefault() to request.isDefault()
        signature.setDefault(request.isDefault());

        // CHANGED: request.signatureDataUrl() -> request.getSignatureDataUrl()
        if (request.getSignatureDataUrl() != null && request.getSignatureDataUrl().contains(",")) {
            String base64ImageBytes = request.getSignatureDataUrl().split(",")[1];
            byte[] imageBytes = Base64.getDecoder().decode(base64ImageBytes);
            signature.setSignatureImage(imageBytes);
        }

        return userSignatureRepository.save(signature);
    }

    @Transactional
    public void softDeleteSignature(UUID signatureId) {
        userSignatureRepository.findByUserSignatureIdAndDeletedAtIsNull(signatureId)
                .ifPresent(sig -> {
                    sig.setDeletedAt(OffsetDateTime.now());
                    sig.setDefault(false);
                    userSignatureRepository.save(sig);
                });
    }
}