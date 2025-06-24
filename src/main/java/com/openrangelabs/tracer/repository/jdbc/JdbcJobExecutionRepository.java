package com.openrangelabs.tracer.repository.jdbc;

import com.openrangelabs.tracer.config.DatabaseType;
import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.model.JobExecution;
import com.openrangelabs.tracer.model.JobStatus;
import com.openrangelabs.tracer.repository.JobExecutionRepository;
import com.openrangelabs.tracer.util.UuidConverter;
import com.openrangelabs.tracer.util.UuidConverterFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC implementation of JobExecutionRepository.
 * Supports PostgreSQL, MySQL, and MariaDB databases.
 */
public class JdbcJobExecutionRepository implements JobExecutionRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcJobExecutionRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TracingProperties properties;
    private final String tableName;
    private final UuidConverter uuidConverter;
    private final DatabaseType databaseType;

    public JdbcJobExecutionRepository(JdbcTemplate jdbcTemplate,
                                      ObjectMapper objectMapper,
                                      TracingProperties properties,
                                      UuidConverterFactory uuidConverterFactory,
                                      DatabaseType databaseType) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tableName = properties.database().jobExecutionsTable();
        this.databaseType = databaseType;
        this.uuidConverter = uuidConverterFactory.getConverter(databaseType);
    }

    // ==================== BASIC CRUD OPERATIONS ====================

    @Override
    @Transactional
    public void save(JobExecution jobExecution) {
        String sql = String.format("""
            INSERT INTO %s (trace_id, job_id, job_type, job_name, parent_job_id, user_id, 
                           status, priority, queue_name, worker_id, input_data, output_data, 
                           error_message, error_stack_trace, retry_count, max_retries, 
                           scheduled_at, started_at, completed_at, duration_ms, memory_usage_mb, 
                           cpu_usage_percent, timestamp, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, %s, ?, ?, ?, %s, %s, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName, getJobStatusCast(), getJsonCast(), getJsonCast());

        try {
            String inputDataJson = jobExecution.inputData() != null ?
                    objectMapper.writeValueAsString(jobExecution.inputData()) : null;
            String outputDataJson = jobExecution.outputData() != null ?
                    objectMapper.writeValueAsString(jobExecution.outputData()) : null;

            jdbcTemplate.update(sql,
                    uuidConverter.convertToDatabase(jobExecution.traceId()),
                    uuidConverter.convertToDatabase(jobExecution.jobId()),
                    jobExecution.jobType(),
                    jobExecution.jobName(),
                    jobExecution.parentJobId() != null ? uuidConverter.convertToDatabase(jobExecution.parentJobId()) : null,
                    jobExecution.userId(),
                    jobExecution.status().name(),
                    jobExecution.priority(),
                    jobExecution.queueName(),
                    jobExecution.workerId(),
                    inputDataJson,
                    outputDataJson,
                    jobExecution.errorMessage(),
                    jobExecution.errorStackTrace(),
                    jobExecution.retryCount(),
                    jobExecution.maxRetries(),
                    jobExecution.scheduledAt() != null ? Timestamp.from(jobExecution.scheduledAt()) : null,
                    jobExecution.startedAt() != null ? Timestamp.from(jobExecution.startedAt()) : null,
                    jobExecution.completedAt() != null ? Timestamp.from(jobExecution.completedAt()) : null,
                    jobExecution.durationMs(),
                    jobExecution.memoryUsageMb(),
                    jobExecution.cpuUsagePercent(),
                    Timestamp.from(jobExecution.timestamp()),
                    Timestamp.from(jobExecution.createdAt()),
                    Timestamp.from(jobExecution.updatedAt())
            );
        } catch (Exception e) {
            logger.error("Failed to save job execution: {}", jobExecution, e);
            throw new RuntimeException("Failed to save job execution", e);
        }
    }

    @Override
    @Transactional
    public void saveAll(List<JobExecution> jobExecutions) {
        if (jobExecutions == null || jobExecutions.isEmpty()) {
            return;
        }

        String sql = String.format("""
            INSERT INTO %s (trace_id, job_id, job_type, job_name, parent_job_id, user_id, 
                           status, priority, queue_name, worker_id, input_data, output_data, 
                           error_message, error_stack_trace, retry_count, max_retries, 
                           scheduled_at, started_at, completed_at, duration_ms, memory_usage_mb, 
                           cpu_usage_percent, timestamp, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, %s, ?, ?, ?, %s, %s, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName, getJobStatusCast(), getJsonCast(), getJsonCast());

        try {
            List<Object[]> batchArgs = jobExecutions.stream()
                    .map(this::convertJobExecutionToArray)
                    .collect(Collectors.toList());

            jdbcTemplate.batchUpdate(sql, batchArgs);
            logger.debug("Batch saved {} job executions", jobExecutions.size());
        } catch (Exception e) {
            logger.error("Failed to batch save job executions", e);
            throw new RuntimeException("Failed to batch save job executions", e);
        }
    }

    @Override
    public Optional<JobExecution> findById(Long id) {
        String sql = String.format("SELECT * FROM %s WHERE id = ?", tableName);
        try {
            JobExecution jobExecution = jdbcTemplate.queryForObject(sql, new Object[]{id}, jobExecutionRowMapper);
            return Optional.ofNullable(jobExecution);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<JobExecution> findByJobId(UUID jobId) {
        String sql = String.format("SELECT * FROM %s WHERE job_id = ?", tableName);
        try {
            JobExecution jobExecution = jdbcTemplate.queryForObject(sql,
                    new Object[]{uuidConverter.convertToDatabase(jobId)}, jobExecutionRowMapper);
            return Optional.ofNullable(jobExecution);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByJobId(UUID jobId) {
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE job_id = ?", tableName);
        Integer count = jdbcTemplate.queryForObject(sql,
                new Object[]{uuidConverter.convertToDatabase(jobId)}, Integer.class);
        return count != null && count > 0;
    }

    // ==================== STATUS OPERATIONS ====================

    @Override
    @Transactional
    public void updateStatus(UUID jobId, JobStatus status) {
        updateStatus(jobId, status, null);
    }

    @Override
    @Transactional
    public void updateStatus(UUID jobId, JobStatus status, String errorMessage) {
        String sql = String.format("""
            UPDATE %s 
            SET status = %s, error_message = ?, updated_at = ? 
            WHERE job_id = ?
            """, tableName, getJobStatusCast());

        jdbcTemplate.update(sql, status.name(), errorMessage, Timestamp.from(Instant.now()),
                uuidConverter.convertToDatabase(jobId));
    }

    @Override
    @Transactional
    public void updateCompletion(UUID jobId, JobStatus status, Object outputData,
                                 Instant completedAt, Long durationMs) {
        String sql = String.format("""
            UPDATE %s 
            SET status = %s, output_data = %s, completed_at = ?, duration_ms = ?, updated_at = ? 
            WHERE job_id = ?
            """, tableName, getJobStatusCast(), getJsonCast());

        try {
            String outputDataJson = outputData != null ?
                    objectMapper.writeValueAsString(outputData) : null;

            jdbcTemplate.update(sql, status.name(), outputDataJson,
                    Timestamp.from(completedAt), durationMs, Timestamp.from(Instant.now()),
                    uuidConverter.convertToDatabase(jobId));
        } catch (Exception e) {
            throw new RuntimeException("Failed to update job completion", e);
        }
    }

    @Override
    @Transactional
    public void updateFailure(UUID jobId, String errorMessage, String stackTrace, Instant failedAt) {
        String sql = String.format("""
            UPDATE %s 
            SET status = %s, error_message = ?, error_stack_trace = ?, completed_at = ?, updated_at = ? 
            WHERE job_id = ?
            """, tableName, getJobStatusCast());

        jdbcTemplate.update(sql, JobStatus.FAILED.name(), errorMessage, stackTrace,
                Timestamp.from(failedAt), Timestamp.from(Instant.now()),
                uuidConverter.convertToDatabase(jobId));
    }

    @Override
    @Transactional
    public void updateRetry(UUID jobId, int retryCount, Instant nextRetryAt) {
        String sql = String.format("""
            UPDATE %s 
            SET status = %s, retry_count = ?, scheduled_at = ?, updated_at = ? 
            WHERE job_id = ?
            """, tableName, getJobStatusCast());

        jdbcTemplate.update(sql, JobStatus.RETRYING.name(), retryCount,
                Timestamp.from(nextRetryAt), Timestamp.from(Instant.now()),
                uuidConverter.convertToDatabase(jobId));
    }

    // ==================== QUERY OPERATIONS ====================

    @Override
    public List<JobExecution> findByTraceId(UUID traceId) {
        String sql = String.format("SELECT * FROM %s WHERE trace_id = ? ORDER BY timestamp", tableName);
        return jdbcTemplate.query(sql, new Object[]{uuidConverter.convertToDatabase(traceId)}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByUserId(String userId) {
        String sql = String.format("SELECT * FROM %s WHERE user_id = ? ORDER BY timestamp DESC", tableName);
        return jdbcTemplate.query(sql, new Object[]{userId}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT * FROM %s 
            WHERE user_id = ? AND timestamp >= ? AND timestamp <= ? 
            ORDER BY timestamp DESC
            """, tableName);
        return jdbcTemplate.query(sql,
                new Object[]{userId, Timestamp.from(startTime), Timestamp.from(endTime)},
                jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByStatus(JobStatus status) {
        String sql = String.format("SELECT * FROM %s WHERE status = %s ORDER BY timestamp DESC",
                tableName, getJobStatusComparison());
        return jdbcTemplate.query(sql, new Object[]{status.name()}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByStatus(JobStatus status, int limit) {
        String sql = String.format("SELECT * FROM %s WHERE status = %s ORDER BY timestamp DESC LIMIT ?",
                tableName, getJobStatusComparison());
        return jdbcTemplate.query(sql, new Object[]{status.name(), limit}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByJobType(String jobType) {
        String sql = String.format("SELECT * FROM %s WHERE job_type = ? ORDER BY timestamp DESC", tableName);
        return jdbcTemplate.query(sql, new Object[]{jobType}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByJobTypeAndTimeRange(String jobType, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT * FROM %s 
            WHERE job_type = ? AND timestamp >= ? AND timestamp <= ? 
            ORDER BY timestamp DESC
            """, tableName);
        return jdbcTemplate.query(sql,
                new Object[]{jobType, Timestamp.from(startTime), Timestamp.from(endTime)},
                jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByQueueName(String queueName) {
        String sql = String.format("SELECT * FROM %s WHERE queue_name = ? ORDER BY timestamp DESC", tableName);
        return jdbcTemplate.query(sql, new Object[]{queueName}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByQueueNameAndStatus(String queueName, JobStatus status) {
        String sql = String.format("SELECT * FROM %s WHERE queue_name = ? AND status = %s ORDER BY timestamp",
                tableName, getJobStatusComparison());
        return jdbcTemplate.query(sql, new Object[]{queueName, status.name()}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByQueueNameAndStatus(String queueName, JobStatus status, int limit) {
        String sql = String.format("""
            SELECT * FROM %s WHERE queue_name = ? AND status = %s ORDER BY timestamp LIMIT ?
            """, tableName, getJobStatusComparison());
        return jdbcTemplate.query(sql, new Object[]{queueName, status.name(), limit}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByParentJobId(UUID parentJobId) {
        String sql = String.format("SELECT * FROM %s WHERE parent_job_id = ? ORDER BY timestamp", tableName);
        return jdbcTemplate.query(sql, new Object[]{uuidConverter.convertToDatabase(parentJobId)}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findByWorkerId(String workerId) {
        String sql = String.format("SELECT * FROM %s WHERE worker_id = ? ORDER BY timestamp DESC", tableName);
        return jdbcTemplate.query(sql, new Object[]{workerId}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findLongRunningJobs(long thresholdMs) {
        String sql = String.format("""
            SELECT * FROM %s 
            WHERE status = %s AND started_at IS NOT NULL 
            AND %s - EXTRACT(EPOCH FROM started_at) * 1000 > ?
            ORDER BY started_at
            """, tableName, getJobStatusComparison(), getCurrentTimestampMs());
        return jdbcTemplate.query(sql, new Object[]{JobStatus.RUNNING.name(), thresholdMs}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findStuckJobs(long thresholdMs) {
        String sql = String.format("""
            SELECT * FROM %s 
            WHERE status = %s 
            AND %s - EXTRACT(EPOCH FROM created_at) * 1000 > ?
            ORDER BY created_at
            """, tableName, getJobStatusComparison(), getCurrentTimestampMs());
        return jdbcTemplate.query(sql, new Object[]{JobStatus.PENDING.name(), thresholdMs}, jobExecutionRowMapper);
    }

    @Override
    public List<JobExecution> findRetryableFailedJobs(int maxRetries) {
        String sql = String.format("""
            SELECT * FROM %s 
            WHERE status = %s AND retry_count < max_retries AND retry_count < ?
            ORDER BY completed_at
            """, tableName, getJobStatusComparison());
        return jdbcTemplate.query(sql, new Object[]{JobStatus.FAILED.name(), maxRetries}, jobExecutionRowMapper);
    }

    // ==================== AGGREGATION OPERATIONS ====================

    @Override
    public long countByTraceId(UUID traceId) {
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE trace_id = ?", tableName);
        Long count = jdbcTemplate.queryForObject(sql, new Object[]{uuidConverter.convertToDatabase(traceId)}, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByStatus(JobStatus status) {
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE status = %s", tableName, getJobStatusComparison());
        Long count = jdbcTemplate.queryForObject(sql, new Object[]{status.name()}, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByJobTypeAndTimeRange(String jobType, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT COUNT(*) FROM %s 
            WHERE job_type = ? AND timestamp >= ? AND timestamp <= ?
            """, tableName);
        Long count = jdbcTemplate.queryForObject(sql,
                new Object[]{jobType, Timestamp.from(startTime), Timestamp.from(endTime)},
                Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByQueueNameAndStatus(String queueName, JobStatus status) {
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE queue_name = ? AND status = %s",
                tableName, getJobStatusComparison());
        Long count = jdbcTemplate.queryForObject(sql, new Object[]{queueName, status.name()}, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public List<JobTypeStatistics> getJobTypeStatistics(Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT job_type,
                   COUNT(*) as total_jobs,
                   COUNT(*) FILTER (WHERE status = %s) as completed_jobs,
                   COUNT(*) FILTER (WHERE status = %s) as failed_jobs,
                   COUNT(*) FILTER (WHERE status = %s) as running_jobs,
                   COUNT(*) FILTER (WHERE status = %s) as pending_jobs,
                   AVG(duration_ms) as avg_duration,
                   %s as p95_duration,
                   AVG(retry_count) as avg_retry_count
            FROM %s 
            WHERE timestamp >= ? AND timestamp <= ?
            GROUP BY job_type
            ORDER BY total_jobs DESC
            """, getJobStatusComparison(), getJobStatusComparison(), getJobStatusComparison(),
                getJobStatusComparison(), getPercentileFunction(95, "duration_ms"), tableName);

        return jdbcTemplate.query(sql,
                new Object[]{JobStatus.COMPLETED.name(), JobStatus.FAILED.name(),
                        JobStatus.RUNNING.name(), JobStatus.PENDING.name(),
                        Timestamp.from(startTime), Timestamp.from(endTime)},
                (rs, rowNum) -> {
                    long totalJobs = rs.getLong("total_jobs");
                    long completedJobs = rs.getLong("completed_jobs");
                    long failedJobs = rs.getLong("failed_jobs");
                    double successRate = totalJobs > 0 ? (completedJobs * 100.0 / totalJobs) : 0.0;

                    return new JobTypeStatistics(
                            rs.getString("job_type"),
                            totalJobs,
                            completedJobs,
                            failedJobs,
                            rs.getLong("running_jobs"),
                            rs.getLong("pending_jobs"),
                            rs.getDouble("avg_duration"),
                            rs.getDouble("p95_duration"),
                            successRate,
                            rs.getDouble("avg_retry_count")
                    );
                });
    }

    @Override
    public List<QueueStatistics> getQueueStatistics() {
        String sql = String.format("""
            SELECT queue_name,
                   COUNT(*) FILTER (WHERE status = %s) as pending_jobs,
                   COUNT(*) FILTER (WHERE status = %s) as running_jobs,
                   COUNT(*) FILTER (WHERE status = %s AND DATE(completed_at) = CURRENT_DATE) as completed_today,
                   COUNT(*) FILTER (WHERE status = %s AND DATE(completed_at) = CURRENT_DATE) as failed_today,
                   AVG(CASE WHEN started_at IS NOT NULL AND scheduled_at IS NOT NULL 
                           THEN EXTRACT(EPOCH FROM started_at - scheduled_at) * 1000 END) as avg_wait_time,
                   AVG(duration_ms) as avg_processing_time,
                   MIN(CASE WHEN status = %s THEN created_at END) as oldest_pending
            FROM %s 
            GROUP BY queue_name
            ORDER BY pending_jobs DESC
            """, getJobStatusComparison(), getJobStatusComparison(), getJobStatusComparison(),
                getJobStatusComparison(), getJobStatusComparison(), tableName);

        return jdbcTemplate.query(sql,
                new Object[]{JobStatus.PENDING.name(), JobStatus.RUNNING.name(),
                        JobStatus.COMPLETED.name(), JobStatus.FAILED.name(), JobStatus.PENDING.name()},
                (rs, rowNum) -> new QueueStatistics(
                        rs.getString("queue_name"),
                        rs.getLong("pending_jobs"),
                        rs.getLong("running_jobs"),
                        rs.getLong("completed_today"),
                        rs.getLong("failed_today"),
                        rs.getDouble("avg_wait_time"),
                        rs.getDouble("avg_processing_time"),
                        rs.getTimestamp("oldest_pending") != null ?
                                rs.getTimestamp("oldest_pending").toInstant() : null
                ));
    }

    @Override
    public List<WorkerStatistics> getWorkerStatistics(Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT worker_id,
                   COUNT(*) as jobs_processed,
                   COUNT(*) FILTER (WHERE status = %s) as jobs_completed,
                   COUNT(*) FILTER (WHERE status = %s) as jobs_failed,
                   AVG(duration_ms) as avg_duration,
                   MAX(updated_at) as last_active
            FROM %s 
            WHERE worker_id IS NOT NULL AND timestamp >= ? AND timestamp <= ?
            GROUP BY worker_id
            ORDER BY jobs_processed DESC
            """, getJobStatusComparison(), getJobStatusComparison(), tableName);

        return jdbcTemplate.query(sql,
                new Object[]{JobStatus.COMPLETED.name(), JobStatus.FAILED.name(),
                        Timestamp.from(startTime), Timestamp.from(endTime)},
                (rs, rowNum) -> {
                    long jobsProcessed = rs.getLong("jobs_processed");
                    long jobsCompleted = rs.getLong("jobs_completed");
                    double successRate = jobsProcessed > 0 ? (jobsCompleted * 100.0 / jobsProcessed) : 0.0;
                    Instant lastActive = rs.getTimestamp("last_active").toInstant();
                    boolean isActive = lastActive.isAfter(Instant.now().minusSeconds(300)); // Active within 5 minutes

                    return new WorkerStatistics(
                            rs.getString("worker_id"),
                            jobsProcessed,
                            jobsCompleted,
                            rs.getLong("jobs_failed"),
                            rs.getDouble("avg_duration"),
                            successRate,
                            lastActive,
                            isActive
                    );
                });
    }

    @Override
    public List<JobPerformanceStatistics> getJobPerformanceStatistics(Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT job_name,
                   job_type,
                   COUNT(*) as execution_count,
                   AVG(duration_ms) as avg_duration,
                   %s as p50_duration,
                   %s as p95_duration,
                   %s as p99_duration,
                   MAX(duration_ms) as max_duration,
                   AVG(memory_usage_mb) as avg_memory,
                   AVG(cpu_usage_percent) as avg_cpu
            FROM %s 
            WHERE timestamp >= ? AND timestamp <= ? AND duration_ms IS NOT NULL
            GROUP BY job_name, job_type
            ORDER BY execution_count DESC
            """, getPercentileFunction(50, "duration_ms"), getPercentileFunction(95, "duration_ms"),
                getPercentileFunction(99, "duration_ms"), tableName);

        return jdbcTemplate.query(sql,
                new Object[]{Timestamp.from(startTime), Timestamp.from(endTime)},
                (rs, rowNum) -> new JobPerformanceStatistics(
                        rs.getString("job_name"),
                        rs.getString("job_type"),
                        rs.getLong("execution_count"),
                        rs.getDouble("avg_duration"),
                        rs.getDouble("p50_duration"),
                        rs.getDouble("p95_duration"),
                        rs.getDouble("p99_duration"),
                        rs.getDouble("max_duration"),
                        rs.getDouble("avg_memory"),
                        rs.getDouble("avg_cpu")
                ));
    }

    @Override
    public List<FailureStatistics> getFailureStatistics(Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT 
                COALESCE(error_message, 'Unknown Error') as error_message,
                COUNT(*) as occurrence_count,
                array_agg(DISTINCT job_type) as affected_job_types,
                array_agg(DISTINCT queue_name) as affected_queues,
                MIN(completed_at) as first_occurrence,
                MAX(completed_at) as last_occurrence
            FROM %s 
            WHERE status = %s AND timestamp >= ? AND timestamp <= ?
            GROUP BY error_message
            ORDER BY occurrence_count DESC
            LIMIT 20
            """, tableName, getJobStatusComparison());

        return jdbcTemplate.query(sql,
                new Object[]{JobStatus.FAILED.name(), Timestamp.from(startTime), Timestamp.from(endTime)},
                (rs, rowNum) -> {
                    // Handle array columns (PostgreSQL specific - would need adaptation for other DBs)
                    String[] jobTypesArray = (String[]) rs.getArray("affected_job_types").getArray();
                    String[] queuesArray = (String[]) rs.getArray("affected_queues").getArray();

                    return new FailureStatistics(
                            "Error", // error type - could be extracted from error message
                            rs.getString("error_message"),
                            rs.getLong("occurrence_count"),
                            Arrays.asList(jobTypesArray),
                            Arrays.asList(queuesArray),
                            rs.getTimestamp("first_occurrence").toInstant(),
                            rs.getTimestamp("last_occurrence").toInstant(),
                            0.0 // failure rate - would need additional calculation
                    );
                });
    }

    // ==================== SEARCH OPERATIONS ====================

    @Override
    public List<JobExecution> searchByCriteria(SearchCriteria criteria) {
        SearchResult<JobExecution> result = searchWithPagination(criteria, 0, Integer.MAX_VALUE);
        return result.content();
    }

    @Override
    public SearchResult<JobExecution> searchWithPagination(SearchCriteria criteria, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // Build WHERE clause
        buildWhereClause(sql, params, criteria);

        // Add ORDER BY
        if (criteria.sortBy() != null) {
            sql.append(" ORDER BY ").append(getSortColumn(criteria.sortBy()));
            if (criteria.sortDirection() != null) {
                sql.append(" ").append(criteria.sortDirection().name());
            }
        } else {
            sql.append(" ORDER BY timestamp DESC");
        }

        // Count total for pagination
        String countSql = "SELECT COUNT(*) FROM " + tableName + " WHERE 1=1" +
                sql.substring(sql.indexOf("WHERE") + 5, sql.indexOf("ORDER BY"));
        Long totalElements = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);
        totalElements = totalElements != null ? totalElements : 0;

        // Add pagination
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<JobExecution> content = jdbcTemplate.query(sql.toString(), params.toArray(), jobExecutionRowMapper);

        return new SearchResult<>(
                content,
                page,
                size,
                totalElements,
                (int) Math.ceil((double) totalElements / size),
                (page + 1) * size < totalElements,
                page > 0
        );
    }

    // ==================== MAINTENANCE OPERATIONS ====================

    @Override
    @Transactional
    public int deleteOlderThan(Instant cutoffTime) {
        String sql = String.format("DELETE FROM %s WHERE created_at < ?", tableName);
        int deletedCount = jdbcTemplate.update(sql, Timestamp.from(cutoffTime));
        logger.info("Deleted {} job executions older than {}", deletedCount, cutoffTime);
        return deletedCount;
    }

    @Override
    @Transactional
    public int deleteByTraceId(UUID traceId) {
        String sql = String.format("DELETE FROM %s WHERE trace_id = ?", tableName);
        int deletedCount = jdbcTemplate.update(sql, uuidConverter.convertToDatabase(traceId));
        logger.info("Deleted {} job executions for trace {}", deletedCount, traceId);
        return deletedCount;
    }

    @Override
    @Transactional
    public int cleanupCompletedJobs(Instant cutoffTime) {
        String sql = String.format("DELETE FROM %s WHERE status IN (%s, %s) AND completed_at < ?",
                tableName, getJobStatusComparison(), getJobStatusComparison());
        int deletedCount = jdbcTemplate.update(sql, JobStatus.COMPLETED.name(), JobStatus.FAILED.name(),
                Timestamp.from(cutoffTime));
        logger.info("Cleaned up {} completed/failed job executions older than {}", deletedCount, cutoffTime);
        return deletedCount;
    }

    @Override
    @Transactional
    public int archiveOldJobs(Instant cutoffTime) {
        // This would typically move data to an archive table
        // For now, we'll just count what would be archived
        String sql = String.format("""
            SELECT COUNT(*) FROM %s 
            WHERE status IN (%s, %s) AND completed_at < ?
            """, tableName, getJobStatusComparison(), getJobStatusComparison());

        Long count = jdbcTemplate.queryForObject(sql,
                new Object[]{JobStatus.COMPLETED.name(), JobStatus.FAILED.name(), Timestamp.from(cutoffTime)},
                Long.class);

        logger.info("Would archive {} job executions older than {}", count, cutoffTime);
        return count != null ? count.intValue() : 0;
    }

    @Override
    public TableStatistics getTableStatistics() {
        return getTableStatisticsFromDatabase();
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private final RowMapper<JobExecution> jobExecutionRowMapper = new RowMapper<JobExecution>() {
        @Override
        public JobExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                JsonNode inputData = null;
                String inputDataStr = rs.getString("input_data");
                if (inputDataStr != null) {
                    inputData = objectMapper.readTree(inputDataStr);
                }

                JsonNode outputData = null;
                String outputDataStr = rs.getString("output_data");
                if (outputDataStr != null) {
                    outputData = objectMapper.readTree(outputDataStr);
                }

                return new JobExecution(
                        rs.getLong("id"),
                        uuidConverter.convertFromDatabase(rs.getObject("trace_id")),
                        uuidConverter.convertFromDatabase(rs.getObject("job_id")),
                        rs.getString("job_type"),
                        rs.getString("job_name"),
                        rs.getObject("parent_job_id") != null ?
                                uuidConverter.convertFromDatabase(rs.getObject("parent_job_id")) : null,
                        rs.getString("user_id"),
                        JobStatus.valueOf(rs.getString("status")),
                        (Integer) rs.getObject("priority"),
                        rs.getString("queue_name"),
                        rs.getString("worker_id"),
                        inputData,
                        outputData,
                        rs.getString("error_message"),
                        rs.getString("error_stack_trace"),
                        (Integer) rs.getObject("retry_count"),
                        (Integer) rs.getObject("max_retries"),
                        rs.getTimestamp("scheduled_at") != null ? rs.getTimestamp("scheduled_at").toInstant() : null,
                        rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                        rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                        (Long) rs.getObject("duration_ms"),
                        (Integer) rs.getObject("memory_usage_mb"),
                        (Double) rs.getObject("cpu_usage_percent"),
                        rs.getTimestamp("timestamp").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                );
            } catch (Exception e) {
                throw new SQLException("Failed to map job execution row", e);
            }
        }
    };

    private Object[] convertJobExecutionToArray(JobExecution jobExecution) {
        try {
            String inputDataJson = jobExecution.inputData() != null ?
                    objectMapper.writeValueAsString(jobExecution.inputData()) : null;
            String outputDataJson = jobExecution.outputData() != null ?
                    objectMapper.writeValueAsString(jobExecution.outputData()) : null;

            return new Object[]{
                    uuidConverter.convertToDatabase(jobExecution.traceId()),
                    uuidConverter.convertToDatabase(jobExecution.jobId()),
                    jobExecution.jobType(),
                    jobExecution.jobName(),
                    jobExecution.parentJobId() != null ? uuidConverter.convertToDatabase(jobExecution.parentJobId()) : null,
                    jobExecution.userId(),
                    jobExecution.status().name(),
                    jobExecution.priority(),
                    jobExecution.queueName(),
                    jobExecution.workerId(),
                    inputDataJson,
                    outputDataJson,
                    jobExecution.errorMessage(),
                    jobExecution.errorStackTrace(),
                    jobExecution.retryCount(),
                    jobExecution.maxRetries(),
                    jobExecution.scheduledAt() != null ? Timestamp.from(jobExecution.scheduledAt()) : null,
                    jobExecution.startedAt() != null ? Timestamp.from(jobExecution.startedAt()) : null,
                    jobExecution.completedAt() != null ? Timestamp.from(jobExecution.completedAt()) : null,
                    jobExecution.durationMs(),
                    jobExecution.memoryUsageMb(),
                    jobExecution.cpuUsagePercent(),
                    Timestamp.from(jobExecution.timestamp()),
                    Timestamp.from(jobExecution.createdAt()),
                    Timestamp.from(jobExecution.updatedAt())
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert job execution to array", e);
        }
    }

    private void buildWhereClause(StringBuilder sql, List<Object> params, SearchCriteria criteria) {
        if (criteria.traceId() != null) {
            sql.append(" AND trace_id = ?");
            params.add(uuidConverter.convertToDatabase(criteria.traceId()));
        }
        if (criteria.jobId() != null) {
            sql.append(" AND job_id = ?");
            params.add(uuidConverter.convertToDatabase(criteria.jobId()));
        }
        if (criteria.jobType() != null) {
            sql.append(" AND job_type = ?");
            params.add(criteria.jobType());
        }
        if (criteria.jobName() != null) {
            sql.append(" AND job_name = ?");
            params.add(criteria.jobName());
        }
        if (criteria.parentJobId() != null) {
            sql.append(" AND parent_job_id = ?");
            params.add(uuidConverter.convertToDatabase(criteria.parentJobId()));
        }
        if (criteria.userId() != null) {
            sql.append(" AND user_id = ?");
            params.add(criteria.userId());
        }
        if (criteria.status() != null) {
            sql.append(" AND status = ").append(getJobStatusComparison());
            params.add(criteria.status().name());
        }
        if (criteria.queueName() != null) {
            sql.append(" AND queue_name = ?");
            params.add(criteria.queueName());
        }
        if (criteria.workerId() != null) {
            sql.append(" AND worker_id = ?");
            params.add(criteria.workerId());
        }
        if (criteria.minPriority() != null) {
            sql.append(" AND priority >= ?");
            params.add(criteria.minPriority());
        }
        if (criteria.maxPriority() != null) {
            sql.append(" AND priority <= ?");
            params.add(criteria.maxPriority());
        }
        if (criteria.minDurationMs() != null) {
            sql.append(" AND duration_ms >= ?");
            params.add(criteria.minDurationMs());
        }
        if (criteria.maxDurationMs() != null) {
            sql.append(" AND duration_ms <= ?");
            params.add(criteria.maxDurationMs());
        }
        if (criteria.startTime() != null) {
            sql.append(" AND timestamp >= ?");
            params.add(Timestamp.from(criteria.startTime()));
        }
        if (criteria.endTime() != null) {
            sql.append(" AND timestamp <= ?");
            params.add(Timestamp.from(criteria.endTime()));
        }
        if (criteria.hasErrors() != null) {
            if (criteria.hasErrors()) {
                sql.append(" AND error_message IS NOT NULL");
            } else {
                sql.append(" AND error_message IS NULL");
            }
        }
        if (criteria.inputDataQuery() != null) {
            sql.append(" AND ").append(getJsonQuery("input_data", criteria.inputDataQuery()));
        }
        if (criteria.outputDataQuery() != null) {
            sql.append(" AND ").append(getJsonQuery("output_data", criteria.outputDataQuery()));
        }
    }

    private String getSortColumn(SearchCriteria.SortBy sortBy) {
        return switch (sortBy) {
            case TIMESTAMP -> "timestamp";
            case DURATION -> "duration_ms";
            case PRIORITY -> "priority";
            case STATUS -> "status";
            case JOB_TYPE -> "job_type";
            case QUEUE_NAME -> "queue_name";
        };
    }

    private TableStatistics getTableStatisticsFromDatabase() {
        try {
            // Get basic counts
            String countSql = String.format("""
                SELECT 
                    COUNT(*) as total_records,
                    COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE) as today_records,
                    COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE - INTERVAL '7 days') as week_records,
                    COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE - INTERVAL '30 days') as month_records,
                    MIN(created_at) as oldest_record,
                    MAX(created_at) as newest_record
                FROM %s
                """, tableName);

            Map<String, Object> counts = jdbcTemplate.queryForMap(countSql);

            // Get status counts
            String statusSql = String.format("""
                SELECT status, COUNT(*) as count 
                FROM %s 
                GROUP BY status
                """, tableName);

            List<Map<String, Object>> statusResults = jdbcTemplate.queryForList(statusSql);
            Map<JobStatus, Long> statusCounts = statusResults.stream()
                    .collect(Collectors.toMap(
                            row -> JobStatus.valueOf((String) row.get("status")),
                            row -> ((Number) row.get("count")).longValue()
                    ));

            // Get table size information (database-specific)
            String sizeInfo = getTableSizeInfo();
            String indexSizeInfo = getIndexSizeInfo();

            return new TableStatistics(
                    ((Number) counts.get("total_records")).longValue(),
                    ((Number) counts.get("today_records")).longValue(),
                    ((Number) counts.get("week_records")).longValue(),
                    ((Number) counts.get("month_records")).longValue(),
                    sizeInfo,
                    indexSizeInfo,
                    counts.get("oldest_record") != null ? ((Timestamp) counts.get("oldest_record")).toInstant() : null,
                    counts.get("newest_record") != null ? ((Timestamp) counts.get("newest_record")).toInstant() : null,
                    statusCounts
            );
        } catch (Exception e) {
            logger.error("Failed to get table statistics", e);
            return new TableStatistics(0, 0, 0, 0, "unknown", "unknown", null, null, Map.of());
        }
    }

    // Database-specific SQL functions
    protected String getJobStatusCast() {
        return switch (databaseType) {
            case POSTGRESQL -> "?::job_status_enum";
            case MYSQL, MARIADB -> "?";
            default -> "?";
        };
    }

    protected String getJobStatusComparison() {
        return switch (databaseType) {
            case POSTGRESQL -> "?::job_status_enum";
            case MYSQL, MARIADB -> "?";
            default -> "?";
        };
    }

    protected String getJsonCast() {
        return switch (databaseType) {
            case POSTGRESQL -> "?::jsonb";
            case MYSQL, MARIADB -> "?";
            default -> "?";
        };
    }

    protected String getPercentileFunction(int percentile, String column) {
        return switch (databaseType) {
            case POSTGRESQL -> String.format("PERCENTILE_CONT(%.2f) WITHIN GROUP (ORDER BY %s)", percentile / 100.0, column);
            case MYSQL, MARIADB -> String.format("PERCENTILE_CONT(%.2f) OVER (ORDER BY %s)", percentile / 100.0, column);
            default -> String.format("AVG(%s)", column); // Fallback
        };
    }

    protected String getCurrentTimestampMs() {
        return switch (databaseType) {
            case POSTGRESQL -> "EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000";
            case MYSQL, MARIADB -> "UNIX_TIMESTAMP() * 1000";
            default -> "CURRENT_TIMESTAMP";
        };
    }

    protected String getJsonQuery(String column, String query) {
        return switch (databaseType) {
            case POSTGRESQL -> String.format("%s @> '%s'::jsonb", column, query);
            case MYSQL, MARIADB -> String.format("JSON_CONTAINS(%s, '%s')", column, query);
            default -> String.format("%s LIKE '%%%s%%'", column, query);
        };
    }

    protected String getTableSizeInfo() {
        return switch (databaseType) {
            case POSTGRESQL -> {
                try {
                    String sql = """
                        SELECT pg_size_pretty(pg_total_relation_size(?::regclass)) as size
                        """;
                    Map<String, Object> result = jdbcTemplate.queryForMap(sql, tableName);
                    yield (String) result.get("size");
                } catch (Exception e) {
                    logger.debug("Could not get PostgreSQL table size", e);
                    yield "unknown";
                }
            }
            case MYSQL, MARIADB -> {
                try {
                    String sql = """
                        SELECT ROUND(((data_length + index_length) / 1024 / 1024), 2) AS size_mb
                        FROM information_schema.tables 
                        WHERE table_name = ? AND table_schema = DATABASE()
                        """;
                    Map<String, Object> result = jdbcTemplate.queryForMap(sql, tableName);
                    Double sizeMb = (Double) result.get("size_mb");
                    yield sizeMb != null ? sizeMb + " MB" : "unknown";
                } catch (Exception e) {
                    logger.debug("Could not get MySQL/MariaDB table size", e);
                    yield "unknown";
                }
            }
            default -> "unknown";
        };
    }

    protected String getIndexSizeInfo() {
        return switch (databaseType) {
            case POSTGRESQL -> {
                try {
                    String sql = """
                        SELECT pg_size_pretty(pg_indexes_size(?::regclass)) as size
                        """;
                    Map<String, Object> result = jdbcTemplate.queryForMap(sql, tableName);
                    yield (String) result.get("size");
                } catch (Exception e) {
                    logger.debug("Could not get PostgreSQL index size", e);
                    yield "unknown";
                }
            }
            case MYSQL, MARIADB -> {
                try {
                    String sql = """
                        SELECT ROUND((index_length / 1024 / 1024), 2) AS size_mb
                        FROM information_schema.tables 
                        WHERE table_name = ? AND table_schema = DATABASE()
                        """;
                    Map<String, Object> result = jdbcTemplate.queryForMap(sql, tableName);
                    Double sizeMb = (Double) result.get("size_mb");
                    yield sizeMb != null ? sizeMb + " MB" : "unknown";
                } catch (Exception e) {
                    logger.debug("Could not get MySQL/MariaDB index size", e);
                    yield "unknown";
                }
            }
            default -> "unknown";
        };
    }
}