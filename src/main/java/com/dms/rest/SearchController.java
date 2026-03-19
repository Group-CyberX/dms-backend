package com.dms.rest;

import com.dms.models.Documents;
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
    public List<Documents> searchDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String dateRange,       // Matches frontend 'dateRange'
            @RequestParam(required = false) String signatureStatus, // Added to match UI
            @RequestParam(required = false) String tags            // Optional: if you plan to filter by tags
    ) {
        // Pass the simplified dateRange string to the service layer 
        // where it will be converted to a LocalDateTime.
        return searchService.search(
            query, 
            documentType, 
            status, 
            owner, 
            dateRange, 
            signatureStatus
        );
    }
    

}
