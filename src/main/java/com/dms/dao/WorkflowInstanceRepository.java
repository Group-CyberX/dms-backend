package com.dms.dao;

import com.dms.models.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * The most recent workflow for each document, as three columns.
     *
     * The document list and the document page both only want a status badge,
     * and both used to fetch every workflow row in the table and reduce it in
     * the browser to exactly this. DISTINCT ON does the same reduction in the
     * database and returns one short row per document.
     */
    @Query(value = """
            select distinct on (i.document_id)
                   i.document_id as "documentId",
                   i.id          as "workflowId",
                   i.status      as "status"
            from workflow_instance i
            where i.document_id is not null
              and i.document_id <> ''
            order by i.document_id, i.id desc
            """, nativeQuery = true)
    List<DocumentWorkflowStatus> findLatestStatusPerDocument();

    /**
     * How many workflows each template has produced, counted by the database.
     * The policies screen used to fetch every workflow row and total them per
     * template in the browser.
     */
    @Query("SELECT i.templateId AS templateId, COUNT(i.id) AS usageCount " +
           "FROM WorkflowInstance i WHERE i.templateId IS NOT NULL GROUP BY i.templateId")
    List<TemplateUsage> findTemplateUsage();

    interface TemplateUsage {
        Long getTemplateId();
        long getUsageCount();
    }

    /** Projection for {@link #findLatestStatusPerDocument()}. */
    interface DocumentWorkflowStatus {
        String getDocumentId();
        Long getWorkflowId();
        String getStatus();
    }
}
