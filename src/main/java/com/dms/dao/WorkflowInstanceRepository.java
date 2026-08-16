package com.dms.dao;

import com.dms.models.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {
    // Count how many workflows are created using a specific template
    long countByTemplateId(Long templateId);

    // Find workflows for a specific document
    List<WorkflowInstance> findByDocumentId(String documentId);

    /**
     * Workflows for a whole set of documents at once. Search used to call
     * findByDocumentId separately for every result row, which is one query per
     * document just to display a status column.
     */
    List<WorkflowInstance> findByDocumentIdIn(Collection<String> documentIds);

    // ---- Dashboard aggregates -------------------------------------------
    // Counted in the database rather than by loading every row and calling
    // .length in the browser.

    long countByStatusIgnoreCaseIn(Collection<String> statuses);

    long countByCreatedByUserId(String createdByUserId);

    /**
     * The only workflows an SLA panel cares about: still running, and with a
     * due date inside the alert window. Everything else never leaves the
     * database.
     */
    List<WorkflowInstance> findByStatusIgnoreCaseInAndDueDateBetween(
            Collection<String> statuses, LocalDate from, LocalDate to);
}
