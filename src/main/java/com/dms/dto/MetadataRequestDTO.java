package com.dms.dto;

public class MetadataRequestDTO {
    private String key;
    private String value;

    public MetadataRequestDTO() {}

    public MetadataRequestDTO(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "MetadataRequestDTO{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}