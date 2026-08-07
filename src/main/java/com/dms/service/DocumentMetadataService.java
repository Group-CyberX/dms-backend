package com.dms.service;

import com.dms.models.DocumentMetadata;
import com.dms.models.Documents;
import com.dms.dao.DocumentMetadataRepository;
import com.dms.dao.DocumentRepository;
import com.dms.dto.MetadataRequestDTO;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentMetadataService {

    private final DocumentMetadataRepository metadataRepository;
    private final DocumentRepository documentRepository;

    public DocumentMetadataService(DocumentMetadataRepository metadataRepository, DocumentRepository documentRepository) {
        this.metadataRepository = metadataRepository;
        this.documentRepository = documentRepository;
    }

    // Add metadata to a document or update it if the key already exists
    public DocumentMetadata addMetadata(UUID documentId, String key, String value) {

    Documents document = documentRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Document not found"));

    Optional<DocumentMetadata> existing =
            metadataRepository.findByDocument_document_idAndKey(documentId, key);

    if (existing.isPresent()) {
        DocumentMetadata meta = existing.get();
        meta.setValue(value);
        return metadataRepository.save(meta);
    }

    DocumentMetadata metadata = new DocumentMetadata(document, key, value);
    return metadataRepository.save(metadata);
}
    // Get all metadata entries for a document
    public List<DocumentMetadata> getDocumentMetadata(UUID documentId) {
        return metadataRepository.findByDocument_document_id(documentId);
    }

    // Get a specific metadata entry by key
    public Optional<DocumentMetadata> getMetadataByKey(UUID documentId, String key) {
        return metadataRepository.findByDocument_document_idAndKey(documentId, key);
    }

    // Update a metadata value by key
    public DocumentMetadata updateMetadata(UUID documentId, String key, String newValue) {
        Optional<DocumentMetadata> existing = metadataRepository.findByDocument_document_idAndKey(documentId, key);
        
        if (existing.isPresent()) {
            DocumentMetadata metadata = existing.get();
            metadata.setValue(newValue);
            return metadataRepository.save(metadata);
        }
        
        throw new RuntimeException("Metadata not found for document: " + documentId + ", key: " + key);
    }

    // Delete a metadata entry by key
    public void deleteMetadata(UUID documentId, String key) {
        metadataRepository.deleteByDocument_document_idAndKey(documentId, key);
    }

    // Delete all metadata entries for a document
    public void deleteAllMetadata(UUID documentId) {
    List<DocumentMetadata> metadata = metadataRepository.findByDocument_document_id(documentId);
    metadataRepository.deleteAll(metadata);
}

// Get metadata by metadata ID
public Optional<DocumentMetadata> getMetadataById(UUID metadataId) {
    return metadataRepository.findById(metadataId);
}

// Update metadata value by metadata ID
public DocumentMetadata updateMetadataById(UUID metadataId, String newValue) {
    DocumentMetadata metadata = metadataRepository.findById(metadataId)
            .orElseThrow(() -> new RuntimeException("Metadata not found: " + metadataId));
    metadata.setValue(newValue);
    return metadataRepository.save(metadata);
}

// Delete metadata by metadata ID
public void deleteMetadataById(UUID metadataId) {
    metadataRepository.deleteById(metadataId);
}

// Add multiple metadata entries with upsert behavior
public List<DocumentMetadata> addMultipleMetadata(
        UUID documentId,
        List<MetadataRequestDTO> requests) {

    Documents document = documentRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Document not found"));

    List<DocumentMetadata> result = new ArrayList<>();

    for (MetadataRequestDTO dto : requests) {

        // 🔥 Upsert logic (avoid duplicates)
        Optional<DocumentMetadata> existing =
                metadataRepository.findByDocument_document_idAndKey(documentId, dto.getKey());

        if (existing.isPresent()) {
            DocumentMetadata meta = existing.get();
            meta.setValue(dto.getValue());
            result.add(metadataRepository.save(meta));
        } else {
            DocumentMetadata meta = new DocumentMetadata(document, dto.getKey(), dto.getValue());
            result.add(metadataRepository.save(meta));
        }
    }

    return result;
}
}