package com.dms.dto;

import java.time.LocalDate;

/**
 * One row of the My Tasks table, with the workflow, document and approval step
 * already resolved.
 *
 * The screen used to assemble this itself: fetch every user, every workflow and
 * every document, then one request per workflow to collect its tasks, then join
 * the four sets in the browser and discard every row not assigned to the
 * viewer. That was hundreds of requests for a couple of dozen rows, and it grew
 * with the size of the tables rather than with the viewer's workload.
 *
 * overdue is decided here rather than in the browser so the badge cannot
 * disagree with the due date carried in the same response.
 */
public record MyTaskRowResponse(
        Long taskId,
        Integer stepOrder,
        String status,
        String actionComment,
        Long workflowId,
        String workflowName,
        String workflowStatus,
        LocalDate dueDate,
        String priority,
        Long templateId,
        boolean requiresSignature,
        String documentId,
        /** Falls back to a placeholder when the document row has gone. */
        String documentTitle,
        /** Step's approver name, else the assignee's username, else the raw stored value. */
        String assigneeLabel,
        String assignedByLabel,
        boolean overdue
) {}
