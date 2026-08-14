package com.dms.service;

import com.dms.dto.DocumentSignRequest;
import com.dms.models.DigitalSignature;
import com.dms.dao.DigitalSignatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class DigitalSignatureService {

    private final DigitalSignatureRepository digitalSignatureRepository;

    public DigitalSignatureService(DigitalSignatureRepository digitalSignatureRepository) {
        this.digitalSignatureRepository = digitalSignatureRepository;
    }

    @Transactional
    public DigitalSignature signDocument(UUID userId, DocumentSignRequest request) {
        DigitalSignature signature = new DigitalSignature();
        signature.setDocumentVersionId(request.documentVersionId());
        signature.setSignatureUserId(userId);
        signature.setComments(request.comments());
        signature.setHash(request.documentHash() != null ? request.documentHash() : UUID.randomUUID().toString());
        signature.setAlgorithm("SHA-256");
        signature.setStatus("VALID");

        return digitalSignatureRepository.save(signature);
    }
}
