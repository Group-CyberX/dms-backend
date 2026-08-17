package com.dms.dto;

import com.dms.models.AuditLog;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the audit trail as the admin screen shows it.
 *
 * The entity was being returned directly, so every row identified its actor by
 * raw UUID alone. That is the one column an auditor reads first - "who did
 * this" - and a UUID does not answer it without looking each id up by hand.
 *
 * The field names deliberately match the entity's existing JSON, so adding
 * user_name does not disturb anything already reading these rows.
 */
public record AuditLogResponse(
        UUID log_id,
        UUID user_id,
        /** Display name of the actor; null when unknown - see AuditLogService. */
        String user_name,
        String action,
        UUID entity_id,
        LocalDateTime timestamp,
        String ip,
        String status,
        String details
) {

    public static AuditLogResponse of(AuditLog log, String userName) {
        return new AuditLogResponse(
                log.getLog_id(),
                log.getUser_id(),
                userName,
                log.getAction(),
                log.getEntity_id(),
                log.getTimestamp(),
                log.getIp(),
                log.getStatus(),
                log.getDetails()
        );
    }
}
