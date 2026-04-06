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

    public SearchLog() {}

    public SearchLog(UUID userId, String query, UUID clickedDocId) {
        this.userId = userId;
        this.query = query;
        this.clickedDocId = clickedDocId;
    }

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    public UUID getSearchId() { return searchId; }
    public void setSearchId(UUID searchId) { this.searchId = searchId; }
    
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    
    public UUID getClickedDocId() { return clickedDocId; }
    public void setClickedDocId(UUID clickedDocId) { this.clickedDocId = clickedDocId; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
