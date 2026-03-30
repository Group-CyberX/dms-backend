package com.dms.dao;

import com.dms.models.WorkflowTemplateStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowTemplateStepRepository extends JpaRepository<WorkflowTemplateStep, Long> {

    List<WorkflowTemplateStep> findByTemplateId(Long templateId);
}