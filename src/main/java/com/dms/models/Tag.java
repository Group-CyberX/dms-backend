package com.dms.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity(name = "Tag")
@Table(name = "\"Tag\"")
public class Tag {
    @Id
    @Column(name = "tag_id")
    private UUID tag_id;

    @Column(name = "tag_name")
    private String tag_name;

    public Tag() {
    }

    public Tag(UUID tag_id,
               String tag_name) {
        this.tag_id = tag_id;
        this.tag_name = tag_name;
    }

    public UUID getTag_id() {
        return tag_id;
    }

    public void setTag_id(UUID tag_id) {
        this.tag_id = tag_id;
    }

    public String getTag_name() {
        return tag_name;
    }

    public void setTag_name(String tag_name) {
        this.tag_name = tag_name;
    }

    @Override
    public String toString() {
        return "Tag{" +
                "tag_id=" + tag_id +
                ", tag_name='" + tag_name + '\'' +
                '}';
    }
}
