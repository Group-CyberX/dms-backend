package com.dms.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_template")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String documentType;
    private int numberOfSteps;
    private String workflowType;
    private String createdBy;
    private boolean isSystemTemplate;

    /**
     * When true, approvers must place a digital signature on the document
     * before the approval is accepted. Only meaningful for PDF documents.
     *
     * Boxed and nullable on purpose: templates created before this column
     * existed hold NULL, and a primitive would fail to load them. Null is read
     * as "no signature required" - see requiresSignatureOrFalse().
     */
    @Column(name = "requires_signature")
    private Boolean requiresSignature = Boolean.FALSE;

    /** Null-safe accessor for the flag. */
    public boolean requiresSignatureOrFalse() {
        return Boolean.TRUE.equals(requiresSignature);
    }

    private LocalDateTime createdAt;
}