package com.dms.dto;

public class SearchRequestDTO {

    private String title; // search by document title
    private String metadataKey; // search by metadata key
    private String metadataValue; // search by metadata value

    public SearchRequestDTO() {}

    public SearchRequestDTO(String title, String metadataKey, String metadataValue) {
        this.title = title;
        this.metadataKey = metadataKey;
        this.metadataValue = metadataValue;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMetadataKey() {
        return metadataKey;
    }

    public void setMetadataKey(String metadataKey) {
        this.metadataKey = metadataKey;
    }

    public String getMetadataValue() {
        return metadataValue;
    }

    public void setMetadataValue(String metadataValue) {
        this.metadataValue = metadataValue;
    }
}