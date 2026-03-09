package com.dms.dao;
import com.dms.models.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public  interface AuditLogRepository extends JpaRepository<AuditLog, Long>{}
