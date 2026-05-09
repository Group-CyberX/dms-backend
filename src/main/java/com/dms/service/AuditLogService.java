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
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAuditLog(String action, UUID entityId, String remoteAddr, String status) {
        AuditLog log = new AuditLog();

        // Clean the local address (Moved from controller to service)
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            remoteAddr = "127.0.0.1";
        }

        log.setAction(action);
        log.setEntity_id(entityId);
        log.setIp(remoteAddr);
        log.setStatus(status);
        log.setTimestamp(LocalDateTime.now());

        // Use your test UUID or fetch from SecurityContext
        UUID testUserId = UUID.fromString("0b0f8543-672e-4a5a-bb8d-99da74f94f90");
        log.setUser_id(testUserId);

        auditLogRepository.save(log);
    }

    // Fetches every log in the database for the admin dashboard
    public List<AuditLog> getAllLogs(){
        return auditLogRepository.findAll();
    }

    // Saves a log object directly (used by the REST API)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog saveLog(AuditLog auditLog){
        if(auditLog.getTimestamp()==null){
            auditLog.setTimestamp(LocalDateTime.now());
        }
        return auditLogRepository.save(auditLog);
    }

    // call the custom filter in the repository
    public List<AuditLog> getFilteredLogs(UUID userId, String action, LocalDateTime fromDate, LocalDateTime toDate) {
        return auditLogRepository.findByFilters(userId, action, fromDate, toDate);
    }
}
