package com.dms.rest;

import com.dms.models.AuditLog;
import com.dms.service.AuditLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminAuditController {
    private final AuditLogService auditLogService;

    public AdminAuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/logs")
    public List<AuditLog> getAllLogs(){
        return auditLogService.getAllLogs();
    }

    @PostMapping("/logs")
    public AuditLog newLog(@RequestBody AuditLog auditLog){
        return auditLogService.saveLog(auditLog);
    }
}
