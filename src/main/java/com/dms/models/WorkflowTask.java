package com.dms.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
// Tasks are read per workflow instance, and per assignee for My Tasks.
@Table(name = "workflow_task", indexes = {
        @Index(name = "idx_workflow_task_instance", columnList = "instance_id"),
        @Index(name = "idx_workflow_task_user", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long instanceId;

    private int stepOrder;

    private String userId;

    private String status;

    @Column(length = 2000)
    private String actionComment;
}
