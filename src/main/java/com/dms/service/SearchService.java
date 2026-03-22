package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dto.DocumentTitleDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private final DocumentRepository documentRepository;

    public SearchService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<DocumentTitleDTO> search(String query) {

        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        return documentRepository.searchDocuments(query);
    }
}