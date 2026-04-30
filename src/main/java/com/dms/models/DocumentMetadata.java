package com.dms.models;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import java.util.UUID;

@Entity
@Table(name = "document_metadata",
       uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "meta_key"}))
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "metadata_id")
    private UUID metadataId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @JsonBackReference
    private Documents document;

    @Column(name = "meta_key", nullable = false)
    private String key;

    @Column(name = "meta_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    // Create an empty metadata entity
    public DocumentMetadata() {}

    // Create a metadata entity with document, key, and value
    public DocumentMetadata(Documents document, String key, String value) {
        this.document = document;
        this.key = key;
        this.value = value;
    }

    // Get metadata ID
    public UUID getMetadataId() { 
        return metadataId; 
    }

    // Get parent document
    public Documents getDocument() { 
        return document; 
    }
    // Set parent document
    public void setDocument(Documents document) { 
        this.document = document; 
    }

    // Get metadata key
    public String getKey() {
         return key; 
    }

    // Set metadata key
    public void setKey(String key) { 
        this.key = key; 
    }

    // Get metadata value
    public String getValue() {
         return value; 
    }
    // Set metadata value
    public void setValue(String value) {
         this.value = value; 
    }
}