package com.dms.dao;

import com.dms.models.WorkflowTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, Long> {
    List<WorkflowTask> findByInstanceIdOrderByStepOrderAsc(Long instanceId);
    Optional<WorkflowTask> findByInstanceIdAndStepOrder(Long instanceId, int stepOrder);
}