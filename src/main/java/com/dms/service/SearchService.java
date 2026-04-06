package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dto.AdvancedSearchRequestDTO;
import com.dms.dto.SearchResponseDTO;
import com.dms.models.Documents;
import com.dms.models.DocumentMetadata;
import com.dms.models.Tag;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final DocumentRepository documentRepository;
    private final TagService tagService;

    public SearchService(DocumentRepository documentRepository, TagService tagService) {
        this.documentRepository = documentRepository;
        this.tagService = tagService;
    }

    private SearchResponseDTO mapToDTO(Documents doc) {
        SearchResponseDTO dto = new SearchResponseDTO(doc.getDocument_id(), doc.getTitle());
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy");
        if (doc.getCreated_at() != null) {
            dto.setCreatedAt(doc.getCreated_at().format(formatter));
        }

        // Map metadata into DTO fields
        if (doc.getMetadata() != null) {
            Map<String, String> metaMap = doc.getMetadata().stream()
                .collect(Collectors.toMap(
                    m -> m.getKey().toLowerCase(), 
                    DocumentMetadata::getValue,
                    (v1, v2) -> v1 // In case of duplicate keys
                ));
            dto.setMetadata(metaMap);
            dto.setOwner(metaMap.getOrDefault("owner", "Unknown"));
            dto.setStatus(metaMap.getOrDefault("status", "Unknown"));
            dto.setDescription(metaMap.getOrDefault("description", ""));
        } else {
            dto.setOwner("Unknown");
            dto.setStatus("Unknown");
            dto.setDescription("");
        }

        // Fetch and map tags
        List<Tag> tags = tagService.getTagsForDocument(doc.getDocument_id());
        dto.setTags(tags.stream().map(Tag::getTag_name).collect(Collectors.toList()));
        
        return dto;
    }

    /**
     * Universal search across title, metadata, and tags
     */
    public List<SearchResponseDTO> universalSearch(String searchTerm) {
        List<Documents> results = documentRepository.universalSearchIncludingTags(searchTerm);

        return results.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search documents by tag name
     */
    public List<SearchResponseDTO> searchByTag(String tagName) {
        List<Documents> results = documentRepository.searchByTag(tagName);

        return results.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Advanced Search with multiple metadata filters
     */
    public List<SearchResponseDTO> advancedSearch(AdvancedSearchRequestDTO filters) {
        List<Documents> docs;
        
        // 1. Initial filtered set using text query if present
        if (filters.getQuery() != null && !filters.getQuery().trim().isEmpty()) {
            docs = documentRepository.universalSearchIncludingTags(filters.getQuery().trim());
        } else {
            docs = documentRepository.findAllActive();
        }

        // 2. In-memory filtering for advanced metadata options
        return docs.stream()
                .filter(doc -> matchesMetadata(doc, "documentType", filters.getDocumentType()))
                .filter(doc -> matchesMetadata(doc, "status", filters.getStatus()))
                .filter(doc -> matchesMetadata(doc, "signatureStatus", filters.getSignatureStatus()))
                .filter(doc -> matchesMetadata(doc, "owner", filters.getOwner()))
                .filter(doc -> matchesDateRange(doc, filters.getDateRange()))
                .filter(doc -> matchesTags(doc, filters.getTags()))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private boolean matchesMetadata(Documents doc, String metaKey, String filterValue) {
        // Ignore empty, default, or "All types" dropdown placeholders
        if (filterValue == null || filterValue.trim().isEmpty() || filterValue.startsWith("All") || filterValue.startsWith("Any")) {
            return true;
        }
        if (doc.getMetadata() == null || doc.getMetadata().isEmpty()) {
            return false;
        }
        return doc.getMetadata().stream()
                .anyMatch(m -> m.getKey().equalsIgnoreCase(metaKey) && 
                               m.getValue().equalsIgnoreCase(filterValue));
    }

    private boolean matchesDateRange(Documents doc, String dateRange) {
        if (dateRange == null || dateRange.trim().isEmpty() || dateRange.equalsIgnoreCase("Any time") || dateRange.equalsIgnoreCase("none")) {
            return true;
        }
        LocalDateTime docDate = doc.getCreated_at();
        if (docDate == null) return true;

        LocalDateTime now = LocalDateTime.now();
        switch (dateRange) {
            case "lastWeek": return docDate.isAfter(now.minusWeeks(1));
            case "lastMonth": return docDate.isAfter(now.minusMonths(1));
            case "lastQuarter": return docDate.isAfter(now.minusMonths(3));
            case "lastYear": return docDate.isAfter(now.minusYears(1));
            default: return true;
        }
    }

    private boolean matchesTags(Documents doc, String tagsFilter) {
        if (tagsFilter == null || tagsFilter.trim().isEmpty()) {
            return true;
        }
        List<Tag> docTags = tagService.getTagsForDocument(doc.getDocument_id());
        List<String> docTagNames = docTags.stream()
                .map(t -> t.getTag_name().toLowerCase())
                .collect(Collectors.toList());

        List<String> filterTags = Arrays.stream(tagsFilter.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());

        // Returns true if ANY of the requested filter tags match the document's tags
        return filterTags.stream().anyMatch(docTagNames::contains);
    }
}