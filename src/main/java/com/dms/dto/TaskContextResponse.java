package com.dms.dto;

import java.time.LocalDate;

/**
 * Everything the document page needs to draw its approval banner, in one call.
 *
 * It used to work this out for itself: fetch the document's workflows, then one
 * request per workflow for its tasks, then another for the current user, then
 * search the result for the task it already had the id of. The page waited on
 * all of that before rendering anything, which is why opening a document from
 * My Tasks felt unresponsive.
 *
 * statusMessage is resolved here because the rules behind it - is the step
 * still waiting on an earlier one, is the workflow rejected, is it past its due
 * date, is it even assigned to this person - are all answerable from data the
 * server already holds.
 */
public record TaskContextResponse(
        Long taskId,
        Long instanceId,
        String documentId,
        String workflowName,
        boolean requiresSignature,
        String taskStatus,
        String workflowStatus,
        LocalDate dueDate,
        boolean assignedToMe,
        boolean overdue,
        /** Ready-to-show banner text, or null when the step is actionable. */
        String statusMessage
) {}
