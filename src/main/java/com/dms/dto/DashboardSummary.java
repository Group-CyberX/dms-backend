package com.dms.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the dashboard draws, in one response.
 *
 * The screen used to assemble this itself: it fetched every user, every
 * workflow and every document, then made one further request per active
 * workflow to read its tasks. That is a classic N+1 - the request count grew
 * with the data - and it is why the dashboard took about thirty seconds.
 *
 * Here the counts are computed by the database and the SLA list is built from a
 * fixed number of queries regardless of how many workflows exist.
 */
public record DashboardSummary(
        long totalUsers,
        long totalDocuments,
        long myDocuments,
        long archivedDocuments,
        long activeWorkflows,
        long completedWorkflows,
        long submittedWorkflows,
        long pendingApprovals,
        long unreadNotifications,
        long erpConnections,
        long auditEvents,
        long failedAuditEvents,
        List<SlaAlert> slaAlerts) {

    /**
     * One approval that is near or past its due date and is waiting on the
     * caller. daysRemaining is negative once the date has passed.
     */
    public record SlaAlert(
            Long taskId,
            String documentId,
            String documentTitle,
            String workflowName,
            String priority,
            String assignedBy,
            LocalDate dueDate,
            long daysRemaining,
            boolean overdue) {
    }

    /** Empty summary for a caller whose role shows none of these panels. */
    public static DashboardSummary empty(UUID unusedUserId) {
        return new DashboardSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }
}
