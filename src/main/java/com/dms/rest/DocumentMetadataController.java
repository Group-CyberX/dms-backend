package com.dms.rest;

import com.dms.models.DocumentMetadata;
import com.dms.dto.MetadataRequestDTO;
import com.dms.dto.MetadataResponseDTO;
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

    public DocumentMetadataController(DocumentMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    // ====== BY METADATA ID ======

    // GET: Get metadata by metadataId
    @GetMapping("/{metadataId}")
    public ResponseEntity<MetadataResponseDTO> getMetadataById(@PathVariable UUID metadataId) {
        return metadataService.getMetadataById(metadataId)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT: Update metadata by metadataId
    @PutMapping("/{metadataId}")
    public ResponseEntity<MetadataResponseDTO> updateMetadataById(
            @PathVariable UUID metadataId,
            @RequestBody MetadataRequestDTO request) {
        DocumentMetadata updated = metadataService.updateMetadataById(metadataId, request.getValue());
        return ResponseEntity.ok(convertToDTO(updated));
    }

    // DELETE: Delete metadata by metadataId
    @DeleteMapping("/{metadataId}")
    public ResponseEntity<Void> deleteMetadataById(@PathVariable UUID metadataId) {
        metadataService.deleteMetadataById(metadataId);
        return ResponseEntity.noContent().build();
    }

    // ====== BY DOCUMENT ID ======

    // POST: Add metadata to document
    @PostMapping("/document/{documentId}")
    public ResponseEntity<MetadataResponseDTO> addMetadata(
            @PathVariable UUID documentId,
            @RequestBody MetadataRequestDTO request) {
        DocumentMetadata metadata = metadataService.addMetadata(
            documentId,
            request.getKey(),
            request.getValue()
        );
        return ResponseEntity.ok(convertToDTO(metadata));
    }

    // GET: Get all metadata for document
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

    // GET: Get specific metadata by document and key
    @GetMapping("/document/{documentId}/{key}")
    public ResponseEntity<MetadataResponseDTO> getMetadataByKey(
            @PathVariable UUID documentId,
            @PathVariable String key) {
        return metadataService.getMetadataByKey(documentId, key)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT: Update metadata by document and key
    @PutMapping("/document/{documentId}/{key}")
    public ResponseEntity<MetadataResponseDTO> updateMetadata(
            @PathVariable UUID documentId,
            @PathVariable String key,
            @RequestBody MetadataRequestDTO request) {
        DocumentMetadata updated = metadataService.updateMetadata(
            documentId,
            key,
            request.getValue()
        );
        return ResponseEntity.ok(convertToDTO(updated));
    }

    // DELETE: Delete metadata by document and key
    @DeleteMapping("/document/{documentId}/{key}")
    public ResponseEntity<Void> deleteMetadata(
            @PathVariable UUID documentId,
            @PathVariable String key) {
        metadataService.deleteMetadata(documentId, key);
        return ResponseEntity.noContent().build();
    }

    // DELETE: Delete all metadata for document
    @DeleteMapping("/document/{documentId}")
    public ResponseEntity<Void> deleteAllMetadata(@PathVariable UUID documentId) {
        metadataService.deleteAllMetadata(documentId);
        return ResponseEntity.noContent().build();
    }

    // Helper method to convert model to DTO
    private MetadataResponseDTO convertToDTO(DocumentMetadata metadata) {
        return new MetadataResponseDTO(
            metadata.getMetadataId(),
            metadata.getDocument().getDocument_id(),
            metadata.getKey(),
            metadata.getValue()
        );
    }
}