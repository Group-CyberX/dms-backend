package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dto.SearchResponseDTO;
import com.dms.models.Documents;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final DocumentRepository documentRepository;

    public SearchService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<SearchResponseDTO> universalSearch(String searchTerm) {
        List<Documents> results = documentRepository.universalSearch(searchTerm);

        return results.stream()
                .map(doc -> new SearchResponseDTO(doc.getDocument_id(), doc.getTitle()))
                .collect(Collectors.toList());
    }
}