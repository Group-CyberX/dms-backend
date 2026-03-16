package com.dms.models;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Documents {

    @Id
    @GeneratedValue
    @Column(name = "document_id")
    private UUID documentId;

    private String title;

    @JsonManagedReference
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DocumentMetadata> metadata;

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<DocumentMetadata> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<DocumentMetadata> metadata) {
        this.metadata = metadata;
    }
}