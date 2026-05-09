package com.dms.dto;

import java.util.UUID;

public class SearchLogRequestDTO {
    private String query;
    private UUID clickedDocId;

    public String getQuery() {
         return query; 
    }
    public void setQuery(String query) { 
        this.query = query; 
    }

    public UUID getClickedDocId() {
         return clickedDocId;
 }
    public void setClickedDocId(UUID clickedDocId) {
         this.clickedDocId = clickedDocId; 
    }
}
