package com.dms.service;

import com.dms.dao.DocumentErpLinkRepository;
import com.dms.dao.DocumentRepository;
import com.dms.dao.DocumentVersionRepository;
import com.dms.dao.ErpTransactionRepository;
import com.dms.models.DocumentErpLink;
import com.dms.models.DocumentVersions;
import com.dms.models.Documents;
import com.dms.models.ErpTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connects uploaded documents to the ERP transactions they belong to.
 *
 * This is step 5 of §8.1 ("ERP sync check -> Link to relevant ERP entities") and
 * the arrow labelled "Link document to ERP entity" in Figure 5.3.19. When an
 * invoice is uploaded and OCR reads "PO-2026-0042" out of it, the document is
 * attached to that purchase order without anyone typing anything.
 *
 * Matching is pattern-based: references look like PO-2026-0042 or INV-2026-Q1-001,
 * so a small set of patterns covers the transaction types we sync. Fuzzy matching
 * on vendor and amount would be the next step, and is noted as further work.
 */
@Service
public class ErpDocumentLinkService {

    /**
     * Reference shapes we recognise in document text. Deliberately anchored on a
     * prefix so ordinary numbers in an invoice are not mistaken for references.
     */
    private static final List<Pattern> REFERENCE_PATTERNS = List.of(
            Pattern.compile("\\bPO[-/ ]?\\d{4}[-/ ]?\\d{3,6}\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bINV[-/ ]?\\d{4}[-/ ]?[A-Z0-9]{1,4}[-/ ]?\\d{1,6}\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:PO|INV)[-/ ]?[A-Z0-9]{2,}[-/ ][A-Z0-9-]{2,}\\b", Pattern.CASE_INSENSITIVE)
    );

    private final DocumentErpLinkRepository linkRepository;
    private final ErpTransactionRepository transactionRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public ErpDocumentLinkService(DocumentErpLinkRepository linkRepository,
                                  ErpTransactionRepository transactionRepository,
                                  DocumentVersionRepository documentVersionRepository,
                                  DocumentRepository documentRepository,
                                  AuditLogService auditLogService,
                                  NotificationService notificationService) {
        this.linkRepository = linkRepository;
        this.transactionRepository = transactionRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentRepository = documentRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    /**
     * Called after OCR finishes. Looks for a transaction reference in the
     * document's title and extracted text, and links it if one matches.
     *
     * The title is searched as well as the text because filing a purchase order
     * as "PO-2026-0042.pdf" is the ordinary way people name these documents.
     * Relying on OCR alone meant nothing linked for a born-digital PDF, for an
     * upload whose OCR pass had not finished, or on a machine without Tesseract
     * installed.
     *
     * Never throws: a failure to link must not fail the upload that triggered it.
     *
     * @return the link if one was created
     */
    @Transactional
    public Optional<DocumentErpLink> tryLinkFromText(UUID documentId, String extractedText) {
        try {
            if (documentId == null) {
                return Optional.empty();
            }

            // Already linked - leave the existing association alone.
            if (!linkRepository.findByDocumentId(documentId).isEmpty()) {
                return Optional.empty();
            }

            String title = documentRepository.findById(documentId)
                    .map(Documents::getTitle).orElse(null);
            String haystack = (title == null ? "" : title)
                    + (extractedText == null ? "" : "\n" + extractedText);
            if (haystack.isBlank()) {
                return Optional.empty();
            }

            for (String candidate : findReferences(haystack)) {
                Optional<ErpTransaction> match =
                        transactionRepository.findByExternalRefIgnoreCase(normalise(candidate));
                if (match.isPresent()) {
                    return Optional.of(createLink(documentId, match.get(), candidate, "AUTO", null));
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            System.err.println("ERP auto-link skipped for document " + documentId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Instantly links a document when the exact reference is provided by the client (e.g. from ERP).
     */
    @Transactional
    public Optional<DocumentErpLink> linkExactReference(UUID documentId, String reference) {
        if (documentId == null || reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        
        try {
            // Check if already linked
            if (!linkRepository.findByDocumentId(documentId).isEmpty()) {
                return Optional.empty();
            }

            Optional<ErpTransaction> match = transactionRepository.findByExternalRefIgnoreCase(normalise(reference));
            if (match.isPresent()) {
                return Optional.of(createLink(documentId, match.get(), reference, "AUTO", null));
            }
        } catch (Exception e) {
            System.err.println("ERP exact-link failed for document " + documentId + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Re-checks every active document that has no ERP link yet. Run after a
     * sync, so transactions that arrive later still pick up documents uploaded
     * earlier.
     *
     * Walks documents rather than versions, because a document with no OCR text
     * can still be matched on its title, and iterating versions skipped those
     * entirely.
     *
     * @return number of links created
     */
    @Transactional
    public int linkPendingDocuments() {
        int created = 0;
        try {
            // Only the versions that carry extracted text, and only the two
            // columns needed - not every column of every version.
            Map<UUID, StringBuilder> textByDocument = new HashMap<>();
            for (DocumentVersionRepository.OcrText version : documentVersionRepository.findOcrText()) {
                textByDocument.computeIfAbsent(version.getDocumentId(), id -> new StringBuilder())
                        .append('\n').append(version.getOcrContent());
            }

            for (Documents document : documentRepository.findAllActive()) {
                StringBuilder ocr = textByDocument.get(document.getDocument_id());
                if (tryLinkFromText(document.getDocument_id(),
                        ocr == null ? null : ocr.toString()).isPresent()) {
                    created++;
                }
            }
        } catch (Exception e) {
            System.err.println("ERP back-link pass failed: " + e.getMessage());
        }
        return created;
    }

    /** Link chosen by a person in the UI, when auto-matching found nothing. */
    @Transactional
    public DocumentErpLink linkManually(UUID documentId, UUID transactionId, UUID userId) {
        ErpTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("ERP transaction not found: " + transactionId));

        // Re-requesting a link that already exists is idempotent - it returns
        // that same link rather than erroring or duplicating it. This has to be
        // looked up by (document, transaction) together: a document that is
        // already linked to two different transactions and gets re-linked to
        // the second one previously came back holding the first one's link
        // instead, because the lookup only kept the document id and dropped
        // which transaction had actually been asked for.
        Optional<DocumentErpLink> existing =
                linkRepository.findByDocumentIdAndTransactionId(documentId, transactionId);
        if (existing.isPresent()) {
            return existing.get();
        }

        return createLink(documentId, transaction, transaction.getExternalRef(), "MANUAL", userId);
    }

    @Transactional
    public void unlink(UUID linkId) {
        linkRepository.deleteById(linkId);
    }

    public List<DocumentErpLink> linksForDocument(UUID documentId) {
        return linkRepository.findByDocumentId(documentId);
    }

    // ------------------------------------------------------------------

    private DocumentErpLink createLink(UUID documentId, ErpTransaction transaction,
                                       String matchedReference, String linkType, UUID userId) {

        DocumentErpLink link = linkRepository.save(DocumentErpLink.builder()
                .documentId(documentId)
                .transactionId(transaction.getTransactionId())
                .linkType(linkType)
                .matchedReference(matchedReference)
                .createdBy(userId)
                .build());

        auditLogService.createAuditLog(
                "AUTO".equals(linkType) ? "ERP_DOCUMENT_AUTO_LINKED" : "ERP_DOCUMENT_LINKED",
                documentId, "internal", "SUCCESS");

        if (userId != null) {
            notificationService.sendNotification(userId,
                    "Document linked to ERP transaction " + transaction.getExternalRef());
        } else {
            notificationService.sendInternalSystemNotification(
                    "Document automatically linked to ERP transaction " + transaction.getExternalRef());
        }

        return link;
    }

    /** All distinct reference-looking strings in the text, in order of appearance. */
    List<String> findReferences(String text) {
        return REFERENCE_PATTERNS.stream()
                .flatMap(pattern -> {
                    Matcher matcher = pattern.matcher(text);
                    return matcher.results().map(result -> result.group().trim());
                })
                .distinct()
                .toList();
    }

    /** OCR often turns "PO-2026-0042" into "PO 2026 0042"; normalise separators. */
    String normalise(String reference) {
        return reference.replaceAll("[\\s/]+", "-").toUpperCase();
    }
}
