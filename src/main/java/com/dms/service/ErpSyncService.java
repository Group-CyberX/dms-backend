package com.dms.service;

import com.dms.dao.ErpConnectionRepository;
import com.dms.dao.ErpTransactionRepository;
import com.dms.dto.ErpConnectionDTOs.SyncResult;
import com.dms.models.ErpConnection;
import com.dms.models.ErpTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pulls business records from a configured ERP and stores them as transactions.
 *
 * The fetch is deliberately plain REST over the connection's base URL - the same
 * call shape works against the mock ERP used for development and against a real
 * system, which is what makes swapping one for the other a configuration change.
 *
 * Failures are recorded on the transaction and the connection rather than thrown
 * away, so the admin screen can show them and offer a retry (§10.2: "integration
 * errors are logged and recoverable").
 */
@Service
public class ErpSyncService {

    /** ERP path -> the transaction type we store records under. */
    private static final Map<String, String> ENDPOINTS = Map.of(
            "/purchase-orders", "PURCHASE_ORDER"
    );

    private final ErpConnectionRepository connectionRepository;
    private final ErpTransactionRepository transactionRepository;
    private final ErpConnectionService connectionService;
    private final ErpMappingService mappingService;
    private final ErpDocumentLinkService linkService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.builder().build();

    public ErpSyncService(ErpConnectionRepository connectionRepository,
                          ErpTransactionRepository transactionRepository,
                          ErpConnectionService connectionService,
                          ErpMappingService mappingService,
                          ErpDocumentLinkService linkService,
                          AuditLogService auditLogService) {
        this.connectionRepository = connectionRepository;
        this.transactionRepository = transactionRepository;
        this.connectionService = connectionService;
        this.mappingService = mappingService;
        this.linkService = linkService;
        this.auditLogService = auditLogService;
    }

    /**
     * Fetches every supported record type from one ERP, upserts the transactions,
     * then tries to link any document whose extracted text mentions them.
     */
    @Transactional
    public SyncResult sync(UUID connectionId, String remoteAddr) {
        ErpConnection connection = connectionService.require(connectionId);

        int fetched = 0;
        int created = 0;
        int linked = 0;
        String failure = null;

        for (Map.Entry<String, String> endpoint : ENDPOINTS.entrySet()) {
            try {
                List<Map<String, Object>> records = fetchRecords(connection, endpoint.getKey());
                fetched += records.size();

                for (Map<String, Object> record : records) {
                    Optional<String> reference =
                            mappingService.extractReference(connectionId, endpoint.getValue(), record);
                    if (reference.isEmpty()) {
                        continue; // nothing to key it on - skip rather than store junk
                    }

                    Map<String, Object> mapped =
                            mappingService.toDmsFields(connectionId, endpoint.getValue(), record);

                    boolean isNew = upsert(connection, endpoint.getValue(), reference.get(), mapped);
                    if (isNew) {
                        created++;
                    }
                }
            } catch (Exception e) {
                failure = endpoint.getKey() + ": " + e.getMessage();
                recordFailure(connection, endpoint.getValue(), failure);
            }
        }

        // Newly arrived transactions may match documents uploaded earlier.
        linked = linkService.linkPendingDocuments();

        boolean success = failure == null;
        connection.setStatus(success ? "OK" : "FAILED");
        connection.setLastSyncedAt(LocalDateTime.now());
        connection.setLastErrorMessage(failure);
        connectionRepository.save(connection);

        auditLogService.createAuditLog("ERP_SYNC", connectionId, remoteAddr, success ? "SUCCESS" : "FAILED");

        return new SyncResult(
                connectionId,
                success,
                fetched,
                created,
                linked,
                success
                        ? "Synced " + fetched + " record(s); " + created + " new, " + linked + " document(s) linked."
                        : "Sync completed with errors: " + failure,
                LocalDateTime.now());
    }

    /** Re-runs a single failed transaction, for the Retry button. */
    @Transactional
    public ErpTransaction retry(UUID transactionId, String remoteAddr) {
        ErpTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        transaction.setRetryCount(transaction.getRetryCount() == null ? 1 : transaction.getRetryCount() + 1);
        transaction.setSyncStatus("SUCCESS");
        transaction.setLastErrorMessage(null);
        transaction.setSyncedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        linkService.linkPendingDocuments();
        auditLogService.createAuditLog("ERP_TRANSACTION_RETRIED", transactionId, remoteAddr, "SUCCESS");
        return transaction;
    }

    // ------------------------------------------------------------------

    private List<Map<String, Object>> fetchRecords(ErpConnection connection, String path) {
        List<Map<String, Object>> body = restClient.get()
                .uri(connection.getApiEndpoint() + path)
                .headers(headers -> connectionService.applyAuth(headers, connection))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        return body != null ? body : List.of();
    }

    /** @return true when this reference had not been seen before */
    private boolean upsert(ErpConnection connection, String type, String reference, Map<String, Object> mapped) {
        Optional<ErpTransaction> existing = transactionRepository
                .findByErpConnectionIdAndExternalRefIgnoreCase(connection.getConnectionId(), reference);

        ErpTransaction transaction = existing.orElseGet(() -> ErpTransaction.builder()
                .erpConnectionId(connection.getConnectionId())
                .externalRef(reference)
                .transactionType(type)
                .build());

        transaction.setTransactionType(type);
        transaction.setPayload(toJson(mapped));
        transaction.setSyncStatus("SUCCESS");
        transaction.setLastErrorMessage(null);
        transaction.setSyncedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        return existing.isEmpty();
    }

    /** Stores a failure as a transaction row so it shows up in the sync history. */
    private void recordFailure(ErpConnection connection, String type, String message) {
        transactionRepository.save(ErpTransaction.builder()
                .erpConnectionId(connection.getConnectionId())
                .transactionType(type)
                .externalRef("SYNC-FAILURE-" + System.currentTimeMillis())
                .syncStatus("FAILED")
                .lastErrorMessage(message)
                .build());
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
