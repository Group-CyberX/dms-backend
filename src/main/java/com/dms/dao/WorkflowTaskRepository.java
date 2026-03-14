package com.dms.dao;

import com.dms.models.WorkflowTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, Long> {
}