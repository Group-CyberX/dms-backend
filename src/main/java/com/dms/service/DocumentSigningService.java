package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dto.DocumentSignRequest;
import com.dms.dto.SignAndApproveRequest;
import com.dms.dto.SignAndApproveResponse;
import com.dms.dto.WorkflowTaskActionRequest;
import com.dms.exceptions.ResourceNotFoundException;
import com.dms.models.DigitalSignature;
import com.dms.models.DocumentVersions;
import com.dms.models.Documents;
import com.dms.util.InMemoryMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Ties together the pieces of "sign this document and approve the task":
 * fetch the current version, stamp the signatures into it, store the result as
 * a new version, record the signature for audit, then advance the workflow.
 *
 * The stamped file becomes version N+1 rather than overwriting N, so the
 * unsigned original stays available in the version history - which is what makes
 * the chain of custody defensible.
 */
@Service
public class DocumentSigningService {

    /** Metadata key the document panel and Advanced Search both read. */
    private static final String SIGNATURE_STATUS_KEY = "signatureStatus";
    private static final String SIGNATURE_SIGNED = "signed";

    private final DocumentRepository documentRepository;
    private final DocumentVersionService documentVersionService;
    private final PdfSignatureStampingService stampingService;
    private final DigitalSignatureService digitalSignatureService;
    private final WorkflowTaskService workflowTaskService;
    private final DocumentLockService documentLockService;
    private final DocumentMetadataService documentMetadataService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public DocumentSigningService(DocumentRepository documentRepository,
                                  DocumentVersionService documentVersionService,
                                  PdfSignatureStampingService stampingService,
                                  DigitalSignatureService digitalSignatureService,
                                  WorkflowTaskService workflowTaskService,
                                  DocumentLockService documentLockService,
                                  DocumentMetadataService documentMetadataService,
                                  AuditLogService auditLogService,
                                  NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.documentVersionService = documentVersionService;
        this.stampingService = stampingService;
        this.digitalSignatureService = digitalSignatureService;
        this.workflowTaskService = workflowTaskService;
        this.documentLockService = documentLockService;
        this.documentMetadataService = documentMetadataService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @Transactional
    public SignAndApproveResponse signAndApprove(UUID userId, SignAndApproveRequest request) throws IOException {

        // Signing writes a new version, so it has to respect the edit lock -
        // otherwise two writers could produce versions at the same moment.
        documentLockService.assertCanMutate(request.documentId(), userId);

        Documents document = documentRepository.findById(request.documentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document not found: " + request.documentId()));

        UUID currentVersionId = document.getCurrent_version_id();
        if (currentVersionId == null) {
            throw new IllegalStateException("Document has no current version to sign");
        }

        // 1. Pull the bytes of the version being signed.
        byte[] original = documentVersionService.getVersionFileBytes(request.documentId(), currentVersionId);

        // 2. Burn the signatures in.
        byte[] stamped = stampingService.stamp(original, request.placements());

        // 3. Store the result as the next version.
        String filename = buildSignedFilename(document.getTitle());
        InMemoryMultipartFile upload = new InMemoryMultipartFile(
                "file", filename, "application/pdf", stamped);
        DocumentVersions newVersion = documentVersionService.uploadNewVersion(request.documentId(), upload);

        // 4. Record the signature against the version that now carries it.
        String hash = sha256Hex(stamped);
        DigitalSignature signature = digitalSignatureService.signDocument(
                userId,
                new DocumentSignRequest(newVersion.getVersion_id(), request.comments(), hash));

        // 5. Tell the rest of the system the document now carries a signature.
        //
        // The document detail panel reads its "SIGNATURE" line from this
        // metadata key, and Advanced Search filters on it. It was written once
        // at upload from a check of the uploaded file itself and never touched
        // again, so a document signed here still described itself as unsigned -
        // the panel disagreed with the signature record and with the stamp
        // visible in the file.
        markSigned(request.documentId());

        // 6. Advance the workflow, if this signing came from a task.
        boolean taskApproved = false;
        if (request.taskId() != null) {
            WorkflowTaskActionRequest action = new WorkflowTaskActionRequest();
            action.setComment(request.comments());
            workflowTaskService.approveTask(request.taskId(), action);
            taskApproved = true;
        }

        // A signature is the strongest claim the system makes about who did
        // what, so it is recorded against the signer and announced to the owner.
        auditLogService.tryRecord("DOCUMENT_SIGNED", userId, request.documentId(), null, "SUCCESS");

        Documents signedDocument = documentRepository.findById(request.documentId()).orElse(null);
        if (signedDocument != null && signedDocument.getOwner_id() != null
                && !signedDocument.getOwner_id().equals(userId)) {
            notificationService.sendNotification(signedDocument.getOwner_id(),
                    "'" + signedDocument.getTitle() + "' has been signed");
        }

        return new SignAndApproveResponse(
                request.documentId(),
                newVersion.getVersion_id(),
                newVersion.getVersion_number(),
                signature.getSignatureId(),
                hash,
                request.placements().size(),
                taskApproved
        );
    }

    /**
     * Records that this document is signed, creating the metadata entry if the
     * upload never wrote one.
     *
     * Never throws: the signature is already stored and the workflow has moved
     * on, so failing here would roll back real work over a display field.
     */
    private void markSigned(UUID documentId) {
        try {
            documentMetadataService.getMetadataByKey(documentId, SIGNATURE_STATUS_KEY)
                    .ifPresentOrElse(
                            existing -> documentMetadataService.updateMetadata(
                                    documentId, SIGNATURE_STATUS_KEY, SIGNATURE_SIGNED),
                            () -> documentMetadataService.addMetadata(
                                    documentId, SIGNATURE_STATUS_KEY, SIGNATURE_SIGNED));
        } catch (Exception e) {
            System.err.println("Could not update signatureStatus for document "
                    + documentId + ": " + e.getMessage());
        }
    }

    private String buildSignedFilename(String title) {
        String base = (title == null || title.isBlank()) ? "document" : title;
        if (base.toLowerCase().endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        return base + "-signed.pdf";
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec; this cannot happen in practice.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
