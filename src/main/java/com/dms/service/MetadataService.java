package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dao.MetadataRepository;
import com.dms.dto.MetadataDTO;
import com.dms.models.Documents;
import com.dms.models.DocumentMetadata;

import org.springframework.stereotype.Service;



import java.util.UUID;

@Service
public class MetadataService {

    private final MetadataRepository metadataRepository;
    private final DocumentRepository documentRepository;

    public MetadataService(MetadataRepository metadataRepository,
                           DocumentRepository documentRepository) {
        this.metadataRepository = metadataRepository;
        this.documentRepository = documentRepository;
    }

    public DocumentMetadata addMetadata(MetadataDTO dto) {

        Documents document = documentRepository.findById(dto.getDocumentId())
                .orElseThrow(() -> new RuntimeException("Document not found"));

        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setKey(dto.getKey());
        metadata.setValue(dto.getValue());
        metadata.setDocument(document);

        return metadataRepository.save(metadata);
    }

    

    public DocumentMetadata updateMetadata(UUID metadataId, MetadataDTO dto) {

        DocumentMetadata metadata = metadataRepository.findById(metadataId)
                .orElseThrow(() -> new RuntimeException("Metadata not found"));

        metadata.setKey(dto.getKey());
        metadata.setValue(dto.getValue());

        return metadataRepository.save(metadata);
    }

    public void deleteMetadata(UUID metadataId) {

        metadataRepository.deleteById(metadataId);
    }

    


}