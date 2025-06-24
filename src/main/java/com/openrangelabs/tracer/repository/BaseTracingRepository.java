package com.openrangelabs.tracer.repository;

import com.openrangelabs.tracer.config.DatabaseType;
import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.model.JobExecution;
import com.openrangelabs.tracer.model.JobStatus;
import com.openrangelabs.tracer.model.UserAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base abstract class for tracing repository implementations.
 * Provides common functionality and defines the contract for database-specific implementations.
 */
public abstract class BaseTracingRepository implements TracingRepository {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final TracingProperties properties;
    protected final UserActionRepository userActionRepository;
    protected final JobExecutionRepository jobExecutionRepository;

    // Cache for frequently accessed metadata
    private final Map<String, Object> metadataCache = new ConcurrentHashMap<>();
    private volatile Instant lastHealthCheck = Instant.now();
    private volatile boolean lastHealthStatus = true;

    protected BaseTracingRepository(TracingProperties properties,
                                    UserActionRepository userActionRepository,
                                    JobExecutionRepository jobExecutionRepository) {
        this.properties = properties;
        this.userActionRepository = userActionRepository;
        this.jobExecutionRepository = jobExecutionRepository;
    }

    // ==================== DELEGATED USER ACTION OPERATIONS ====================

    @Override
    public void saveUserAction(UserAction userAction) {
        validateUserAction(userAction);
        userActionRepository.save(userAction);
    }

    @Override
    public void saveUserActionsBatch(List<UserAction> userActions) {
        if (userActions == null || userActions.isEmpty()) {
            return;
        }

        userActions.forEach(this::validateUserAction);
        userActionRepository.saveAll(userActions);
    }

    @Override
    public List<UserAction> findUserActionsByTraceId(String traceId) {
        validateTraceId(traceId);
        return userActionRepository.findByTraceId(traceId);
    }

    @Override
    public List<UserAction> findUserActionsByUserId(String userId, Instant startTime, Instant endTime) {
        validateUserId(userId);
        validateTimeRange(startTime, endTime);
        return userActionRepository.findByUserIdAndTimeRange(userId, startTime, endTime);
    }

    @Override
    public List<UserAction> findUserActionsByAction(String action, Instant startTime, Instant endTime) {
        validateAction(action);
        validateTimeRange(startTime, endTime);
        return userActionRepository.findByActionAndTimeRange(action, startTime, endTime);
    }

    // ==================== DELEGATED JOB EXECUTION OPERATIONS ====================

    @Override
    public void saveJobExecution(JobExecution jobExecution) {
        validateJobExecution(jobExecution);
        jobExecutionRepository.save(jobExecution);
    }

    @Override
    public void saveJobExecutionsBatch(List<JobExecution> jobExecutions) {
        if (jobExecutions == null || jobExecutions.isEmpty()) {
            return;
        }

        jobExecutions.forEach(this::validateJobExecution);
        jobExecutionRepository.saveAll(jobExecutions);
    }

    @Override
    public List<JobExecution> findJobExecutionsByTraceId(String traceId) {
        validateTraceId(traceId);
        return jobExecutionRepository.findByTraceId(traceId);
    }

    @Override
    public Optional<JobExecution> findJobExecutionByJobId(String jobId) {
        validateJobId(jobId);
        return jobExecutionRepository.findByJobId(jobId);
    }

    @Override
    public void updateJobExecutionStatus(String jobId, JobStatus status, String errorMessage) {
        validateJobId(jobId);
        validateJobStatus(status);
        jobExecutionRepository.updateStatus(jobId, status, errorMessage);
    }

    @Override
    public void updateJobExecutionCompletion(String jobId, JobStatus status, Object outputData,
                                             Instant completedAt, Long durationMs) {
        validateJobId(jobId);
        validateJobStatus(status);
        jobExecutionRepository.updateCompletion(jobId, status, outputData, completedAt, durationMs);
    }

    @Override
    public List<JobExecution> findJobExecutionsByStatus(JobStatus status, int limit) {
        validateJobStatus(status);
        validateLimit(limit);
        return jobExecutionRepository.findByStatus(status, limit);
    }

    @Override
    public List<JobExecution> findJobExecutionsByQueueAndStatus(String queueName, JobStatus status, int limit) {
        validateQueueName(queueName);
        validateJobStatus(status);
        validateLimit(limit);
        return jobExecutionRepository.findByQueueNameAndStatus(queueName, status, limit);
    }

    // ==================== TRACE OPERATIONS ====================

    @Override
    public TraceInfo getTraceInfo(String traceId) {
        validateTraceId(traceId);

        List<UserAction> userActions = findUserActionsByTraceId(traceId);
        List<JobExecution> jobExecutions = findJobExecutionsByTraceId(traceId);

        if (userActions.isEmpty() && jobExecutions.isEmpty()) {
            return null; // Trace not found
        }

        Instant firstSeen = getEarliestTimestamp(userActions, jobExecutions);
        Instant lastSeen = getLatestTimestamp(userActions, jobExecutions);

        return new TraceInfo(
                traceId,
                userActions,
                jobExecutions,
                firstSeen,
                lastSeen,
                userActions.size(),
                jobExecutions.size()
        );
    }

    @Override
    public boolean traceExists(String traceId) {
        validateTraceId(traceId);

        // Check if trace exists in either user actions or job executions
        return userActionRepository.countByTraceId(traceId) > 0 ||
                jobExecutionRepository.countByTraceId(traceId) > 0;
    }

    @Override
    public TraceStatistics getTraceStatistics(Instant startTime, Instant endTime) {
        validateTimeRange(startTime, endTime);
        return calculateTraceStatistics(startTime, endTime);
    }

    // ==================== MAINTENANCE OPERATIONS ====================

    @Override
    public int deleteRecordsOlderThan(Instant cutoffTime) {
        validateCutoffTime(cutoffTime);

        int deletedUserActions = userActionRepository.deleteOlderThan(cutoffTime);
        int deletedJobExecutions = jobExecutionRepository.deleteOlderThan(cutoffTime);

        int totalDeleted = deletedUserActions + deletedJobExecutions;

        logger.info("Deleted {} records older than {}: {} user actions, {} job executions",
                totalDeleted, cutoffTime, deletedUserActions, deletedJobExecutions);

        return totalDeleted;
    }

    @Override
    public DatabaseHealthInfo getHealthInfo() {
        // Use cached health status if checked recently (within 1 minute)
        Instant now = Instant.now();
        if (now.minusSeconds(60).isBefore(lastHealthCheck) && lastHealthStatus) {
            return createHealthInfo(true);
        }

        try {
            boolean isHealthy = performHealthCheck();
            lastHealthStatus = isHealthy;
            lastHealthCheck = now;
            return createHealthInfo(isHealthy);
        } catch (Exception e) {
            logger.warn("Health check failed", e);
            lastHealthStatus = false;
            lastHealthCheck = now;
            return createHealthInfo(false);
        }
    }

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();

        try {
            // Get user action table statistics
            UserActionRepository.TableStatistics userActionStats = userActionRepository.getTableStatistics();
            metrics.put("userActions", Map.of(
                    "totalRecords", userActionStats.totalRecords(),
                    "todayRecords", userActionStats.todayRecords(),
                    "tableSize", userActionStats.tableSizeFormatted()
            ));

            // Get job execution table statistics
            JobExecutionRepository.TableStatistics jobExecutionStats = jobExecutionRepository.getTableStatistics();
            metrics.put("jobExecutions", Map.of(
                    "totalRecords", jobExecutionStats.totalRecords(),
                    "todayRecords", jobExecutionStats.todayRecords(),
                    "tableSize", jobExecutionStats.tableSizeFormatted(),
                    "statusCounts", jobExecutionStats.statusCounts()
            ));

            // Add database-specific metrics
            metrics.putAll(getDatabaseSpecificMetrics());

        } catch (Exception e) {
            logger.warn("Failed to collect metrics", e);
            metrics.put("error", e.getMessage());
        }

        return metrics;
    }

    // ==================== TRANSACTION SUPPORT ====================

    @Override
    public <T> T executeInTransaction(TransactionCallback<T> callback) {
        return doExecuteInTransaction(callback);
    }

    // ==================== ABSTRACT METHODS FOR SUBCLASSES ====================

    /**
     * Get the database type for this repository
     */
    public abstract DatabaseType getDatabaseType();

    /**
     * Perform database-specific health check
     */
    protected abstract boolean performHealthCheck();

    /**
     * Get database-specific metrics
     */
    protected abstract Map<String, Object> getDatabaseSpecificMetrics();

    /**
     * Execute callback within a transaction (database-specific implementation)
     */
    protected abstract <T> T doExecuteInTransaction(TransactionCallback<T> callback);

    /**
     * Calculate trace statistics (may be optimized per database)
     */
    protected abstract TraceStatistics calculateTraceStatistics(Instant startTime, Instant endTime);

    // ==================== VALIDATION METHODS ====================

    protected void validateUserAction(UserAction userAction) {
        if (userAction == null) {
            throw new IllegalArgumentException("UserAction cannot be null");
        }
        if (userAction.traceId() == null) {
            throw new IllegalArgumentException("UserAction traceId cannot be null");
        }
        if (userAction.action() == null || userAction.action().trim().isEmpty()) {
            throw new IllegalArgumentException("UserAction action cannot be null or empty");
        }
    }

    protected void validateJobExecution(JobExecution jobExecution) {
        if (jobExecution == null) {
            throw new IllegalArgumentException("JobExecution cannot be null");
        }
        if (jobExecution.traceId() == null) {
            throw new IllegalArgumentException("JobExecution traceId cannot be null");
        }
        if (jobExecution.jobId() == null) {
            throw new IllegalArgumentException("JobExecution jobId cannot be null");
        }
        if (jobExecution.jobType() == null || jobExecution.jobType().trim().isEmpty()) {
            throw new IllegalArgumentException("JobExecution jobType cannot be null or empty");
        }
    }

    protected void validateTraceId(UUID traceId) {
        if (traceId == null) {
            throw new IllegalArgumentException("TraceId cannot be null");
        }
    }

    protected void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }
    }

    protected void validateAction(String action) {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
    }

    protected void validateJobId(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("JobId cannot be null");
        }
    }

    protected void validateJobStatus(JobStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("JobStatus cannot be null");
        }
    }

    protected void validateQueueName(String queueName) {
        if (queueName == null || queueName.trim().isEmpty()) {
            throw new IllegalArgumentException("QueueName cannot be null or empty");
        }
    }

    protected void validateTimeRange(Instant startTime, Instant endTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("StartTime cannot be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("EndTime cannot be null");
        }
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("StartTime cannot be after endTime");
        }
    }

    protected void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (limit > 10000) {
            throw new IllegalArgumentException("Limit cannot exceed 10000");
        }
    }

    protected void validateCutoffTime(Instant cutoffTime) {
        if (cutoffTime == null) {
            throw new IllegalArgumentException("CutoffTime cannot be null");
        }
        if (cutoffTime.isAfter(Instant.now())) {
            throw new IllegalArgumentException("CutoffTime cannot be in the future");
        }
    }

    // ==================== UTILITY METHODS ====================

    private Instant getEarliestTimestamp(List<UserAction> userActions, List<JobExecution> jobExecutions) {
        Instant earliest = null;

        for (UserAction action : userActions) {
            if (earliest == null || action.timestamp().isBefore(earliest)) {
                earliest = action.timestamp();
            }
        }

        for (JobExecution execution : jobExecutions) {
            if (earliest == null || execution.timestamp().isBefore(earliest)) {
                earliest = execution.timestamp();
            }
        }

        return earliest;
    }

    private Instant getLatestTimestamp(List<UserAction> userActions, List<JobExecution> jobExecutions) {
        Instant latest = null;

        for (UserAction action : userActions) {
            if (latest == null || action.timestamp().isAfter(latest)) {
                latest = action.timestamp();
            }
        }

        for (JobExecution execution : jobExecutions) {
            if (latest == null || execution.timestamp().isAfter(latest)) {
                latest = execution.timestamp();
            }
        }

        return latest;
    }

    private DatabaseHealthInfo createHealthInfo(boolean isHealthy) {
        return new DatabaseHealthInfo(
                isHealthy,
                getDatabaseType().getDisplayName(),
                getDatabaseVersion(),
                isHealthy,
                getConnectionCount(),
                getDatabaseSpecificMetrics()
        );
    }

    /**
     * Get database version (to be implemented by subclasses)
     */
    protected String getDatabaseVersion() {
        return "unknown";
    }

    /**
     * Get current connection count (to be implemented by subclasses)
     */
    protected long getConnectionCount() {
        return 0;
    }
}