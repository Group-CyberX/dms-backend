package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dto.MetadataDTO;
import com.dms.dto.SearchResultDTO;
import com.dms.models.Documents;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final DocumentRepository documentRepository;

    public SearchService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<SearchResultDTO> search(String query, String documentType, String status, 
                                         String owner, String tags, String signatureStatus,
                                         String dateRange) {
        // If no filters are provided, return empty list
        if (isEmpty(query) && isEmpty(documentType) && isEmpty(status) && 
            isEmpty(owner) && isEmpty(tags) && isEmpty(signatureStatus) &&
            isEmpty(dateRange)) {
            return List.of();
        }

        List<Documents> results = documentRepository.searchDocumentsWithFilters(query, documentType, status, 
                                                             owner, tags, signatureStatus);
        
        // Filter by date range if provided
        if (!isEmpty(dateRange) && !dateRange.equals("all")) {
            results = filterByDateRange(results, dateRange);
        }

        // Convert Documents to SearchResultDTO
        return results.stream()
                .map(this::convertToSearchResultDTO)
                .collect(Collectors.toList());
    }

    private List<Documents> filterByDateRange(List<Documents> documents, String dateRange) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = calculateStartDate(today, dateRange);
        
        return documents.stream()
                .filter(doc -> {
                    LocalDateTime createdAt = doc.getCreated_at();
                    if (createdAt == null) return false;
                    
                    LocalDate docDate = createdAt.toLocalDate();
                    
                    // Check if document is within the date range
                    return !docDate.isBefore(startDate) && !docDate.isAfter(today);
                })
                .collect(Collectors.toList());
    }

    private LocalDate calculateStartDate(LocalDate today, String dateRange) {
        return switch (dateRange) {
            case "lastWeek" -> today.minus(7, ChronoUnit.DAYS);
            case "lastMonth" -> today.minus(30, ChronoUnit.DAYS);
            case "lastQuarter" -> today.minus(90, ChronoUnit.DAYS);
            case "lastYear" -> today.minus(365, ChronoUnit.DAYS);
            default -> today;
        };
    }

    private SearchResultDTO convertToSearchResultDTO(Documents document) {
        List<MetadataDTO> metadataList = document.getMetadata() != null
                ? document.getMetadata().stream()
                    .map(m -> new MetadataDTO(m.getMetadataId(), m.getKey(), m.getValue()))
                    .collect(Collectors.toList())
                : List.of();
        
        return new SearchResultDTO(document.getDocument_id(), document.getTitle(), metadataList);
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
