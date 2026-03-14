package com.dms.dao;

import com.dms.models.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {
}
