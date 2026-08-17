package com.dms.rest;

import com.dms.dto.AuditLogResponse;
import com.dms.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminAuditController {
    private final AuditLogService auditLogService;

    public AdminAuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * One page of activity, newest first.
     *
     * This used to return the entire table. The audit trail is append-only, so
     * that request got slower every day the system was used and there was no
     * point at which it stopped growing.
     */
    @GetMapping("/logs")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewAuditLog')")
    public Page<AuditLogResponse> getAllLogs(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "25") int size,
                                     @RequestParam(defaultValue = "false") boolean export,
                                     HttpServletRequest request){

        // Taking a copy of the trail is itself an event the trail should carry:
        // it says who read the record of everyone else's activity, and when.
        if (export) {
            auditLogService.tryRecordCurrentUser("AUDIT_LOG_EXPORTED", null,
                    request.getRemoteAddr(), "SUCCESS",
                    "exported up to " + size + " audit records");
        }

        return auditLogService.getLogs(page, size);
    }

    // The Filter Endpoint - filtering happens in the database, not the browser.
    @GetMapping("/logs/filter")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewAuditLog')")
    public Page<AuditLogResponse> getFilteredLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        // Convert strings to proper types or null
        UUID userUuid = (userId != null && !userId.isEmpty() && !userId.equals("all"))
                ? UUID.fromString(userId) : null;

        String actionParam = (action != null && !action.isEmpty() && !action.equals("all"))
                ? action : null;

        LocalDateTime start = (fromDate != null && !fromDate.isEmpty())
                ? LocalDateTime.parse(fromDate + "T00:00:00") : null;

        LocalDateTime end = (toDate != null && !toDate.isEmpty())
                ? LocalDateTime.parse(toDate + "T23:59:59") : null;

        return auditLogService.getFilteredLogs(userUuid, actionParam, start, end, page, size);
    }

    // There is deliberately no POST /logs. An audit trail is only evidence if
    // nothing outside the system can write to it, so rows are created by the
    // services that perform the action (AuditLogService.createAuditLog) and by
    // nothing else. The endpoint that used to sit here accepted a fully
    // caller-supplied AuditLog from any authenticated user, which meant the
    // trail could be forged - see requirements 11.4.

//    // Provides a list of all system users to populate the UI filter dropdown
//    @GetMapping("/users")
//    public List<User> getAllUsers() {
//        return userService.getAllUsers();
//    }
}