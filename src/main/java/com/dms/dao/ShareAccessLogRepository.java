package com.dms.dao;

import com.dms.models.ShareAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShareAccessLogRepository extends JpaRepository<ShareAccessLog, UUID> {
}
