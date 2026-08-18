package com.dms.dao;

import com.dms.models.WorkflowTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, Long> {

    // Get all tasks for a workflow instance in step order
    List<WorkflowTask> findByInstanceIdOrderByStepOrderAsc(Long instanceId);
    
    // Get a specific step task within a workflow instance
    Optional<WorkflowTask> findByInstanceIdAndStepOrder(Long instanceId, int stepOrder);

    /**
     * Every task belonging to any of these instances, in one query. This is what
     * replaces the dashboard's one-request-per-workflow loop.
     */
    List<WorkflowTask> findByInstanceIdIn(Collection<Long> instanceIds);

    /** Tasks waiting on a given person, matched by user id or by role name. */
    long countByStatusIgnoreCaseInAndUserIdIgnoreCaseIn(
            Collection<String> statuses, Collection<String> assignees);

    /**
     * Everything the My Tasks screen renders, for one assignee, in one round trip.
     *
     * The screen used to fetch all workflows, all documents and all users, then
     * issue one request per workflow to collect tasks, and finally throw away
     * every row not assigned to the viewer. That cost hundreds of requests and
     * grew with the size of the tables rather than with the viewer's workload.
     *
     * A task is the viewer's when user_id holds their id or their role name -
     * both forms exist in the data, and matching only on id silently hides the
     * role-assigned ones.
     *
     * Joins are all LEFT so a task whose document, template step or user has
     * gone missing still produces a row; the screen already renders a fallback
     * title. Ids are compared as text rather than cast to uuid because
     * workflow_instance stores them as free-form strings, and casting a
     * non-uuid value would fail the whole query instead of simply not matching.
     */
    @Query(value = """
            select t.id                as "taskId",
                   t.step_order        as "stepOrder",
                   t.status            as "status",
                   t.action_comment    as "actionComment",
                   t.user_id           as "assigneeUserId",
                   i.id                as "workflowId",
                   i.workflow_name     as "workflowName",
                   i.status            as "workflowStatus",
                   i.due_date          as "dueDate",
                   i.priority          as "priority",
                   i.template_id       as "templateId",
                   i.requires_signature as "requiresSignature",
                   i.document_id       as "documentId",
                   d.title             as "documentTitle",
                   s.approver_name     as "stepApproverName",
                   au.username         as "assigneeName",
                   cu.username         as "createdByName",
                   (i.due_date is not null
                     and i.due_date < current_date
                     and upper(coalesce(i.status, '')) not in ('APPROVED', 'REJECTED')) as "overdue"
            from workflow_task t
            join workflow_instance i on i.id = t.instance_id
            left join "Document" d on d.document_id::text = i.document_id
            left join workflow_template_step s
                   on s.template_id = i.template_id and s.step_order = t.step_order
            left join users au on au.user_id::text = t.user_id
            left join users cu on cu.user_id::text = i.created_by_user_id
            where t.user_id = :userId or upper(t.user_id) = upper(:roleName)
            order by i.due_date asc nulls last, t.id asc
            """, nativeQuery = true)
    List<MyTaskRow> findMyTasks(@Param("userId") String userId,
                                @Param("roleName") String roleName);

    /** Projection for {@link #findMyTasks}; getter names match the query aliases. */
    interface MyTaskRow {
        Long getTaskId();
        Integer getStepOrder();
        String getStatus();
        String getActionComment();
        /** Raw stored assignment: a user id for most rows, a role name for some. */
        String getAssigneeUserId();
        Long getWorkflowId();
        String getWorkflowName();
        String getWorkflowStatus();
        LocalDate getDueDate();
        String getPriority();
        Long getTemplateId();
        Boolean getRequiresSignature();
        String getDocumentId();
        String getDocumentTitle();
        String getStepApproverName();
        String getAssigneeName();
        String getCreatedByName();
        Boolean getOverdue();
    }
}