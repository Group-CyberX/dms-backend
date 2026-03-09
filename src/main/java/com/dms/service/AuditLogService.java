package com.dms.service;

import com.dms.dao.AuditLogRepository;
import com.dms.models.AuditLog;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AuditLogService {
    private AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void LogAudit(Long log_id,Long user_id, Long entity_id, String action) {
        AuditLog auditLog = new AuditLog(log_id, user_id, action, entity_id, LocalDateTime.now());
        auditLogRepository.save(auditLog);
    }
}
