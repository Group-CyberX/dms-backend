package com.dms.rest;

import com.dms.dao.ErpTransactionRepository;
import com.dms.dao.IntegrationMappingRepository;
import com.dms.dto.ErpConnectionDTOs.ConnectionTestResult;
import com.dms.dto.ErpConnectionDTOs.ErpConnectionRequest;
import com.dms.dto.ErpConnectionDTOs.ErpConnectionResponse;
import com.dms.dto.ErpConnectionDTOs.SyncResult;
import com.dms.models.DocumentErpLink;
import com.dms.models.ErpTransaction;
import com.dms.models.IntegrationMapping;
import com.dms.security.SecurityUtils;
import com.dms.service.ErpConnectionService;
import com.dms.service.ErpDocumentLinkService;
import com.dms.service.ErpMappingService;
import com.dms.service.ErpSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ERP integration management.
 *
 * Guarded by the ERP permissions that already exist in PermissionCatalog, so an
 * End User cannot reconfigure an integration even though they can see documents
 * linked through it.
 */
@RestController
@RequestMapping("/api/erp")
public class ErpController {

    private final ErpConnectionService connectionService;
    private final ErpSyncService syncService;
    private final ErpMappingService mappingService;
    private final ErpDocumentLinkService linkService;
    private final IntegrationMappingRepository mappingRepository;
    private final ErpTransactionRepository transactionRepository;

    public ErpController(ErpConnectionService connectionService,
                         ErpSyncService syncService,
                         ErpMappingService mappingService,
                         ErpDocumentLinkService linkService,
                         IntegrationMappingRepository mappingRepository,
                         ErpTransactionRepository transactionRepository) {
        this.connectionService = connectionService;
        this.syncService = syncService;
        this.mappingService = mappingService;
        this.linkService = linkService;
        this.mappingRepository = mappingRepository;
        this.transactionRepository = transactionRepository;
    }

    // ---------------- Connections ----------------

    @GetMapping("/connections")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewERPIntegration')")
    public List<ErpConnectionResponse> listConnections() {
        return connectionService.listConnections();
    }

    @GetMapping("/connections/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewERPIntegration')")
    public ErpConnectionResponse getConnection(@PathVariable UUID id) {
        return connectionService.getConnection(id);
    }

    @PostMapping("/connections")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canConfigureERPIntegration')")
    public ResponseEntity<ErpConnectionResponse> createConnection(@RequestBody ErpConnectionRequest request,
                                                                  HttpServletRequest httpRequest) {
        ErpConnectionResponse created = connectionService.create(request, httpRequest.getRemoteAddr());

        // Seed the default field mappings so a new connection is immediately
        // usable and the mapping screen is never empty.
        mappingRepository.saveAll(mappingService.defaultMappings(created.connectionId()));

        return ResponseEntity.ok(created);
    }

    @PutMapping("/connections/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canConfigureERPIntegration')")
    public ErpConnectionResponse updateConnection(@PathVariable UUID id,
                                                  @RequestBody ErpConnectionRequest request,
                                                  HttpServletRequest httpRequest) {
        return connectionService.update(id, request, httpRequest.getRemoteAddr());
    }

    @DeleteMapping("/connections/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canDeleteERPIntegration')")
    public ResponseEntity<Void> deleteConnection(@PathVariable UUID id, HttpServletRequest httpRequest) {
        connectionService.delete(id, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** Connectivity check with retry and backoff (Figure 5.3.10). */
    @PostMapping("/connections/{id}/test")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canConfigureERPIntegration')")
    public ConnectionTestResult testConnection(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return connectionService.testConnection(id, httpRequest.getRemoteAddr());
    }

    // ---------------- Field mapping ----------------

    @GetMapping("/connections/{id}/mappings")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewERPIntegration')")
    public List<IntegrationMapping> listMappings(@PathVariable UUID id) {
        return mappingRepository.findByErpConnectionId(id);
    }

    @PostMapping("/connections/{id}/mappings")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canConfigureERPIntegration')")
    public IntegrationMapping addMapping(@PathVariable UUID id, @RequestBody IntegrationMapping mapping) {
        mapping.setErpConnectionId(id);
        mapping.setMappingId(null);
        return mappingRepository.save(mapping);
    }

    @DeleteMapping("/mappings/{mappingId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canConfigureERPIntegration')")
    public ResponseEntity<Void> deleteMapping(@PathVariable UUID mappingId) {
        mappingRepository.deleteById(mappingId);
        return ResponseEntity.noContent().build();
    }

    // ---------------- Sync ----------------

    @PostMapping("/connections/{id}/sync")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canSyncERPIntegration')")
    public SyncResult sync(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return syncService.sync(id, httpRequest.getRemoteAddr());
    }

    /** Sync history, paged newest-first - never returns the whole table. */
    @GetMapping("/transactions")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewERPIntegration')")
    public Page<ErpTransaction> transactions(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "25") int size) {
        return transactionRepository.findAllByOrderBySyncedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @PostMapping("/transactions/{transactionId}/retry")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canSyncERPIntegration')")
    public ErpTransaction retry(@PathVariable UUID transactionId, HttpServletRequest httpRequest) {
        return syncService.retry(transactionId, httpRequest.getRemoteAddr());
    }

    /**
     * Counts for the console cards.
     *
     * The "today" figures are measured from midnight so the screen answers what
     * the integration has done during this working day, which is the question
     * an administrator is actually asking when they open it.
     */
    @GetMapping("/stats")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewERPIntegration')")
    public Map<String, Object> stats() {
        LocalDateTime midnight = LocalDate.now().atStartOfDay();

        return Map.of(
                "connections", connectionService.listConnections().size(),
                "transactions", transactionRepository.count(),
                "failed", transactionRepository.countBySyncStatus("FAILED"),
                "successfulToday", transactionRepository.countBySyncStatusAndSyncedAtAfter("SUCCESS", midnight),
                "failedToday", transactionRepository.countBySyncStatusAndSyncedAtAfter("FAILED", midnight)
        );
    }

    /**
     * Per-connection counters for the connections table: how many records this
     * connection has pulled, and how many documents ended up attached to them.
     */
    @GetMapping("/connections/{id}/counts")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewERPIntegration')")
    public Map<String, Long> connectionCounts(@PathVariable UUID id) {
        return Map.of(
                "transactions", transactionRepository.countByErpConnectionId(id),
                "linkedDocuments", transactionRepository.countLinkedDocuments(id)
        );
    }

    // ---------------- Document links ----------------

    /** What this document is attached to, for the panel on the document page. */
    @GetMapping("/documents/{documentId}/links")
    public List<Map<String, Object>> documentLinks(@PathVariable UUID documentId) {
        return linkService.linksForDocument(documentId).stream()
                .map(link -> {
                    ErpTransaction tx = transactionRepository.findById(link.getTransactionId()).orElse(null);
                    return Map.<String, Object>of(
                            "linkId", link.getLinkId(),
                            "linkType", link.getLinkType(),
                            "matchedReference", link.getMatchedReference() != null ? link.getMatchedReference() : "",
                            "externalRef", tx != null ? tx.getExternalRef() : "",
                            "transactionType", tx != null && tx.getTransactionType() != null ? tx.getTransactionType() : "",
                            "payload", tx != null && tx.getPayload() != null ? tx.getPayload() : "{}",
                            "createdAt", link.getCreatedAt()
                    );
                })
                .toList();
    }

    /** Manual link, for when auto-matching found nothing. */
    @PostMapping("/documents/{documentId}/links")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditDocument')")
    public DocumentErpLink linkManually(@PathVariable UUID documentId, @RequestBody Map<String, String> body) {
        UUID transactionId = UUID.fromString(body.get("transactionId"));
        return linkService.linkManually(documentId, transactionId, SecurityUtils.currentUserId());
    }

    @DeleteMapping("/links/{linkId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditDocument')")
    public ResponseEntity<Void> unlink(@PathVariable UUID linkId) {
        linkService.unlink(linkId);
        return ResponseEntity.noContent().build();
    }
}
