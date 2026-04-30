package com.dms.dao;

import com.dms.models.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {
    // Count how many workflows are created using a specific template
	long countByTemplateId(Long templateId);
}
