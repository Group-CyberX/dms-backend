package com.dms.dto;

import java.util.UUID;

public class TagDTO {
    private UUID tagId;
    private String tagName;

    public TagDTO(UUID tagId, String tagName) {
        this.tagId = tagId;
        this.tagName = tagName;
    }

    public TagDTO() {}

    public UUID getTagId() {
        return tagId;
    }

    public void setTagId(UUID tagId) {
        this.tagId = tagId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    @Override
    public String toString() {
        return "TagDTO{" +
                "tagId=" + tagId +
                ", tagName='" + tagName + '\'' +
                '}';
    }
}
