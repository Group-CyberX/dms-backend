package com.dms.rest;

import com.dms.models.Document;
import com.dms.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<Document> searchDocuments(@RequestParam String query) {
        return searchService.search(query);
    }
}