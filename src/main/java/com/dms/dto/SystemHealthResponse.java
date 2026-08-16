package com.dms.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Every field here is a live reading taken when the request arrived, not a
 * cached or simulated value - so a demo of this page and a re-run of it a
 * minute later are allowed to disagree, the way a real monitoring page would.
 */
public record SystemHealthResponse(
        boolean healthy,
        String uptime,
        long activeUsers,
        String lastBackup,
        long apiResponseTimeMs,
        long documentQueueDepth,
        String erpSyncStatus,
        long storageUsedBytes,
        int dbConnectionsActive,
        int dbConnectionsMax,
        boolean ocrAvailable,
        List<ServiceCheck> services,
        LocalDateTime checkedAt
) {
    public record ServiceCheck(String name, boolean healthy, String detail, long latencyMs) {}
}
