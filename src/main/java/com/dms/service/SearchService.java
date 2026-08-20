package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dto.AdvancedSearchRequestDTO;
import com.dms.dto.SearchResponseDTO;
import com.dms.models.Documents;
import com.dms.models.DocumentMetadata;
import com.dms.models.Tag;
import com.dms.dao.UserRepository;
import com.dms.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.dms.dao.WorkflowInstanceRepository;
import com.dms.models.WorkflowInstance;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final DocumentRepository documentRepository;
    private final TagService tagService;
    private final UserRepository userRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;

    public SearchService(DocumentRepository documentRepository, TagService tagService, UserRepository userRepository, WorkflowInstanceRepository workflowInstanceRepository) {
        this.documentRepository = documentRepository;
        this.tagService = tagService;
        this.userRepository = userRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
    }

    private String getDocumentWorkflowStatus(UUID documentId) {
        List<WorkflowInstance> workflows = workflowInstanceRepository.findByDocumentId(documentId.toString());
        if (workflows != null && !workflows.isEmpty()) {
            return workflows.stream()
                .reduce((latest, current) -> (current.getId() != null && latest.getId() != null && current.getId() > latest.getId()) ? current : latest)
                .map(WorkflowInstance::getStatus)
                .orElse(null);
        }
        return null;
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
            
            // Override status with actual workflow status if available
            String workflowStatus = getDocumentWorkflowStatus(doc.getDocument_id());
            if (workflowStatus != null && !workflowStatus.isEmpty()) {
                dto.setStatus(workflowStatus);
            } else {
                dto.setStatus(metaMap.getOrDefault("status", "Unknown"));
            }
            
            dto.setDescription(metaMap.getOrDefault("description", ""));
        } else {
            String workflowStatus = getDocumentWorkflowStatus(doc.getDocument_id());
            dto.setStatus(workflowStatus != null ? workflowStatus : "Unknown");
            dto.setDescription("");
        }
        
        // Fetch and map owner from user table
        if (doc.getOwner_id() != null) {
            userRepository.findById(doc.getOwner_id()).ifPresentOrElse(user -> {
                Map<String, String> ownerData = new HashMap<>();
                ownerData.put("id", user.getUserId().toString());
                ownerData.put("name", user.getUsername());
                ownerData.put("email", user.getEmail());
                dto.setOwner(ownerData);
            }, () -> {
                dto.setOwner("Unknown");
            });
        } else {
            dto.setOwner("Unknown");
        }

        // Fetch and map tags
        List<Tag> tags = tagService.getTagsForDocument(doc.getDocument_id());
        dto.setTags(tags.stream().map(Tag::getTag_name).collect(Collectors.toList()));
        
        return dto;
    }

    /**
     * Universal search across title, metadata, and tags
     */
    public List<SearchResponseDTO> universalSearch(String searchTerm, UUID ownerId) {
        List<Documents> results = documentRepository.universalSearchIncludingTags(
                searchTerm, ownerId == null ? null : ownerId.toString());

        return results.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Search documents by tag name
     */
    public List<SearchResponseDTO> searchByTag(String tagName, UUID ownerId) {
        List<Documents> results = documentRepository.searchByTag(
                tagName, ownerId == null ? null : ownerId.toString());

        return results.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Advanced Search with multiple metadata filters
     */
    public List<SearchResponseDTO> advancedSearch(AdvancedSearchRequestDTO filters, UUID ownerId) {
        return advancedSearch(filters, 0, Integer.MAX_VALUE, ownerId).getContent();
    }

    /**
     * Advanced search, returning one page.
     *
     * Two costs were removed here. The status filter used to run a workflow
     * query for every candidate document; now the latest status for the whole
     * candidate set is read once. And only the rows on the requested page are
     * turned into DTOs, so the per-row owner and tag lookups happen ten times
     * instead of once per matching document.
     *
     * The metadata, owner and tag filters still run in Java, because they read
     * joined metadata that the text query does not return. What changed is that
     * they now run over a candidate set the database has already narrowed, and
     * the response is bounded regardless of how many documents match.
     */
    public Page<SearchResponseDTO> advancedSearch(AdvancedSearchRequestDTO filters, int page, int size,
                                                  UUID ownerId) {
        List<Documents> docs;

        // Scoped by the database. Without this an end user searching saw every
        // document in the system, including ones they cannot open.
        String owner = ownerId == null ? null : ownerId.toString();

        // 1. Initial filtered set using text query if present
        if (filters.getQuery() != null && !filters.getQuery().trim().isEmpty()) {
            docs = documentRepository.universalSearchIncludingTags(filters.getQuery().trim(), owner);
        } else {
            docs = ownerId == null
                    ? documentRepository.findAllActive()
                    : documentRepository.findAllActiveByOwner(ownerId);
        }

        // One query for the workflow status of every candidate, instead of one
        // query per candidate.
        Map<UUID, String> statusByDocument = latestWorkflowStatuses(docs);

        // 2. Filtering for advanced metadata options
        List<Documents> matches = docs.stream()
                .filter(doc -> matchesMetadata(doc, "documentType", filters.getDocumentType()))
                .filter(doc -> matchesStatus(doc, filters.getStatus(), statusByDocument))
                .filter(doc -> matchesMetadata(doc, "signatureStatus", filters.getSignatureStatus()))
                .filter(doc -> matchesOwner(doc, filters.getOwner()))
                .filter(doc -> matchesDateRange(doc, filters.getDateRange()))
                .filter(doc -> matchesTags(doc, filters.getTags()))
                .toList();

        int safeSize = Math.max(size, 1);
        int safePage = Math.max(page, 0);
        int from = Math.min((int) Math.min((long) safePage * safeSize, Integer.MAX_VALUE), matches.size());
        int to = (safeSize == Integer.MAX_VALUE) ? matches.size() : Math.min(from + safeSize, matches.size());

        List<SearchResponseDTO> content = matches.subList(from, to).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(safePage, safeSize == Integer.MAX_VALUE ? Math.max(matches.size(), 1) : safeSize);
        return new PageImpl<>(content, pageable, matches.size());
    }

    /** Latest workflow status per document, read in a single query. */
    private Map<UUID, String> latestWorkflowStatuses(List<Documents> docs) {
        if (docs.isEmpty()) {
            return Map.of();
        }

        List<String> documentIds = docs.stream()
                .map(d -> d.getDocument_id().toString())
                .toList();

        Map<UUID, WorkflowInstance> latest = new HashMap<>();
        for (WorkflowInstance instance : workflowInstanceRepository.findByDocumentIdIn(documentIds)) {
            UUID documentId;
            try {
                documentId = UUID.fromString(instance.getDocumentId());
            } catch (IllegalArgumentException | NullPointerException ex) {
                continue;   // a workflow row with a malformed document id
            }
            WorkflowInstance current = latest.get(documentId);
            if (current == null || (instance.getId() != null && current.getId() != null
                    && instance.getId() > current.getId())) {
                latest.put(documentId, instance);
            }
        }

        Map<UUID, String> statuses = new HashMap<>();
        latest.forEach((documentId, instance) -> statuses.put(documentId, instance.getStatus()));
        return statuses;
    }

    /** Status filter, reading the pre-loaded workflow statuses. */
    private boolean matchesStatus(Documents doc, String filterVal, Map<UUID, String> statusByDocument) {
        if (filterVal == null || filterVal.trim().isEmpty()
                || filterVal.startsWith("All") || filterVal.startsWith("Any")) {
            return true;
        }

        String actualStatus = statusByDocument.get(doc.getDocument_id());
        if (actualStatus == null && doc.getMetadata() != null) {
            actualStatus = doc.getMetadata().stream()
                    .filter(m -> m.getKey().equalsIgnoreCase("status"))
                    .map(DocumentMetadata::getValue)
                    .findFirst().orElse(null);
        }
        return actualStatus != null && actualStatus.equalsIgnoreCase(filterVal);
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

    private boolean matchesOwner(Documents doc, String filterValue) {
        if (filterValue == null || filterValue.trim().isEmpty() || filterValue.startsWith("Any")) {
            return true;
        }
        if (doc.getOwner_id() == null) {
            return filterValue.equalsIgnoreCase("Unknown");
        }
        User user = userRepository.findById(doc.getOwner_id()).orElse(null);
        if (user == null) {
            return filterValue.equalsIgnoreCase("Unknown");
        }
        return user.getUsername().equalsIgnoreCase(filterValue);
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