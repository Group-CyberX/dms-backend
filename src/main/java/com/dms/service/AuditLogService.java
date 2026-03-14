package com.dms.service;

import com.dms.dao.AuditLogRepository;
import com.dms.models.AuditLog;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {
    private AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void LogAudit(UUID log_id, UUID user_id, UUID entity_id, String action) {
        AuditLog auditLog = new AuditLog(log_id, user_id, action, entity_id, LocalDateTime.now());
        auditLogRepository.save(auditLog);
    }

    public List<AuditLog> getAllLogs(){
        return auditLogRepository.findAll();
    }

    public AuditLog saveLog(AuditLog auditLog){
        if(auditLog.getTimestamp()==null){
            auditLog.setTimestamp(LocalDateTime.now());
        }
        return auditLogRepository.save(auditLog);
    }
}
