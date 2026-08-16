package com.dms.rest;

import com.dms.models.DocumentMetadata;
import com.dms.dto.MetadataRequestDTO;
import com.dms.dto.MetadataResponseDTO;
import com.dms.security.SecurityUtils;
import com.dms.service.DocumentLockService;
import com.dms.service.DocumentMetadataService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/metadata")
public class DocumentMetadataController {

    private final DocumentMetadataService metadataService;
    private final DocumentLockService documentLockService;

    public DocumentMetadataController(DocumentMetadataService metadataService,
                                      DocumentLockService documentLockService) {
        this.metadataService = metadataService;
        this.documentLockService = documentLockService;
    }

    /** Metadata is document content, so it follows the same edit lock as the file. */
    private void assertNotLockedByOther(UUID documentId) {
        documentLockService.assertCanMutate(documentId, SecurityUtils.currentUserId());
    }

    // =========================
    // SINGLE METADATA
    // =========================

    @PostMapping("/document/{documentId}")
    public ResponseEntity<MetadataResponseDTO> addMetadata(
            @PathVariable UUID documentId,
            @RequestBody MetadataRequestDTO request) {

        if (request.getKey() == null || request.getValue() == null) {
            return ResponseEntity.badRequest().build();
        }

        assertNotLockedByOther(documentId);

        DocumentMetadata metadata =
                metadataService.addMetadata(documentId, request.getKey(), request.getValue());

        return ResponseEntity.ok(convertToDTO(metadata));
    }

    // =========================
    // 🔥 BULK INSERT METADATA
    // =========================

    @PostMapping("/document/{documentId}/bulk")
    public ResponseEntity<List<MetadataResponseDTO>> addBulkMetadata(
            @PathVariable UUID documentId,
            @RequestBody List<MetadataRequestDTO> requests) {

        if (requests == null || requests.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        assertNotLockedByOther(documentId);

        List<DocumentMetadata> metadataList =
                metadataService.addMultipleMetadata(documentId, requests);

        List<MetadataResponseDTO> response = metadataList.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // =========================
    // GET ALL METADATA
    // =========================

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<MetadataResponseDTO>> getDocumentMetadata(
            @PathVariable UUID documentId) {

        List<MetadataResponseDTO> metadata = metadataService
                .getDocumentMetadata(documentId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(metadata);
    }

    // =========================
    // UPDATE BY KEY
    // =========================

    @PutMapping("/document/{documentId}/{key}")
    public ResponseEntity<MetadataResponseDTO> updateMetadata(
            @PathVariable UUID documentId,
            @PathVariable String key,
            @RequestBody MetadataRequestDTO request) {

        assertNotLockedByOther(documentId);

        DocumentMetadata updated =
                metadataService.updateMetadata(documentId, key, request.getValue());

        return ResponseEntity.ok(convertToDTO(updated));
    }

    // =========================
    // DELETE BY KEY
    // =========================

    @DeleteMapping("/document/{documentId}/{key}")
    public ResponseEntity<Void> deleteMetadata(
            @PathVariable UUID documentId,
            @PathVariable String key) {

        assertNotLockedByOther(documentId);

        metadataService.deleteMetadata(documentId, key);
        return ResponseEntity.noContent().build();
    }

    // =========================
    // DELETE ALL
    // =========================

    @DeleteMapping("/document/{documentId}")
    public ResponseEntity<Void> deleteAllMetadata(@PathVariable UUID documentId) {
        assertNotLockedByOther(documentId);

        metadataService.deleteAllMetadata(documentId);
        return ResponseEntity.noContent().build();
    }

    // =========================
    // HELPER DTO CONVERTER
    // =========================

    private MetadataResponseDTO convertToDTO(DocumentMetadata metadata) {
        return new MetadataResponseDTO(
                metadata.getMetadataId(),
                metadata.getDocument().getDocument_id(),
                metadata.getKey(),
                metadata.getValue()
        );
    }
}