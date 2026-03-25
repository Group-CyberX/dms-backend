package com.dms.rest;

import com.dms.dto.SearchResultDTO;
import com.dms.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<SearchResultDTO> searchDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String signatureStatus,
            @RequestParam(required = false) String dateRange
    ) {
        return searchService.search(query, documentType, status, owner, tags, signatureStatus, dateRange);
    }
}