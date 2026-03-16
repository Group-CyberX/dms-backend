package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.models.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private final DocumentRepository documentRepository;

    public SearchService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<Document> search(String query) {
        return documentRepository.searchDocuments(query);
    }
}