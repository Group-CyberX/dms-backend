package com.dms.config;

import com.dms.constants.WorkflowConstants;
import com.dms.dao.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gives a status to documents that were stored before the column existed.
 *
 * Anything a workflow was ever started on is already past NEW, so it is marked
 * PENDING_APPROVAL; everything else is NEW and will show up for whoever routes
 * new uploads. Documents that have since been approved or rejected are
 * corrected the next time their workflow moves.
 *
 * Runs once: after the first pass no row has a null status, so the queries
 * below return nothing and the class does no work on later restarts.
 */
@Component
public class DocumentStatusBackfill {

    private static final Logger log = LoggerFactory.getLogger(DocumentStatusBackfill.class);

    private final DocumentRepository documentRepository;

    public DocumentStatusBackfill(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        try {
            List<UUID> missing = documentRepository.findIdsWithoutStatus();
            if (missing.isEmpty()) {
                return;
            }

            Set<String> withWorkflow = new HashSet<>(documentRepository.findDocumentIdsWithWorkflow());

            int started = 0;
            for (UUID id : missing) {
                if (withWorkflow.contains(id.toString())) {
                    documentRepository.updateStatus(id, WorkflowConstants.DOCUMENT_PENDING_APPROVAL);
                    started++;
                }
            }

            // Whatever is still null never had a workflow, so it is a new upload.
            int fresh = documentRepository.backfillMissingStatus(WorkflowConstants.DOCUMENT_NEW);

            log.info("Document status backfill: {} already in a workflow, {} marked new.", started, fresh);
        } catch (Exception e) {
            // A backfill must never stop the application from serving requests.
            log.error("Document status backfill skipped: {}", e.getMessage());
        }
    }
}
