package com.dms.dao;

import com.dms.models.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {
    // Count how many workflows are created using a specific template
    long countByTemplateId(Long templateId);

    // Find workflows for a specific document
    List<WorkflowInstance> findByDocumentId(String documentId);
}
