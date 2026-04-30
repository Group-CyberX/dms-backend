package com.dms.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "search_logs")
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "search_id")
    private UUID searchId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "query", nullable = false)
    private String query;

    @Column(name = "clicked_doc_id")
    private UUID clickedDocId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime timestamp;

    // Create an empty search log entity
    public SearchLog() {}

    // Create a search log entry with user, query, and clicked document
    public SearchLog(UUID userId, String query, UUID clickedDocId) {
        this.userId = userId;
        this.query = query;
        this.clickedDocId = clickedDocId;
    }

    // Set timestamp before persisting if it is missing
    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    // Get search log ID
    public UUID getSearchId() { return searchId; }
    // Set search log ID
    public void setSearchId(UUID searchId) { this.searchId = searchId; }
    
    // Get user ID
    public UUID getUserId() { return userId; }
    // Set user ID
    public void setUserId(UUID userId) { this.userId = userId; }
    
    // Get search query
    public String getQuery() { return query; }
    // Set search query
    public void setQuery(String query) { this.query = query; }
    
    // Get clicked document ID
    public UUID getClickedDocId() { return clickedDocId; }
    // Set clicked document ID
    public void setClickedDocId(UUID clickedDocId) { this.clickedDocId = clickedDocId; }
    
    // Get timestamp
    public LocalDateTime getTimestamp() { return timestamp; }
    // Set timestamp
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
