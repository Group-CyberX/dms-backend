package com.dms.models;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long log_id;
    private Long user_id;
    private String action;
    private Long entity_id;
    private LocalDateTime timestamp;

    public AuditLog() {}

    public AuditLog(
            Long log_id,
            Long user_id,
            String action,
            Long entity_id,
            LocalDateTime timestamp
    ) {
        this.log_id = log_id;
        this.user_id = user_id;
        this.action = action;
        this.entity_id = entity_id;
        this.timestamp = timestamp;
    }

    public Long getLog_id() {
        return log_id;
    }

    public void setLog_id(Long log_id) {
        this.log_id = log_id;
    }

    public Long getUser_id() {
        return user_id;
    }

    public void setUser_id(Long user_id) {
        this.user_id = user_id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getEntity_id() {
        return entity_id;
    }

    public void setEntity_id(Long entity_id) {
        this.entity_id = entity_id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AuditLog auditLog = (AuditLog) o;
        return Objects.equals(log_id, auditLog.log_id) && Objects.equals(user_id, auditLog.user_id) && Objects.equals(action, auditLog.action) && Objects.equals(entity_id, auditLog.entity_id) && Objects.equals(timestamp, auditLog.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(log_id, user_id, action, entity_id, timestamp);
    }
}
