package com.dms.dao;

import com.dms.models.WorkflowTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, Long> {

    // Get all tasks for a workflow instance in step order
    List<WorkflowTask> findByInstanceIdOrderByStepOrderAsc(Long instanceId);
    
    // Get a specific step task within a workflow instance
    Optional<WorkflowTask> findByInstanceIdAndStepOrder(Long instanceId, int stepOrder);
}