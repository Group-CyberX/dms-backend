package com.dms.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * How long a class of documents is kept before it is retired.
 *
 * Requirements §9.2 asks for automated retention policy enforcement, and §4.1
 * makes it the Document Administrator's responsibility. A policy names the
 * documents it covers, the age at which they are due, and what should happen
 * then - see {@link Action}.
 */
@Entity
@Table(name = "retention_policies")
public class RetentionPolicy {

    /** What a policy does to a document once it is older than its retention period. */
    public enum Action {
        /** Report it only. The safe default: nothing is removed without a decision. */
        FLAG,
        /** Move it to the recycle bin, where it can still be restored. */
        ARCHIVE
    }

    /** Which documents a policy covers. */
    public enum Scope {
        /** Every active document. */
        ALL,
        /** Documents carrying a particular tag. */
        TAG
    }

    @Id
    @Column(name = "policy_id", updatable = false, nullable = false)
    private UUID policyId = UUID.randomUUID();

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private Scope scope = Scope.ALL;

    /** The tag name when scope is TAG; ignored otherwise. */
    @Column(name = "match_value")
    private String matchValue;

    @Column(name = "retain_days", nullable = false)
    private int retainDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private Action action = Action.FLAG;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    /** How many documents the last run acted on, for the summary in the UI. */
    @Column(name = "last_run_affected")
    private Integer lastRunAffected;

    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }

    public String getMatchValue() { return matchValue; }
    public void setMatchValue(String matchValue) { this.matchValue = matchValue; }

    public int getRetainDays() { return retainDays; }
    public void setRetainDays(int retainDays) { this.retainDays = retainDays; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }

    public Integer getLastRunAffected() { return lastRunAffected; }
    public void setLastRunAffected(Integer lastRunAffected) { this.lastRunAffected = lastRunAffected; }
}
