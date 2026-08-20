package com.dms.service;

import com.dms.dao.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(DocumentLifecycleService.class);

    private final DocumentRepository documentRepository;

    public DocumentLifecycleService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Records where a document has reached in its life.
     *
     * This is called from the three points that already decide it - a workflow
     * starting, a final approval, and a rejection - and until now only printed
     * to the console, so the document's own row never learned its status and
     * nothing could list documents by it.
     *
     * A single update by primary key, on a transition that happens once per
     * workflow step. Nothing on the read path changes.
     *
     * The workflow tables carry documentId as text while the document's key is
     * a uuid, so the value is parsed here; an unparseable one is logged and
     * ignored rather than failing the approval that triggered it.
     */
    @Transactional
    public void updateDocumentStatus(String documentId, String status) {
        if (documentId == null || documentId.isBlank() || status == null) {
            return;
        }

        final UUID id;
        try {
            id = UUID.fromString(documentId.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring status update for '{}': not a document id.", documentId);
            return;
        }

        int updated = documentRepository.updateStatus(id, status);
        if (updated == 0) {
            log.warn("No document {} to mark {}.", id, status);
        }
    }
}
