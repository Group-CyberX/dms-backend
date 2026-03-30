package com.dms.rest;

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

    // Universal search - searches title, metadata key, and metadata value
    @GetMapping
    public ResponseEntity<List<SearchResponseDTO>> search(@RequestParam String query) {
        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<SearchResponseDTO> results = searchService.universalSearch(query);
        return ResponseEntity.ok(results);
    }
}