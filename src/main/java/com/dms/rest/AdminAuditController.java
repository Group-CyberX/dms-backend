package com.dms.rest;

import com.dms.models.AuditLog;
import com.dms.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminAuditController {
    private final AuditLogService auditLogService;

    public AdminAuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    //Returns the full list of all activities in the system
    @GetMapping("/logs")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewAuditLog')")
    public List<AuditLog> getAllLogs(){
        return auditLogService.getAllLogs();
    }

    // The Filter Endpoint
    @GetMapping("/logs/filter")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewAuditLog')")
    public List<AuditLog> getFilteredLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {

        // Convert strings to proper types or null
        UUID userUuid = (userId != null && !userId.isEmpty() && !userId.equals("all"))
                ? UUID.fromString(userId) : null;

        String actionParam = (action != null && !action.isEmpty() && !action.equals("all"))
                ? action : null;

        LocalDateTime start = (fromDate != null && !fromDate.isEmpty())
                ? LocalDateTime.parse(fromDate + "T00:00:00") : null;

        LocalDateTime end = (toDate != null && !toDate.isEmpty())
                ? LocalDateTime.parse(toDate + "T23:59:59") : null;

        return auditLogService.getFilteredLogs(userUuid, actionParam, start, end);
    }

    //Allows other parts of the system to programmatically record a new event
    @PostMapping("/logs")
    @PreAuthorize("isAuthenticated()")
    public AuditLog newLog(@RequestBody AuditLog auditLog, HttpServletRequest request){
        auditLog.setIp(request.getRemoteAddr());
        if (auditLog.getStatus() == null || auditLog.getStatus().isEmpty()){
            auditLog.setStatus("Failed");
        }
        return auditLogService.saveLog(auditLog);
    }

//    // Provides a list of all system users to populate the UI filter dropdown
//    @GetMapping("/users")
//    public List<User> getAllUsers() {
//        return userService.getAllUsers();
//    }
}