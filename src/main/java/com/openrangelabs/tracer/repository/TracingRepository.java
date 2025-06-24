package com.openrangelabs.tracer.repository;

import com.openrangelabs.tracer.model.JobExecution;
import com.openrangelabs.tracer.model.JobStatus;
import com.openrangelabs.tracer.model.UserAction;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Main repository interface for tracing operations.
 * Provides unified access to both user actions and job executions.
 */
public interface TracingRepository {

    // ==================== USER ACTION OPERATIONS ====================

    /**
     * Save a single user action
     */
    void saveUserAction(UserAction userAction);

    /**
     * Save multiple user actions in a batch operation
     */
    void saveUserActionsBatch(List<UserAction> userActions);

    /**
     * Find user actions by trace ID
     */
    List<UserAction> findUserActionsByTraceId(UUID traceId);

    /**
     * Find user actions by user ID within a time range
     */
    List<UserAction> findUserActionsByUserId(String userId, Instant startTime, Instant endTime);

    /**
     * Find user actions by action name within a time range
     */
    List<UserAction> findUserActionsByAction(String action, Instant startTime, Instant endTime);

    // ==================== JOB EXECUTION OPERATIONS ====================

    /**
     * Save a single job execution
     */
    void saveJobExecution(JobExecution jobExecution);

    /**
     * Save multiple job executions in a batch operation
     */
    void saveJobExecutionsBatch(List<JobExecution> jobExecutions);

    /**
     * Find job executions by trace ID
     */
    List<JobExecution> findJobExecutionsByTraceId(UUID traceId);

    /**
     * Find job execution by job ID
     */
    Optional<JobExecution> findJobExecutionByJobId(UUID jobId);

    /**
     * Update job execution status
     */
    void updateJobExecutionStatus(UUID jobId, JobStatus status, String errorMessage);

    /**
     * Update job execution with completion data
     */
    void updateJobExecutionCompletion(UUID jobId, JobStatus status, Object outputData,
                                      Instant completedAt, Long durationMs);

    /**
     * Find job executions by status
     */
    List<JobExecution> findJobExecutionsByStatus(JobStatus status, int limit);

    /**
     * Find job executions by queue name and status
     */
    List<JobExecution> findJobExecutionsByQueueAndStatus(String queueName, JobStatus status, int limit);

    // ==================== TRACE OPERATIONS ====================

    /**
     * Get complete trace information (user actions + job executions)
     */
    TraceInfo getTraceInfo(UUID traceId);

    /**
     * Check if a trace exists
     */
    boolean traceExists(UUID traceId);

    /**
     * Get trace statistics for a time period
     */
    TraceStatistics getTraceStatistics(Instant startTime, Instant endTime);

    // ==================== MAINTENANCE OPERATIONS ====================

    /**
     * Delete records older than the specified time
     */
    int deleteRecordsOlderThan(Instant cutoffTime);

    /**
     * Get database health information
     */
    DatabaseHealthInfo getHealthInfo();

    /**
     * Get repository-specific metrics
     */
    Map<String, Object> getMetrics();

    // ==================== TRANSACTION SUPPORT ====================

    /**
     * Execute operations within a transaction
     */
    <T> T executeInTransaction(TransactionCallback<T> callback);

    @FunctionalInterface
    interface TransactionCallback<T> {
        T execute(TracingRepository repository) throws Exception;
    }

    // ==================== DATA TRANSFER OBJECTS ====================

    /**
     * Complete trace information including user actions and job executions
     */
    record TraceInfo(
            UUID traceId,           // Changed from String to UUID
            List<UserAction> userActions,
            List<JobExecution> jobExecutions,
            Instant firstSeen,
            Instant lastSeen,
            int totalUserActions,
            int totalJobExecutions
    ) {}

    /**
     * Statistics about traces in a time period
     */
    record TraceStatistics(
            long totalTraces,
            long totalUserActions,
            long totalJobExecutions,
            long uniqueUsers,
            double averageActionsPerTrace,
            double averageJobsPerTrace,
            Map<String, Long> actionCounts,
            Map<String, Long> jobTypeCounts,
            Map<JobStatus, Long> jobStatusCounts
    ) {}

    /**
     * Database health information
     */
    record DatabaseHealthInfo(
            boolean isHealthy,
            String databaseType,
            String version,
            boolean canConnect,
            long connectionCount,
            Map<String, Object> additionalInfo
    ) {}
}