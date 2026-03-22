package com.dms.rest;

import com.dms.dto.DocumentTitleDTO;
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
    public List<DocumentTitleDTO> searchDocuments(
            @RequestParam(required = false) String query
    ) {
        return searchService.search(query);
    }
}