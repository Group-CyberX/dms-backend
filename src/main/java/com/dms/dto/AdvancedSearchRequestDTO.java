package com.dms.dto;

public class AdvancedSearchRequestDTO {
    private String query;
    private String documentType;
    private String status;
    private String owner;
    private String signatureStatus;
    private String dateRange;
    private String tags;

    public AdvancedSearchRequestDTO() {}

    public String getQuery() {
         return query; 
    }
    public void setQuery(String query) {
        this.query = query; 
    }

    public String getDocumentType() {
         return documentType; 
    }
    public void setDocumentType(String documentType) {
         this.documentType = documentType; 
    }

    public String getStatus() {
         return status; 
    }
    public void setStatus(String status) {
         this.status = status; 
    }

    public String getOwner() {
         return owner; 
    }
    public void setOwner(String owner) {
         this.owner = owner; 
    }

    public String getSignatureStatus() {
         return signatureStatus; 
    }
    public void setSignatureStatus(String signatureStatus) {
         this.signatureStatus = signatureStatus; 
    }

    public String getDateRange() {
         return dateRange; 
    }
    public void setDateRange(String dateRange) {
         this.dateRange = dateRange; 
    }

    public String getTags() {
         return tags; 
    }
    public void setTags(String tags) {
         this.tags = tags; 
    }
}