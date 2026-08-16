package com.dms.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "workflow_instance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String documentId;

    private Long templateId;

    private String workflowName;

    private String description;

    private String documentType;

    private String priority;

    private LocalDate dueDate;

    private String workflowType;

    private String status;

    private String createdByUserId;

    /**
     * Whether approvers on this workflow must place a digital signature.
     *
     * Captured on the instance rather than read from the template each time, so
     * editing a template later cannot change the rules of workflows already in
     * flight - and so manually built workflows (which have no template) can
     * require a signature too.
     *
     * Nullable: rows created before this column existed hold NULL, which reads
     * as "not required". See requiresSignatureOrFalse().
     */
    @Column(name = "requires_signature")
    @Builder.Default
    private Boolean requiresSignature = Boolean.FALSE;

    /** Null-safe accessor for the flag. */
    public boolean requiresSignatureOrFalse() {
        return Boolean.TRUE.equals(requiresSignature);
    }
}