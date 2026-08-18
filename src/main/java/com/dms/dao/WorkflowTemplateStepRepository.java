package com.dms.dao;

import com.dms.models.WorkflowTemplateStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WorkflowTemplateStepRepository extends JpaRepository<WorkflowTemplateStep, Long> {
    // Fetch steps of a template in correct execution order (ascending stepOrder)
    List<WorkflowTemplateStep> findByTemplateIdOrderByStepOrderAsc(Long templateId);

    /**
     * Steps for several templates at once. Screens that list templates used to
     * request each template's steps separately, which is one request per row.
     */
    List<WorkflowTemplateStep> findByTemplateIdInOrderByTemplateIdAscStepOrderAsc(Collection<Long> templateIds);
}