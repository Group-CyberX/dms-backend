package com.dms.rest;

import com.dms.dto.AdvancedSearchRequestDTO;
import com.dms.dto.SearchResponseDTO;
import com.dms.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Universal search - searches title, metadata, and tags
     * Usage: GET /api/search?query=searchTerm
     */
    @GetMapping
    public ResponseEntity<List<SearchResponseDTO>> search(@RequestParam String query) {
        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<SearchResponseDTO> results = searchService.universalSearch(query);
        return ResponseEntity.ok(results);
    }

    /**
     * Search documents by tags only
     * Usage: GET /api/search/tags?tag=tagName
     */
    @GetMapping("/tags")
    public ResponseEntity<List<SearchResponseDTO>> searchByTag(@RequestParam String tag) {
        if (tag == null || tag.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<SearchResponseDTO> results = searchService.searchByTag(tag);
        return ResponseEntity.ok(results);
    }

    /**
     * Advanced Search with multiple metadata filters
     * Usage: POST /api/search/advanced with AdvancedSearchRequestDTO body
     */
    @PostMapping("/advanced")
    public ResponseEntity<List<SearchResponseDTO>> advancedSearch(@RequestBody AdvancedSearchRequestDTO filters) {
        List<SearchResponseDTO> results = searchService.advancedSearch(filters);
        return ResponseEntity.ok(results);
    }
}