package com.dms.service;

import com.dms.dao.AuditLogRepository;
import com.dms.dao.UserRepository;
import com.dms.dto.AuditLogResponse;
import com.dms.models.AuditLog;
import com.dms.models.User;
import com.dms.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAuditLog(String action, UUID entityId, String remoteAddr, String status) {
        record(action, SecurityUtils.currentUserId(), entityId, remoteAddr, status);
    }

    /**
     * Records an action against a named user rather than the current one.
     *
     * Needed for anything that happens outside an authenticated request - a
     * failed login has no SecurityContext to read the actor from, and that is
     * precisely the event an auditor most wants to see. Runs in its own
     * transaction so a rolled-back operation still leaves its audit trail.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, UUID userId, UUID entityId, String remoteAddr, String status) {
        record(action, userId, entityId, remoteAddr, status, null);
    }

    /** As above, plus a plain description of what was attempted. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, UUID userId, UUID entityId, String remoteAddr,
                       String status, String details) {
        AuditLog log = new AuditLog();
        log.setDetails(details);

        // Clean the local address (Moved from controller to service)
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            remoteAddr = "127.0.0.1";
        }

        log.setAction(action);
        log.setEntity_id(entityId);
        log.setIp(remoteAddr);
        log.setStatus(status);
        log.setTimestamp(LocalDateTime.now());
        log.setUser_id(userId);

        auditLogRepository.save(log);
    }

    /**
     * Best-effort audit write.
     *
     * Auditing must never be the reason an action fails: if the trail cannot be
     * written the operation still completes, and the failure is logged to the
     * console where it will be noticed.
     */
    public void tryRecord(String action, UUID userId, UUID entityId, String remoteAddr, String status) {
        tryRecord(action, userId, entityId, remoteAddr, status, null);
    }

    public void tryRecord(String action, UUID userId, UUID entityId, String remoteAddr,
                          String status, String details) {
        try {
            record(action, userId, entityId, remoteAddr, status, details);
        } catch (Exception ex) {
            System.err.println("Audit log write failed for action " + action + ": " + ex.getMessage());
        }
    }

    /**
     * Records the current user where one exists, and nobody where one does not.
     *
     * Events worth auditing happen on both sides of the login boundary - a
     * refused request may have no authenticated caller at all - so resolving the
     * actor must never itself throw.
     */
    public void tryRecordCurrentUser(String action, UUID entityId, String remoteAddr,
                                     String status, String details) {
        UUID userId = null;
        try {
            userId = SecurityUtils.currentUserId();
        } catch (Exception ignored) {
            // Unauthenticated, or the principal is not a real account.
        }
        tryRecord(action, userId, entityId, remoteAddr, status, details);
    }

    // Fetches every log in the database. Kept for exports only - the audit
    // table grows without bound, so the screen must never call this.
    public List<AuditLog> getAllLogs(){
        return auditLogRepository.findAll();
    }

    /** One newest-first page, counted and sorted by the database. */
    public Page<AuditLogResponse> getLogs(int page, int size) {
        return withUserNames(
                auditLogRepository.findAllByOrderByTimestampDesc(pageRequest(page, size)));
    }

    /**
     * Filtered and paged in a single query. Filtering used to happen in the
     * browser, which meant downloading the whole table to look at twenty rows.
     */
    public Page<AuditLogResponse> getFilteredLogs(UUID userId, String action,
                                                  LocalDateTime fromDate, LocalDateTime toDate,
                                                  int page, int size) {
        return withUserNames(
                auditLogRepository.findByFilters(userId, action, fromDate, toDate, pageRequest(page, size)));
    }

    /**
     * Attaches the actor's display name to each row on the page.
     *
     * Names are fetched once for the page rather than per row - a 500-row
     * export would otherwise become 500 extra queries.
     *
     * A name can legitimately be missing in two ways, and the screen tells them
     * apart by whether user_id is set: an event with no actor at all (a refused
     * request, a failed login - nobody was authenticated to name), and an event
     * whose actor has since been deleted. The trail keeps the id either way,
     * because an audit record must not lose its actor when an account goes.
     */
    private Page<AuditLogResponse> withUserNames(Page<AuditLog> logs) {

        Set<UUID> actorIds = logs.getContent().stream()
                .map(AuditLog::getUser_id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> names = actorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(actorIds).stream()
                        .filter(u -> u.getUsername() != null)
                        .collect(Collectors.toMap(User::getUserId, User::getUsername, (a, b) -> a));

        return logs.map(log -> AuditLogResponse.of(
                log,
                log.getUser_id() == null ? null : names.get(log.getUser_id())));
    }

    /**
     * Clamped so a caller cannot ask for page -1 or for fifty thousand rows in
     * one response. The upper bound is generous enough for a CSV export.
     */
    private PageRequest pageRequest(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 500);
        return PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "timestamp"));
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
