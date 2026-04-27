package com.dms.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "workflow_template_step")
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

    private String approverRole;
}
