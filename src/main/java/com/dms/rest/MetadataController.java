package com.dms.rest;

import com.dms.dto.MetadataDTO;
import com.dms.models.DocumentMetadata;
import com.dms.service.MetadataService;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @PostMapping
    public DocumentMetadata addMetadata(@RequestBody MetadataDTO dto) {

        return metadataService.addMetadata(dto);
    }

    @GetMapping("/{documentId}")
    public List<DocumentMetadata> getMetadata(@PathVariable UUID documentId) {

        return metadataService.getMetadataByDocument(documentId);
    }

    @PutMapping("/{metadataId}")
    public DocumentMetadata updateMetadata(@PathVariable UUID metadataId,
                                           @RequestBody MetadataDTO dto) {

        return metadataService.updateMetadata(metadataId, dto);
    }

    @DeleteMapping("/{metadataId}")
    public void deleteMetadata(@PathVariable UUID metadataId) {

        metadataService.deleteMetadata(metadataId);
    }
}