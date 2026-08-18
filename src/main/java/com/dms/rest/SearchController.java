package com.dms.rest;

import com.dms.dao.SearchLogRepository;
import com.dms.dto.AdvancedSearchRequestDTO;
import com.dms.dto.SearchHistoryResponseDTO;
import com.dms.dto.SearchLogRequestDTO;
import com.dms.dto.SearchResponseDTO;
import com.dms.models.SearchLog;
import com.dms.security.SecurityUtils;
import com.dms.service.PermissionService;
import com.dms.service.SearchService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final SearchLogRepository searchLogRepository;
    private final com.dms.dao.UserRepository userRepository;
    private final PermissionService permissionService;

    public SearchController(SearchService searchService, SearchLogRepository searchLogRepository, com.dms.dao.UserRepository userRepository, PermissionService permissionService) {
        this.searchService = searchService;
        this.searchLogRepository = searchLogRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    /**
     * How far a search may reach. canSearchAllDocuments is what opens it to the
     * whole library; without it a search only ever returns the caller's own
     * documents, which is the same rule the document list follows.
     */
    private UUID searchScopeOwnerId(Authentication auth) {
        return permissionService.hasPermission(auth, "canSearchAllDocuments")
                ? null
                : SecurityUtils.currentUserId();
    }

    /**
     * Universal search - searches title, metadata, and tags
     * Usage: GET /api/search?query=searchTerm
     */
    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewSearch')")
    public ResponseEntity<List<SearchResponseDTO>> search(@RequestParam String query,
                                                          Authentication auth) {
        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<SearchResponseDTO> results = searchService.universalSearch(query, searchScopeOwnerId(auth));
        return ResponseEntity.ok(results);
    }

    /**
     * Search documents by tags only
     * Usage: GET /api/search/tags?tag=tagName
     */
    @GetMapping("/tags")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewSearch')")
    public ResponseEntity<List<SearchResponseDTO>> searchByTag(@RequestParam String tag,
                                                               Authentication auth) {
        if (tag == null || tag.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<SearchResponseDTO> results = searchService.searchByTag(tag, searchScopeOwnerId(auth));
        return ResponseEntity.ok(results);
    }

    /**
     * Advanced Search with multiple metadata filters, one page at a time.
     *
     * Returns a page rather than the whole result set - the screen shows ten
     * rows, so ten rows is what crosses the network. Paging in the browser did
     * not save anything, because everything had already been downloaded to
     * slice it.
     */
    @PostMapping("/advanced")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canAdvancedSearchSearch')")
    public ResponseEntity<Page<SearchResponseDTO>> advancedSearch(
            @RequestBody AdvancedSearchRequestDTO filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        return ResponseEntity.ok(searchService.advancedSearch(
                filters, page, Math.min(Math.max(size, 1), 100), searchScopeOwnerId(auth)));
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
        
        // Get current user ID from authentication context
        UUID userId = SecurityUtils.currentUserId();
        
        SearchLog log = new SearchLog(
                userId,
                logRequest.getQuery(),
                logRequest.getClickedDocId()
        );
        searchLogRepository.save(log);

        return ResponseEntity.ok().build();
    }

    /**
     * Get Search History (recently clicked items) for current user
     * Usage: GET /api/search/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<SearchHistoryResponseDTO>> getSearchHistory() {
        UUID userId = SecurityUtils.currentUserId();
        return ResponseEntity.ok(searchLogRepository.findSearchHistoryByUserId(userId));
    }

    /**
     * Clear Search History for current user
     * Usage: DELETE /api/search/history
     */
    @DeleteMapping("/history")
    public ResponseEntity<Void> clearSearchHistory() {
        UUID userId = SecurityUtils.currentUserId();
        searchLogRepository.deleteAllByUserId(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all available filter options for search dropdowns
     * Usage: GET /api/search/options
     */
    @GetMapping("/options")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewSearch')")
    public ResponseEntity<Map<String, List<String>>> getFilterOptions(Authentication auth) {
        Map<String, List<String>> options = new HashMap<>();
        
        // Document types
        options.put("documentTypes", List.of(
            "Invoice",
            "Contract",
            "Report",
            "Memo",
            "Email",
            "Proposal",
            "Agreement",
            "Other"
        ));
        
        // Document statuses
        options.put("statuses", List.of(
            "Draft",
            "Pending",
            "Approved",
            "Rejected",
            "Archived",
            "Active"
        ));
        
        // Owner filters. Offering every active user to someone who can only
        // search their own documents both leaks the user directory and lists
        // filters that can never match, so the list follows the search scope.
        if (searchScopeOwnerId(auth) == null) {
            options.put("owners", userRepository.findAll().stream()
                    .filter(u -> "ACTIVE".equals(u.getStatus()))
                    .map(com.dms.models.User::getUsername)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList()
            );
        } else {
            options.put("owners", userRepository.findById(SecurityUtils.currentUserId())
                    .map(com.dms.models.User::getUsername)
                    .map(List::of)
                    .orElseGet(List::of)
            );
        }
        
        // Signature statuses
        options.put("signatureStatuses", List.of(
            "Unsigned",
            "Pending Signature",
            "Signed",
            "Rejected"
        ));
        
        // Date range options
        options.put("dateRanges", List.of(
            "Last 7 Days",
            "Last 30 Days",
            "Last 90 Days",
            "This Year",
            "Custom Range"
        ));
        
        return ResponseEntity.ok(options);
    }

    /**
     * Get specific filter options by name
     * Usage: GET /api/search/options/{filterName}
     */
    @GetMapping("/options/{filterName}")
    public ResponseEntity<List<String>> getFilterOptionsByName(@PathVariable String filterName) {
        Map<String, List<String>> allOptions = new HashMap<>();
        
        allOptions.put("documentTypes", List.of(
            "Invoice", "Contract", "Report", "Memo", "Email", "Proposal", "Agreement", "Other"
        ));
        allOptions.put("statuses", List.of(
            "Draft", "Pending", "Approved", "Rejected", "Archived", "Active"
        ));
        allOptions.put("owners", List.of(
            "Me", "Team", "Organization", "Shared with Me"
        ));
        allOptions.put("signatureStatuses", List.of(
            "Unsigned", "Pending Signature", "Signed", "Rejected"
        ));
        allOptions.put("dateRanges", List.of(
            "Last 7 Days", "Last 30 Days", "Last 90 Days", "This Year", "Custom Range"
        ));
        
        List<String> options = allOptions.getOrDefault(filterName, List.of());
        if (options.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(options);
    }
}