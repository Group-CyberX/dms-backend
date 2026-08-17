package com.dms.service;

import com.dms.dao.DocumentRepository;
import com.dms.dao.ErpConnectionRepository;
import com.dms.dao.ProcessingJobRepository;
import com.dms.dao.RefreshTokenRepository;
import com.dms.dto.SystemHealthResponse;
import com.dms.models.ErpConnection;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Reads the system's actual state for the System Health page.
 *
 * Every number here comes from something this process can genuinely observe -
 * its own uptime, a live database round trip, a live S3 reachability check,
 * counts from real tables. There is no simulated uptime percentage and no
 * placeholder for infrastructure (a backup schedule, a metrics agent) that
 * does not exist yet: those are reported as absent rather than faked, because
 * a page that always shows green teaches nobody anything.
 */
@Service
public class SystemHealthService {

    private static final List<String> QUEUED_JOB_STATUSES = List.of("PENDING", "IN_PROGRESS");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final ErpConnectionRepository erpConnectionRepository;
    private final DocumentRepository documentRepository;
    private final MetadataExtractorService metadataExtractorService;
    private final S3Client s3Client;

    @Value("${app.s3.bucket:}")
    private String s3Bucket;

    public SystemHealthService(DataSource dataSource,
                                JdbcTemplate jdbc,
                                RefreshTokenRepository refreshTokenRepository,
                                ProcessingJobRepository processingJobRepository,
                                ErpConnectionRepository erpConnectionRepository,
                                DocumentRepository documentRepository,
                                MetadataExtractorService metadataExtractorService,
                                S3Client s3Client) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.refreshTokenRepository = refreshTokenRepository;
        this.processingJobRepository = processingJobRepository;
        this.erpConnectionRepository = erpConnectionRepository;
        this.documentRepository = documentRepository;
        this.metadataExtractorService = metadataExtractorService;
        this.s3Client = s3Client;
    }

    public SystemHealthResponse check() {
        SystemHealthResponse.ServiceCheck api = checkApi();
        SystemHealthResponse.ServiceCheck database = checkDatabase();
        SystemHealthResponse.ServiceCheck storage = checkStorage();

        boolean healthy = api.healthy() && database.healthy() && storage.healthy();

        return new SystemHealthResponse(
                healthy,
                formatUptime(),
                refreshTokenRepository.countDistinctActiveUsers(LocalDateTime.now()),
                // No backup job exists in this codebase yet (see the requirements-vs-build
                // gap on scheduled backups) - reporting a timestamp here would be inventing
                // evidence for infrastructure that has not been built.
                "Not configured",
                database.latencyMs(),
                processingJobRepository.countByStatusIn(QUEUED_JOB_STATUSES),
                erpSyncStatus(),
                documentRepository.sumFileSizeActive(),
                hikariActiveConnections(),
                hikariMaxPoolSize(),
                metadataExtractorService.isOcrAvailable(),
                List.of(api, database, storage),
                LocalDateTime.now()
        );
    }

    // ------------------------------------------------------------------

    private SystemHealthResponse.ServiceCheck checkApi() {
        // Answering this request at all is the check - there is no separate
        // web-server process to ask.
        return new SystemHealthResponse.ServiceCheck("Web Server", true, "Handling requests", 0);
    }

    private SystemHealthResponse.ServiceCheck checkDatabase() {
        long start = System.nanoTime();
        try {
            jdbc.queryForObject("select 1", Integer.class);
            long latency = (System.nanoTime() - start) / 1_000_000;
            return new SystemHealthResponse.ServiceCheck("Database Server", true, "Reachable", latency);
        } catch (Exception e) {
            long latency = (System.nanoTime() - start) / 1_000_000;
            return new SystemHealthResponse.ServiceCheck("Database Server", false,
                    "Unreachable: " + e.getMessage(), latency);
        }
    }

    private SystemHealthResponse.ServiceCheck checkStorage() {
        if (s3Bucket == null || s3Bucket.isBlank()) {
            return new SystemHealthResponse.ServiceCheck("Storage Service", false, "No bucket configured", 0);
        }
        long start = System.nanoTime();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(s3Bucket).build());
            long latency = (System.nanoTime() - start) / 1_000_000;
            return new SystemHealthResponse.ServiceCheck("Storage Service", true, "Reachable: " + s3Bucket, latency);
        } catch (S3Exception e) {
            long latency = (System.nanoTime() - start) / 1_000_000;
            return new SystemHealthResponse.ServiceCheck("Storage Service", false,
                    "Unreachable: " + e.awsErrorDetails().errorMessage(), latency);
        } catch (Exception e) {
            long latency = (System.nanoTime() - start) / 1_000_000;
            return new SystemHealthResponse.ServiceCheck("Storage Service", false,
                    "Unreachable: " + e.getMessage(), latency);
        }
    }

    private String erpSyncStatus() {
        List<ErpConnection> connections = erpConnectionRepository.findAll().stream()
                .filter(ErpConnection::isActiveOrFalse)
                .toList();

        if (connections.isEmpty()) {
            return "Not configured";
        }
        boolean anyFailed = connections.stream().anyMatch(c -> "FAILED".equals(c.getStatus()));
        boolean anyOk = connections.stream().anyMatch(c -> "OK".equals(c.getStatus()));

        if (anyFailed) return "Degraded";
        if (anyOk) return "Connected";
        return "Unknown";
    }

    private String formatUptime() {
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration d = Duration.ofMillis(uptimeMillis);
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) return String.format("%dd %dh %dm", days, hours, minutes);
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        return String.format("%dm", minutes);
    }

    private int hikariActiveConnections() {
        return hikariPool().map(HikariPoolMXBean::getActiveConnections).orElse(0);
    }

    private int hikariMaxPoolSize() {
        return (dataSource instanceof HikariDataSource hikari)
                ? hikari.getMaximumPoolSize()
                : 0;
    }

    private Optional<HikariPoolMXBean> hikariPool() {
        if (dataSource instanceof HikariDataSource hikari) {
            return Optional.ofNullable(hikari.getHikariPoolMXBean());
        }
        return Optional.empty();
    }
}
