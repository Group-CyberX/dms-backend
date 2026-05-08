package com.dms.rest;

import com.dms.dao.SearchLogRepository;
import com.dms.dto.AdvancedSearchRequestDTO;
import com.dms.dto.SearchHistoryResponseDTO;
import com.dms.dto.SearchLogRequestDTO;
import com.dms.dto.SearchResponseDTO;
import com.dms.models.SearchLog;
import com.dms.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final SearchLogRepository searchLogRepository;

    public SearchController(SearchService searchService, SearchLogRepository searchLogRepository) {
        this.searchService = searchService;
        this.searchLogRepository = searchLogRepository;
    }

    /**
     * Universal search - searches title, metadata, and tags
     * Usage: GET /api/search?query=searchTerm
     */
    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean paged) {
        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<SearchResponseDTO> results = searchService.universalSearch(query);

        if (Boolean.TRUE.equals(paged)) {
            int start = Math.min(page * size, results.size());
            int end = Math.min((page + 1) * size, results.size());
            List<SearchResponseDTO> pagedContent = results.subList(start, end);
            return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(
                    pagedContent, 
                    org.springframework.data.domain.PageRequest.of(page, size), 
                    results.size()
            ));
        }

        return ResponseEntity.ok(results);
    }

    /**
     * Search documents by tags only
     * Usage: GET /api/search/tags?tag=tagName
     */
    @GetMapping("/tags")
    public ResponseEntity<?> searchByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean paged) {
        if (tag == null || tag.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<SearchResponseDTO> results = searchService.searchByTag(tag);

        if (Boolean.TRUE.equals(paged)) {
            int start = Math.min(page * size, results.size());
            int end = Math.min((page + 1) * size, results.size());
            List<SearchResponseDTO> pagedContent = results.subList(start, end);
            return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(
                    pagedContent, 
                    org.springframework.data.domain.PageRequest.of(page, size), 
                    results.size()
            ));
        }

        return ResponseEntity.ok(results);
    }

    /**
     * Advanced Search with multiple metadata filters
     * Usage: POST /api/search/advanced with AdvancedSearchRequestDTO body
     */
    @PostMapping("/advanced")
    public ResponseEntity<?> advancedSearch(
            @RequestBody AdvancedSearchRequestDTO filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean paged) {
                
        List<SearchResponseDTO> results = searchService.advancedSearch(filters);
        
        if (Boolean.TRUE.equals(paged)) {
            int start = Math.min(page * size, results.size());
            int end = Math.min((page + 1) * size, results.size());
            List<SearchResponseDTO> pagedContent = results.subList(start, end);
            return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(
                    pagedContent, 
                    org.springframework.data.domain.PageRequest.of(page, size), 
                    results.size()
            ));
        }
        
        return ResponseEntity.ok(results);
    }

    /**
     * Log a clicked search result
     * Usage: POST /api/search/log
     */
    @PostMapping("/log")
    public ResponseEntity<Void> logSearchClick(@RequestBody SearchLogRequestDTO logRequest) {
        if (logRequest.getQuery() == null || logRequest.getQuery().trim().isEmpty() || logRequest.getClickedDocId() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        SearchLog log = new SearchLog(
                null, // userId kept null as default for now
                logRequest.getQuery(),
                logRequest.getClickedDocId()
        );
        searchLogRepository.save(log);

        return ResponseEntity.ok().build();
    }

    /**
     * Get Search History (recently clicked items)
     * Usage: GET /api/search/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<SearchHistoryResponseDTO>> getSearchHistory() {
        return ResponseEntity.ok(searchLogRepository.findSearchHistory());
    }

    /**
     * Clear Search History
     * Usage: DELETE /api/search/history
     */
    @DeleteMapping("/history")
    public ResponseEntity<Void> clearSearchHistory() {
        searchLogRepository.deleteAll();
        return ResponseEntity.noContent().build();
    }
}