package com.dms.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Request and response shapes for ERP connection management. */
public class ErpConnectionDTOs {

    /**
     * Create/update payload. apiKey is write-only: leave it null on an update to
     * keep the stored credential unchanged.
     */
    public record ErpConnectionRequest(
            String name,
            String erpType,
            String apiEndpoint,
            String authType,
            String apiKey,
            Boolean isActive
    ) {}

    /**
     * What the API returns. Deliberately has no credential field, so the stored
     * secret can never leak through this endpoint.
     */
    public record ErpConnectionResponse(
            UUID connectionId,
            String name,
            String erpType,
            String apiEndpoint,
            String authType,
            boolean hasCredential,
            boolean isActive,
            String status,
            LocalDateTime lastSyncedAt,
            String lastErrorMessage,
            LocalDateTime createdAt
    ) {}

    /** Outcome of a connectivity test. */
    public record ConnectionTestResult(
            boolean success,
            int attempts,
            String message,
            LocalDateTime testedAt
    ) {}

    /** Outcome of pulling records from an ERP. */
    public record SyncResult(
            UUID connectionId,
            boolean success,
            int transactionsFetched,
            int transactionsCreated,
            int documentsLinked,
            String message,
            LocalDateTime syncedAt
    ) {}
}
