package com.openrangelabs.tracer.repository;

import com.openrangelabs.tracer.model.UserAction;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface specifically for user action operations.
 * Provides detailed user action management capabilities.
 */
public interface UserActionRepository {

    // ==================== BASIC CRUD OPERATIONS ====================

    /**
     * Save a single user action
     */
    void save(UserAction userAction);

    /**
     * Save multiple user actions efficiently
     */
    void saveAll(List<UserAction> userActions);

    /**
     * Batch save method (alias for saveAll for backward compatibility)
     */
    default void saveBatch(List<UserAction> userActions) {
        saveAll(userActions);
    }

    /**
     * Find user action by ID
     */
    Optional<UserAction> findById(Long id);

    /**
     * Check if user action exists by ID
     */
    boolean existsById(Long id);

    // ==================== QUERY OPERATIONS ====================

    /**
     * Find all user actions for a specific trace
     */
    List<UserAction> findByTraceId(UUID traceId);

    /**
     * Find user actions by user ID
     */
    List<UserAction> findByUserId(String userId);

    /**
     * Find user actions by user ID within time range
     */
    List<UserAction> findByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime);

    /**
     * Find user actions by action name
     */
    List<UserAction> findByAction(String action);

    /**
     * Find user actions by action name within time range
     */
    List<UserAction> findByActionAndTimeRange(String action, Instant startTime, Instant endTime);

    /**
     * Find user actions by session ID
     */
    List<UserAction> findBySessionId(String sessionId);

    /**
     * Find user actions by IP address
     */
    List<UserAction> findByIpAddress(String ipAddress);

    /**
     * Find user actions by endpoint
     */
    List<UserAction> findByEndpoint(String endpoint);

    /**
     * Find user actions by HTTP method
     */
    List<UserAction> findByHttpMethod(String httpMethod);

    /**
     * Find user actions with response status in range
     */
    List<UserAction> findByResponseStatusRange(int minStatus, int maxStatus, Instant startTime, Instant endTime);

    /**
     * Find slow user actions (duration above threshold)
     */
    List<UserAction> findSlowActions(long durationThresholdMs, Instant startTime, Instant endTime);

    // ==================== AGGREGATION OPERATIONS ====================

    /**
     * Count user actions by trace ID
     */
    long countByTraceId(UUID traceId);

    /**
     * Count user actions by user ID within time range
     */
    long countByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime);

    /**
     * Count user actions by action name within time range
     */
    long countByActionAndTimeRange(String action, Instant startTime, Instant endTime);

    /**
     * Get unique user count within time range
     */
    long countUniqueUsersInTimeRange(Instant startTime, Instant endTime);

    /**
     * Get action statistics for time period
     */
    List<ActionStatistics> getActionStatistics(Instant startTime, Instant endTime);

    /**
     * Get user activity statistics
     */
    List<UserActivityStatistics> getUserActivityStatistics(Instant startTime, Instant endTime, int limit);

    /**
     * Get endpoint performance statistics
     */
    List<EndpointStatistics> getEndpointStatistics(Instant startTime, Instant endTime);

    /**
     * Get error rate statistics by time bucket
     */
    List<ErrorRateStatistics> getErrorRateStatistics(Instant startTime, Instant endTime,
                                                     TimeBucket timeBucket);

    // ==================== SEARCH OPERATIONS ====================

    /**
     * Search user actions by criteria
     */
    List<UserAction> searchByCriteria(SearchCriteria criteria);

    /**
     * Search user actions with pagination
     */
    SearchResult<UserAction> searchWithPagination(SearchCriteria criteria, int page, int size);

    // ==================== MAINTENANCE OPERATIONS ====================

    /**
     * Delete user actions older than specified time
     */
    int deleteOlderThan(Instant cutoffTime);

    /**
     * Delete user actions by trace ID
     */
    int deleteByTraceId(UUID traceId);

    /**
     * Get table statistics
     */
    TableStatistics getTableStatistics();

    // ==================== DATA TRANSFER OBJECTS ====================

    /**
     * Search criteria for user actions
     */
    record SearchCriteria(
            UUID traceId,        // Changed from String to UUID
            String userId,
            String action,
            String sessionId,
            String ipAddress,
            String endpoint,
            String httpMethod,
            Integer minResponseStatus,
            Integer maxResponseStatus,
            Long minDurationMs,
            Long maxDurationMs,
            Instant startTime,
            Instant endTime,
            String actionDataQuery, // Database-specific query for action_data field
            SortBy sortBy,
            SortDirection sortDirection
    ) {
        public enum SortBy {
            TIMESTAMP, DURATION, RESPONSE_STATUS, ACTION, USER_ID
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
     * Action statistics aggregation
     */
    record ActionStatistics(
            String action,
            long count,
            double averageDurationMs,
            double p95DurationMs,
            long errorCount,
            double errorRate
    ) {}

    /**
     * User activity statistics
     */
    record UserActivityStatistics(
            String userId,
            long actionCount,
            long uniqueSessions,
            Instant firstAction,
            Instant lastAction,
            double averageDurationMs
    ) {}

    /**
     * Endpoint performance statistics
     */
    record EndpointStatistics(
            String endpoint,
            String httpMethod,
            long requestCount,
            double averageDurationMs,
            double p95DurationMs,
            long errorCount,
            double errorRate,
            Map<Integer, Long> statusCodeCounts
    ) {}

    /**
     * Error rate statistics by time bucket
     */
    record ErrorRateStatistics(
            Instant bucketStart,
            Instant bucketEnd,
            long totalRequests,
            long errorRequests,
            double errorRate
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
            Instant newestRecord
    ) {}

    /**
     * Time bucket for aggregations
     */
    enum TimeBucket {
        MINUTE, HOUR, DAY, WEEK, MONTH
    }
}