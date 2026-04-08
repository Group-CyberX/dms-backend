package com.dms.service;

import com.dms.dao.AuditLogRepository;
import com.dms.models.AuditLog;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class AuditLogService {
    private AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void LogAudit(UUID log_id, UUID user_id, UUID entity_id, String action,String ip_address,String status) {
        AuditLog auditLog = new AuditLog(log_id, user_id, action, entity_id, LocalDateTime.now(),ip_address,status);
        auditLogRepository.save(auditLog);
    }

    public List<AuditLog> getAllLogs(){
        return auditLogRepository.findAll();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog saveLog(AuditLog auditLog){
        if(auditLog.getTimestamp()==null){
            auditLog.setTimestamp(LocalDateTime.now());
        }
        return auditLogRepository.save(auditLog);
    }
    public List<AuditLog> getFilteredLogs(UUID userId, String action, LocalDateTime fromDate, LocalDateTime toDate) {
        return auditLogRepository.findByFilters(userId, action, fromDate, toDate);
    }
}
