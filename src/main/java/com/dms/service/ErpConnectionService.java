package com.dms.service;

import com.dms.dao.DocumentErpLinkRepository;
import com.dms.dao.ErpConnectionRepository;
import com.dms.dao.ErpTransactionRepository;
import com.dms.dao.IntegrationMappingRepository;
import com.dms.dto.ErpConnectionDTOs.ConnectionTestResult;
import com.dms.dto.ErpConnectionDTOs.ErpConnectionRequest;
import com.dms.dto.ErpConnectionDTOs.ErpConnectionResponse;
import com.dms.exceptions.ResourceNotFoundException;
import com.dms.models.ErpConnection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages the ERP systems the DMS is configured to talk to.
 *
 * Credentials are encrypted before they are stored and are never returned by the
 * API. Connectivity testing retries with exponential backoff, which is the
 * behaviour drawn in Figure 5.3.10 and required by §5.2 ("retry mechanisms with
 * exponential backoff").
 */
@Service
public class ErpConnectionService {

    private static final int MAX_ATTEMPTS = 3;

    private final ErpConnectionRepository connectionRepository;
    private final IntegrationMappingRepository mappingRepository;
    private final ErpTransactionRepository transactionRepository;
    private final DocumentErpLinkRepository documentErpLinkRepository;
    private final CryptoService cryptoService;
    private final AuditLogService auditLogService;

    private final RestClient restClient = RestClient.builder().build();

    public ErpConnectionService(ErpConnectionRepository connectionRepository,
                                IntegrationMappingRepository mappingRepository,
                                ErpTransactionRepository transactionRepository,
                                DocumentErpLinkRepository documentErpLinkRepository,
                                CryptoService cryptoService,
                                AuditLogService auditLogService) {
        this.connectionRepository = connectionRepository;
        this.mappingRepository = mappingRepository;
        this.transactionRepository = transactionRepository;
        this.documentErpLinkRepository = documentErpLinkRepository;
        this.cryptoService = cryptoService;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    public List<ErpConnectionResponse> listConnections() {
        return connectionRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ErpConnectionResponse getConnection(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public ErpConnectionResponse create(ErpConnectionRequest request, String remoteAddr) {
        validate(request);

        ErpConnection connection = ErpConnection.builder()
                .name(request.name().trim())
                .erpType(blankToDefault(request.erpType(), "GENERIC"))
                .apiEndpoint(stripTrailingSlash(request.apiEndpoint().trim()))
                .authType(blankToDefault(request.authType(), "NONE"))
                .authConfig(cryptoService.encrypt(request.apiKey()))
                .isActive(request.isActive() == null || request.isActive())
                .status("UNKNOWN")
                .build();

        connection = connectionRepository.save(connection);
        auditLogService.createAuditLog("ERP_CONNECTION_CREATED", connection.getConnectionId(), remoteAddr, "SUCCESS");
        return toResponse(connection);
    }

    @Transactional
    public ErpConnectionResponse update(UUID id, ErpConnectionRequest request, String remoteAddr) {
        validate(request);
        ErpConnection connection = require(id);

        connection.setName(request.name().trim());
        connection.setErpType(blankToDefault(request.erpType(), connection.getErpType()));
        connection.setApiEndpoint(stripTrailingSlash(request.apiEndpoint().trim()));
        connection.setAuthType(blankToDefault(request.authType(), connection.getAuthType()));
        if (request.isActive() != null) {
            connection.setIsActive(request.isActive());
        }
        // A blank key on update means "leave the stored credential alone".
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            connection.setAuthConfig(cryptoService.encrypt(request.apiKey()));
        }

        connection = connectionRepository.save(connection);
        auditLogService.createAuditLog("ERP_CONNECTION_UPDATED", id, remoteAddr, "SUCCESS");
        return toResponse(connection);
    }

    /**
     * Deletes a connection along with everything sync ever produced under it.
     *
     * A synced connection has transactions, and a transaction can have
     * documents linked to it, so deleting the connection alone left the
     * transactions behind - Postgres then refused the delete with a raw
     * foreign-key error instead of the operation either succeeding or failing
     * with a reason the interface could show. The three deletes below run in
     * dependency order: the links first, since they are the innermost
     * reference, then the transactions, then the connection itself.
     */
    @Transactional
    public void delete(UUID id, String remoteAddr) {
        ErpConnection connection = require(id);

        documentErpLinkRepository.deleteByConnectionId(id);
        transactionRepository.deleteByErpConnectionId(id);
        mappingRepository.deleteByErpConnectionId(id);
        connectionRepository.delete(connection);

        auditLogService.createAuditLog("ERP_CONNECTION_DELETED", id, remoteAddr, "SUCCESS");
    }

    // ------------------------------------------------------------------
    // Connectivity
    // ------------------------------------------------------------------

    /**
     * Calls the ERP's health endpoint, retrying with exponential backoff before
     * giving up. The outcome is stored on the connection so the admin screen can
     * show it without re-testing.
     */
    @Transactional
    public ConnectionTestResult testConnection(UUID id, String remoteAddr) {
        ErpConnection connection = require(id);

        String message = null;
        int attempt = 0;

        while (attempt < MAX_ATTEMPTS) {
            attempt++;
            try {
                restClient.get()
                        .uri(connection.getApiEndpoint() + "/health")
                        .headers(headers -> applyAuth(headers, connection))
                        .retrieve()
                        .toBodilessEntity();

                connection.setStatus("OK");
                connection.setLastErrorMessage(null);
                connection.setLastSyncedAt(LocalDateTime.now());
                connectionRepository.save(connection);

                auditLogService.createAuditLog("ERP_CONNECTION_TESTED", id, remoteAddr, "SUCCESS");
                return new ConnectionTestResult(true, attempt,
                        "Connected successfully on attempt " + attempt, LocalDateTime.now());

            } catch (Exception e) {
                message = e.getMessage();
                if (attempt < MAX_ATTEMPTS) {
                    sleepBackoff(attempt);
                }
            }
        }

        connection.setStatus("FAILED");
        connection.setLastErrorMessage(message);
        connectionRepository.save(connection);

        auditLogService.createAuditLog("ERP_CONNECTION_TESTED", id, remoteAddr, "FAILED");
        return new ConnectionTestResult(false, attempt,
                "Could not reach the ERP after " + attempt + " attempts: " + message, LocalDateTime.now());
    }

    /** 1s, then 2s - short enough to stay interactive, long enough to matter. */
    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Duration.ofSeconds((long) Math.pow(2, attempt - 1)).toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Adds whatever credential the connection is configured to use. */
    void applyAuth(org.springframework.http.HttpHeaders headers, ErpConnection connection) {
        String credential = decryptCredential(connection);
        if (credential == null || credential.isBlank()) {
            return;
        }
        if ("BASIC".equalsIgnoreCase(connection.getAuthType())) {
            headers.add("Authorization", "Basic " + credential);
        } else {
            headers.add("X-API-Key", credential);
        }
    }

    String decryptCredential(ErpConnection connection) {
        try {
            return cryptoService.decrypt(connection.getAuthConfig());
        } catch (Exception e) {
            // A credential stored before encryption existed, or with a different
            // key. Treat as absent rather than failing the whole call.
            return null;
        }
    }

    // ------------------------------------------------------------------

    public ErpConnection require(UUID id) {
        return connectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ERP connection not found: " + id));
    }

    private void validate(ErpConnectionRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Connection name is required");
        }
        if (request.apiEndpoint() == null || request.apiEndpoint().isBlank()) {
            throw new IllegalArgumentException("API endpoint is required");
        }
    }

    private String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    ErpConnectionResponse toResponse(ErpConnection c) {
        return new ErpConnectionResponse(
                c.getConnectionId(),
                c.getName(),
                c.getErpType(),
                c.getApiEndpoint(),
                c.getAuthType(),
                c.getAuthConfig() != null && !c.getAuthConfig().isBlank(),
                c.isActiveOrFalse(),
                c.getStatus(),
                c.getLastSyncedAt(),
                c.getLastErrorMessage(),
                c.getCreatedAt()
        );
    }
}
