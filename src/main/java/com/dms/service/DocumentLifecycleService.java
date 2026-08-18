package com.dms.service;

import com.dms.dao.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DocumentLifecycleService {

    private final DocumentRepository documentRepository;

    public DocumentLifecycleService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Moves a document to the status its workflow has just reached. Called when a
     * workflow starts, completes or is rejected, which is what takes a document
     * out of the NEW queue once someone has routed it.
     *
     * WorkflowInstance.documentId is a free-form varchar, so a reference that is
     * not a document id is ignored rather than failing the approval around it.
     */
    public void updateDocumentStatus(String documentId, String status) {
        if (documentId == null || documentId.isBlank() || status == null) {
            return;
        }

        UUID id;
        try {
            id = UUID.fromString(documentId.trim());
        } catch (IllegalArgumentException e) {
            return;
        }

        documentRepository.updateStatus(id, status);
    }
}
