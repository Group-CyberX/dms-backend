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

    private LocalDateTime createdAt;
}