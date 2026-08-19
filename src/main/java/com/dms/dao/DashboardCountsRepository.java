package com.dms.dao;

import com.dms.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

/**
 * Every headline figure on the dashboard, in one query.
 *
 * The panel needs a dozen counts. Asked for one at a time they are all cheap,
 * but each is a separate round trip to a database that is not on this machine,
 * and the round trips are what the request actually costs - together they were
 * most of two seconds while the database itself was barely working.
 *
 * Written as scalar subqueries against a single row so the whole set comes back
 * at once. This repository is bound to User only because Spring Data needs an
 * entity to hang a native query on; it never reads or writes users.
 */
public interface DashboardCountsRepository extends JpaRepository<User, UUID> {

    @Query(value = """
            select
              (select count(*) from users) as userCount,
              (select count(*) from "Document" where is_deleted = false) as activeDocuments,
              (select count(*) from "Document" where is_deleted = false and owner_id = :userId) as myDocuments,
              (select count(*) from "Document" where is_deleted = true) as deletedDocuments,
              (select count(*) from workflow_instance
                 where upper(status) in (:runningStatuses)) as runningWorkflows,
              (select count(*) from workflow_instance
                 where upper(status) = 'APPROVED') as approvedWorkflows,
              (select count(*) from workflow_instance
                 where created_by_user_id = :userIdText) as myWorkflows,
              (select count(*) from workflow_task
                 where upper(status) in (:openTaskStatuses)
                   and upper(user_id) in (:assignees)) as myOpenTasks,
              (select count(*) from notifications
                 where user_id = :userId and is_read = false) as unreadNotifications,
              (select count(*) from erp_connections) as erpConnections,
              (select count(*) from audit_logs) as auditEntries,
              (select count(*) from audit_logs where upper(status) = 'FAILED') as failedAuditEntries
            """, nativeQuery = true)
    DashboardCounts loadAll(@Param("userId") UUID userId,
                            @Param("userIdText") String userIdText,
                            @Param("runningStatuses") Collection<String> runningStatuses,
                            @Param("openTaskStatuses") Collection<String> openTaskStatuses,
                            @Param("assignees") Collection<String> assignees);

    interface DashboardCounts {
        Long getUserCount();
        Long getActiveDocuments();
        Long getMyDocuments();
        Long getDeletedDocuments();
        Long getRunningWorkflows();
        Long getApprovedWorkflows();
        Long getMyWorkflows();
        Long getMyOpenTasks();
        Long getUnreadNotifications();
        Long getErpConnections();
        Long getAuditEntries();
        Long getFailedAuditEntries();
    }
}
