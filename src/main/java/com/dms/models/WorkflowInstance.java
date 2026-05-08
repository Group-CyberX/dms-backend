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
}