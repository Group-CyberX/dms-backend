package com.dms.models;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;


@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JsonProperty("log_id")
    private UUID log_id;

    @JsonProperty("user_id")
    private UUID user_id;

    @JsonProperty("action")
    private String action;

    @JsonProperty("entity_id")
    private UUID entity_id;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("ip")
    private String ip;

    @JsonProperty("status")
    private String status;

    public AuditLog() {}

    public AuditLog(
            UUID log_id,
            UUID user_id,
            String action,
            UUID entity_id,
            LocalDateTime timestamp,
            String ip,
            String status
    ) {
        this.log_id = log_id;
        this.user_id = user_id;
        this.action = action;
        this.entity_id = entity_id;
        this.timestamp = timestamp;
        this.ip = ip;
        this.status = status;
    }

    public UUID getLog_id() {
        return log_id;
    }

    public void setLog_id(UUID log_id) {
        this.log_id = log_id;
    }

    public UUID getUser_id() {
        return user_id;
    }

    public void setUser_id(UUID user_id) {
        this.user_id = user_id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public UUID getEntity_id() {
        return entity_id;
    }

    public void setEntity_id(UUID entity_id) {
        this.entity_id = entity_id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getIp() {
            return ip;
    }
    public void setIp(String ip) {this.ip = ip;}
    public String getStatus() {return status;}
    public void setStatus(String status) {this.status = status;}

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AuditLog auditLog = (AuditLog) o;
        return Objects.equals(log_id, auditLog.log_id) && Objects.equals(user_id, auditLog.user_id) && Objects.equals(action, auditLog.action) && Objects.equals(entity_id, auditLog.entity_id) && Objects.equals(timestamp, auditLog.timestamp) && Objects.equals(ip, auditLog.ip) && Objects.equals(status, auditLog.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(log_id, user_id, action, entity_id, timestamp, ip, status);
    }
}
