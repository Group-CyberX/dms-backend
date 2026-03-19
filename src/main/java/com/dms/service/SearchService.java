package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.models.Documents;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SearchService {

    private final DocumentRepository documentRepository;

    public SearchService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<Documents> search(
            String query,
            String documentType,
            String status,
            String owner,
            String dateRange,        // Received from Controller
            String signatureStatus   // Added to match UI
    ) {
        LocalDateTime sinceDate = calculateSinceDate(dateRange);

        // We pass the calculated timestamp and the new signatureStatus to the DAO
        return documentRepository.searchDocuments(
                query, 
                documentType, 
                status, 
                owner, 
                signatureStatus, 
                sinceDate
        );
    }

    /**
     * Converts UI dropdown values into a starting LocalDateTime.
     * Everything from 'sinceDate' to 'now' will be included in the search.
     */
    private LocalDateTime calculateSinceDate(String dateRange) {
        if (dateRange == null || dateRange.isEmpty() || dateRange.equalsIgnoreCase("any")) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        return switch (dateRange.toLowerCase()) {
            case "last_24h" -> now.minusHours(24);
            case "last_7d"  -> now.minusDays(7);
            case "last_30d" -> now.minusDays(30);
            case "last_year" -> now.minusYears(1);
            default -> null;
        };
    }
}