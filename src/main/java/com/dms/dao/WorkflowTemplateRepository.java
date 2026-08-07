package com.dms.dao;

import com.dms.models.WorkflowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, Long> {
    // Custom query method to find templates by document type
    List<WorkflowTemplate> findByDocumentTypeIgnoreCase(String documentType);
}
