package com.dms.dao;

import com.dms.models.WorkflowTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, Long> {

    // Get all tasks for a workflow instance in step order
    List<WorkflowTask> findByInstanceIdOrderByStepOrderAsc(Long instanceId);
    
    // Get a specific step task within a workflow instance
    Optional<WorkflowTask> findByInstanceIdAndStepOrder(Long instanceId, int stepOrder);

    /**
     * Every task belonging to any of these instances, in one query. This is what
     * replaces the dashboard's one-request-per-workflow loop.
     */
    List<WorkflowTask> findByInstanceIdIn(Collection<Long> instanceIds);

    /** Tasks waiting on a given person, matched by user id or by role name. */
    long countByStatusIgnoreCaseInAndUserIdIgnoreCaseIn(
            Collection<String> statuses, Collection<String> assignees);
}