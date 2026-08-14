package com.dms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserSignatureRequest {
    private String label;

    @JsonProperty("signatureType")
    private String signatureType;

    @JsonProperty("signatureDataUrl")
    private String signatureDataUrl;

    @JsonProperty("isDefault")
    private boolean isDefault;

    // Default Constructor required by Jackson
    public UserSignatureRequest() {}

    // Getters and Setters
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getSignatureType() { return signatureType; }
    public void setSignatureType(String signatureType) { this.signatureType = signatureType; }

    public String getSignatureDataUrl() { return signatureDataUrl; }
    public void setSignatureDataUrl(String signatureDataUrl) { this.signatureDataUrl = signatureDataUrl; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}