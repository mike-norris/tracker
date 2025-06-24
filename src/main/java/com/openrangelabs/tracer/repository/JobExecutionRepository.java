package com.openrangelabs.tracer.repository;

import com.openrangelabs.tracer.model.JobExecution;
import com.openrangelabs.tracer.model.JobStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface specifically for job execution operations.
 * Provides detailed job execution management capabilities.
 */
public interface JobExecutionRepository {

    // ==================== BASIC CRUD OPERATIONS ====================

    /**
     * Save a single job execution
     */
    void save(JobExecution jobExecution);

    /**
     * Save multiple job executions efficiently
     */
    void saveAll(List<JobExecution> jobExecutions);

    /**
     * Find job execution by ID
     */
    Optional<JobExecution> findById(Long id);

    /**
     * Find job execution by job ID (business identifier)
     */
    Optional<JobExecution> findByJobId(String jobId);

    /**
     * Check if job execution exists by job ID
     */
    boolean existsByJobId(String jobId);

    // ==================== STATUS OPERATIONS ====================

    /**
     * Update job execution status
     */
    void updateStatus(String jobId, JobStatus status);

    /**
     * Update job execution status with error message
     */
    void updateStatus(String jobId, JobStatus status, String errorMessage);

    /**
     * Update job execution with completion data
     */
    void updateCompletion(String jobId, JobStatus status, Object outputData,
                          Instant completedAt, Long durationMs);

    /**
     * Update job execution with failure data
     */
    void updateFailure(String jobId, String errorMessage, String stackTrace, Instant failedAt);

    /**
     * Update job execution retry information
     */
    void updateRetry(String jobId, int retryCount, Instant nextRetryAt);

    // ==================== QUERY OPERATIONS ====================

    /**
     * Find all job executions for a specific trace
     */
    List<JobExecution> findByTraceId(String traceId);

    /**
     * Find job executions by user ID
     */
    List<JobExecution> findByUserId(String userId);

    /**
     * Find job executions by user ID within time range
     */
    List<JobExecution> findByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime);

    /**
     * Find job executions by status
     */
    List<JobExecution> findByStatus(JobStatus status);

    /**
     * Find job executions by status with limit
     */
    List<JobExecution> findByStatus(JobStatus status, int limit);

    /**
     * Find job executions by job type
     */
    List<JobExecution> findByJobType(String jobType);

    /**
     * Find job executions by job type within time range
     */
    List<JobExecution> findByJobTypeAndTimeRange(String jobType, Instant startTime, Instant endTime);

    /**
     * Find job executions by queue name
     */
    List<JobExecution> findByQueueName(String queueName);

    /**
     * Find job executions by queue name and status
     */
    List<JobExecution> findByQueueNameAndStatus(String queueName, JobStatus status);

    /**
     * Find job executions by queue name and status with limit
     */
    List<JobExecution> findByQueueNameAndStatus(String queueName, JobStatus status, int limit);

    /**
     * Find child job executions by parent job ID
     */
    List<JobExecution> findByParentJobId(String parentJobId);

    /**
     * Find job executions by worker ID
     */
    List<JobExecution> findByWorkerId(String workerId);

    /**
     * Find long-running jobs (started but not completed within threshold)
     */
    List<JobExecution> findLongRunningJobs(long thresholdMs);

    /**
     * Find stuck jobs (pending for longer than threshold)
     */
    List<JobExecution> findStuckJobs(long thresholdMs);

    /**
     * Find failed jobs that can be retried
     */
    List<JobExecution> findRetryableFailedJobs(int maxRetries);

    // ==================== AGGREGATION OPERATIONS ====================

    /**
     * Count job executions by trace ID
     */
    long countByTraceId(String traceId);

    /**
     * Count job executions by status
     */
    long countByStatus(JobStatus status);

    /**
     * Count job executions by job type within time range
     */
    long countByJobTypeAndTimeRange(String jobType, Instant startTime, Instant endTime);

    /**
     * Count job executions by queue name and status
     */
    long countByQueueNameAndStatus(String queueName, JobStatus status);

    /**
     * Get job type statistics for time period
     */
    List<JobTypeStatistics> getJobTypeStatistics(Instant startTime, Instant endTime);

    /**
     * Get queue statistics
     */
    List<QueueStatistics> getQueueStatistics();

    /**
     * Get worker statistics
     */
    List<WorkerStatistics> getWorkerStatistics(Instant startTime, Instant endTime);

    /**
     * Get job performance statistics
     */
    List<JobPerformanceStatistics> getJobPerformanceStatistics(Instant startTime, Instant endTime);

    /**
     * Get failure analysis statistics
     */
    List<FailureStatistics> getFailureStatistics(Instant startTime, Instant endTime);

    // ==================== SEARCH OPERATIONS ====================

    /**
     * Search job executions by criteria
     */
    List<JobExecution> searchByCriteria(SearchCriteria criteria);

    /**
     * Search job executions with pagination
     */
    SearchResult<JobExecution> searchWithPagination(SearchCriteria criteria, int page, int size);

    // ==================== MAINTENANCE OPERATIONS ====================

    /**
     * Delete job executions older than specified time
     */
    int deleteOlderThan(Instant cutoffTime);

    /**
     * Delete job executions by trace ID
     */
    int deleteByTraceId(String traceId);

    /**
     * Clean up completed jobs older than specified time
     */
    int cleanupCompletedJobs(Instant cutoffTime);

    /**
     * Archive old job executions to separate storage
     */
    int archiveOldJobs(Instant cutoffTime);

    /**
     * Get table statistics
     */
    TableStatistics getTableStatistics();

    // ==================== DATA TRANSFER OBJECTS ====================

    /**
     * Search criteria for job executions
     */
    record SearchCriteria(
            String traceId,
            String jobId,
            String jobType,
            String jobName,
            String parentJobId,
            String userId,
            JobStatus status,
            String queueName,
            String workerId,
            Integer minPriority,
            Integer maxPriority,
            Long minDurationMs,
            Long maxDurationMs,
            Instant startTime,
            Instant endTime,
            Boolean hasErrors,
            String inputDataQuery, // Database-specific query for input_data field
            String outputDataQuery, // Database-specific query for output_data field
            SortBy sortBy,
            SortDirection sortDirection
    ) {
        public enum SortBy {
            TIMESTAMP, DURATION, PRIORITY, STATUS, JOB_TYPE, QUEUE_NAME
        }

        public enum SortDirection {
            ASC, DESC
        }
    }

    /**
     * Paginated search result
     */
    record SearchResult<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {}

    /**
     * Job type statistics
     */
    record JobTypeStatistics(
            String jobType,
            long totalJobs,
            long completedJobs,
            long failedJobs,
            long runningJobs,
            long pendingJobs,
            double averageDurationMs,
            double p95DurationMs,
            double successRate,
            double averageRetryCount
    ) {}

    /**
     * Queue statistics
     */
    record QueueStatistics(
            String queueName,
            long pendingJobs,
            long runningJobs,
            long completedJobsToday,
            long failedJobsToday,
            double averageWaitTimeMs,
            double averageProcessingTimeMs,
            Instant oldestPendingJob
    ) {}

    /**
     * Worker statistics
     */
    record WorkerStatistics(
            String workerId,
            long jobsProcessed,
            long jobsCompleted,
            long jobsFailed,
            double averageJobDurationMs,
            double successRate,
            Instant lastActiveTime,
            boolean isActive
    ) {}

    /**
     * Job performance statistics
     */
    record JobPerformanceStatistics(
            String jobName,
            String jobType,
            long executionCount,
            double averageDurationMs,
            double p50DurationMs,
            double p95DurationMs,
            double p99DurationMs,
            double maxDurationMs,
            double averageMemoryUsageMb,
            double averageCpuUsagePercent
    ) {}

    /**
     * Failure analysis statistics
     */
    record FailureStatistics(
            String errorType,
            String errorMessage,
            long occurrenceCount,
            List<String> affectedJobTypes,
            List<String> affectedQueues,
            Instant firstOccurrence,
            Instant lastOccurrence,
            double failureRate
    ) {}

    /**
     * Table statistics
     */
    record TableStatistics(
            long totalRecords,
            long todayRecords,
            long thisWeekRecords,
            long thisMonthRecords,
            String tableSizeFormatted,
            String indexSizeFormatted,
            Instant oldestRecord,
            Instant newestRecord,
            Map<JobStatus, Long> statusCounts
    ) {}
}