package com.dms.dao;

import com.dms.models.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Newest-first page of the trail. The audit table is the one table in this
     * system that only ever grows, so it is never read whole - the API hands
     * back a page and the count, and the database does the sorting.
     */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * The same filters as findByFilters, but paged and counted in the database.
     * Filtering in the browser means transferring the whole table first, which
     * is exactly what this avoids.
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
            "(CAST(:userId AS uuid) IS NULL OR a.user_id = :userId) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(CAST(:fromDate AS localdatetime) IS NULL OR a.timestamp >= :fromDate) AND " +
            "(CAST(:toDate AS localdatetime) IS NULL OR a.timestamp <= :toDate)")
    Page<AuditLog> findByFilters(
            @Param("userId") UUID userId,
            @Param("action") String action,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );

    long countByStatusIgnoreCase(String status);

    @Query("SELECT a FROM AuditLog a WHERE " +
            "(CAST(:userId AS uuid) IS NULL OR a.user_id = :userId) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(CAST(:fromDate AS localdatetime) IS NULL OR a.timestamp >= :fromDate) AND " +
            "(CAST(:toDate AS localdatetime) IS NULL OR a.timestamp <= :toDate) " +
            "ORDER BY a.timestamp DESC")
    List<AuditLog> findByFilters(
            @Param("userId") UUID userId,
            @Param("action") String action,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );
}