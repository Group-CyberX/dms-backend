package com.dms.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
// Steps are looked up per template, and per step within a template when
// resolving the approver shown against a task.
@Table(name = "workflow_template_step", indexes = {
        @Index(name = "idx_wf_template_step_template", columnList = "template_id, step_order")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowTemplateStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long templateId;
    private int stepOrder;

    private String approverUserId;
    private String approverName;
    private String approverRole;
}
