package com.dms.dao;

import com.dms.models.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:userId IS NULL OR a.user_id = :userId) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(:fromDate IS NULL OR a.timestamp >= :fromDate) AND " +
            "(:toDate IS NULL OR a.timestamp <= :toDate) " +
            "ORDER BY a.timestamp DESC")
    List<AuditLog> findByFilters(
            @Param("userId") UUID userId,
            @Param("action") String action,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );
}