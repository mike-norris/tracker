package com.openrangelabs.tracer.repository.jdbc;

import com.openrangelabs.tracer.config.DatabaseType;
import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.model.UserAction;
import com.openrangelabs.tracer.repository.UserActionRepository;
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
 * JDBC implementation of UserActionRepository.
 * Supports PostgreSQL, MySQL, and MariaDB databases.
 */
public class JdbcUserActionRepository implements UserActionRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcUserActionRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TracingProperties properties;
    private final String tableName;
    private final UuidConverter uuidConverter;
    private final DatabaseType databaseType;

    public JdbcUserActionRepository(JdbcTemplate jdbcTemplate,
                                    ObjectMapper objectMapper,
                                    TracingProperties properties,
                                    UuidConverterFactory uuidConverterFactory,
                                    DatabaseType databaseType) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tableName = properties.database().userActionsTable();
        this.databaseType = databaseType;
        this.uuidConverter = uuidConverterFactory.getConverter(databaseType);
    }

    // ==================== BASIC CRUD OPERATIONS ====================

    @Override
    @Transactional
    public void save(UserAction userAction) {
        String sql = String.format("""
            INSERT INTO %s (trace_id, user_id, action, action_data, session_id, 
                           ip_address, user_agent, http_method, endpoint, request_size, 
                           response_status, response_size, duration_ms, timestamp, created_at)
            VALUES (?, ?, ?, %s, ?, %s, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName, getJsonCast(), getInetCast());

        try {
            String actionDataJson = userAction.actionData() != null ?
                    objectMapper.writeValueAsString(userAction.actionData()) : null;

            jdbcTemplate.update(sql,
                    uuidConverter.convertToDatabase(userAction.traceId()),
                    userAction.userId(),
                    userAction.action(),
                    actionDataJson,
                    userAction.sessionId(),
                    userAction.ipAddress(),
                    userAction.userAgent(),
                    userAction.httpMethod(),
                    userAction.endpoint(),
                    userAction.requestSize(),
                    userAction.responseStatus(),
                    userAction.responseSize(),
                    userAction.durationMs(),
                    Timestamp.from(userAction.timestamp()),
                    Timestamp.from(userAction.createdAt())
            );
        } catch (Exception e) {
            logger.error("Failed to save user action: {}", userAction, e);
            throw new RuntimeException("Failed to save user action", e);
        }
    }

    @Override
    @Transactional
    public void saveAll(List<UserAction> userActions) {
        if (userActions == null || userActions.isEmpty()) {
            return;
        }

        String sql = String.format("""
            INSERT INTO %s (trace_id, user_id, action, action_data, session_id, 
                           ip_address, user_agent, http_method, endpoint, request_size, 
                           response_status, response_size, duration_ms, timestamp, created_at)
            VALUES (?, ?, ?, %s, ?, %s, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName, getJsonCast(), getInetCast());

        try {
            List<Object[]> batchArgs = userActions.stream()
                    .map(this::convertUserActionToArray)
                    .collect(Collectors.toList());

            jdbcTemplate.batchUpdate(sql, batchArgs);
            logger.debug("Batch saved {} user actions", userActions.size());
        } catch (Exception e) {
            logger.error("Failed to batch save user actions", e);
            throw new RuntimeException("Failed to batch save user actions", e);
        }
    }

    @Override
    public Optional<UserAction> findById(Long id) {
        String sql = String.format("SELECT * FROM %s WHERE id = ?", tableName);
        try {
            UserAction userAction = jdbcTemplate.queryForObject(sql, new Object[]{id}, userActionRowMapper);
            return Optional.ofNullable(userAction);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsById(Long id) {
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE id = ?", tableName);
        Integer count = jdbcTemplate.queryForObject(sql, new Object[]{id}, Integer.class);
        return count != null && count > 0;
    }

    // ==================== QUERY OPERATIONS ====================

    @Override
    public List<UserAction> findByTraceId(UUID traceId) {
        String sql = String.format("SELECT * FROM %s WHERE trace_id = ? ORDER BY timestamp", tableName);
        return jdbcTemplate.query(sql, new Object[]{uuidConverter.convertToDatabase(traceId)}, userActionRowMapper);
    }

    @Override
    public List<UserAction> findByUserId(String userId) {
        String sql = String.format("SELECT * FROM %s WHERE user_id = ? ORDER BY timestamp DESC", tableName);
        return jdbcTemplate.query(sql, new Object[]{userId}, userActionRowMapper);
    }

    @Override
    public List<UserAction> findByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT * FROM %s 
            WHERE user_id = ? AND timestamp >= ? AND timestamp <= ? 
            ORDER BY timestamp DESC
            """, tableName);
        return jdbcTemplate.query(sql,
                new Object[]{userId, Timestamp.from(startTime), Timestamp.from(endTime)},
                userActionRowMapper);
    }

    @Override
    public List<UserAction> findByAction(String action) {
        String sql = String.format("SELECT * FROM %s WHERE action = ? ORDER BY timestamp DESC", tableName);
        return jdbcTemplate.query(sql, new Object[]{action}, userActionRowMapper);
    }

    @Override
    public List<UserAction> findByActionAndTimeRange(String action, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT * FROM %s 
            WHERE action = ? AND timestamp >= ? AND timestamp <= ? 
            ORDER BY timestamp DESC
            """, tableName);
        return jdbcTemplate.query(sql,
                new Object[]{action, Timestamp.from(startTime), Timestamp.from(endTime)},
                userActionRowMapper);
    }

    @Override
    public List<UserAction> findBySessionId(String sessionId) {
        String sql = String.format("SELECT * FROM %s WHERE session_id = ? ORDER BY timestamp", tableName);
        return jdbcTemplate.query(sql, new Object[]{sessionId}, userActionRowMapper);
    }

    @Override
    public List<UserAction> findByIpAddress(String ipAddress) {
        String sql = String.format("SELECT * FROM %s WHERE ip_address = %s ORDER BY timestamp DESC",
                tableName, getInetComparison());
        return jdbcTemplate.query(sql, new Object[]{ipAddress}, userActionRowMapper);
    }

    @Override
    public List<UserAction> findByEndpoint(String endpoint) {
        String sql = String.format("SELECT * FROM %s WHERE endpoint = ? ORDER BY timestamp DESC", tableName);
        return jdbcTemplate.query(sql, new Object[]{endpoint}, userActionRowMapper);
    }

    @Override
    public List<UserAction> findByHttpMethod(String httpMethod) {
        String sql = String.format("SELECT * FROM %s WHERE http_method = ? ORDER BY timestamp DESC", tableName);
        return jdbcTemplate.query(sql, new Object[]{httpMethod}, userActionRowMapper);
    }

    @Override
    public List<UserAction> findByResponseStatusRange(int minStatus, int maxStatus, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT * FROM %s 
            WHERE response_status >= ? AND response_status <= ? 
            AND timestamp >= ? AND timestamp <= ? 
            ORDER BY timestamp DESC
            """, tableName);
        return jdbcTemplate.query(sql,
                new Object[]{minStatus, maxStatus, Timestamp.from(startTime), Timestamp.from(endTime)},
                userActionRowMapper);
    }

    @Override
    public List<UserAction> findSlowActions(long durationThresholdMs, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT * FROM %s 
            WHERE duration_ms >= ? AND timestamp >= ? AND timestamp <= ? 
            ORDER BY duration_ms DESC
            """, tableName);
        return jdbcTemplate.query(sql,
                new Object[]{durationThresholdMs, Timestamp.from(startTime), Timestamp.from(endTime)},
                userActionRowMapper);
    }

    // ==================== AGGREGATION OPERATIONS ====================

    @Override
    public long countByTraceId(UUID traceId) {
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE trace_id = ?", tableName);
        Long count = jdbcTemplate.queryForObject(sql, new Object[]{uuidConverter.convertToDatabase(traceId)}, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT COUNT(*) FROM %s 
            WHERE user_id = ? AND timestamp >= ? AND timestamp <= ?
            """, tableName);
        Long count = jdbcTemplate.queryForObject(sql,
                new Object[]{userId, Timestamp.from(startTime), Timestamp.from(endTime)},
                Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByActionAndTimeRange(String action, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT COUNT(*) FROM %s 
            WHERE action = ? AND timestamp >= ? AND timestamp <= ?
            """, tableName);
        Long count = jdbcTemplate.queryForObject(sql,
                new Object[]{action, Timestamp.from(startTime), Timestamp.from(endTime)},
                Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countUniqueUsersInTimeRange(Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT COUNT(DISTINCT user_id) FROM %s 
            WHERE timestamp >= ? AND timestamp <= ?
            """, tableName);
        Long count = jdbcTemplate.queryForObject(sql,
                new Object[]{Timestamp.from(startTime), Timestamp.from(endTime)},
                Long.class);
        return count != null ? count : 0;
    }

    @Override
    public List<ActionStatistics> getActionStatistics(Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT action,
                   COUNT(*) as count,
                   AVG(duration_ms) as avg_duration,
                   %s as p95_duration,
                   COUNT(*) FILTER (WHERE response_status >= 400) as error_count
            FROM %s 
            WHERE timestamp >= ? AND timestamp <= ?
            GROUP BY action
            ORDER BY count DESC
            """, getPercentileFunction(95, "duration_ms"), tableName);

        return jdbcTemplate.query(sql,
                new Object[]{Timestamp.from(startTime), Timestamp.from(endTime)},
                (rs, rowNum) -> new ActionStatistics(
                        rs.getString("action"),
                        rs.getLong("count"),
                        rs.getDouble("avg_duration"),
                        rs.getDouble("p95_duration"),
                        rs.getLong("error_count"),
                        rs.getLong("error_count") * 100.0 / rs.getLong("count")
                ));
    }

    @Override
    public List<UserActivityStatistics> getUserActivityStatistics(Instant startTime, Instant endTime, int limit) {
        String sql = String.format("""
            SELECT user_id,
                   COUNT(*) as action_count,
                   COUNT(DISTINCT session_id) as unique_sessions,
                   MIN(timestamp) as first_action,
                   MAX(timestamp) as last_action,
                   AVG(duration_ms) as avg_duration
            FROM %s 
            WHERE timestamp >= ? AND timestamp <= ?
            GROUP BY user_id
            ORDER BY action_count DESC
            LIMIT ?
            """, tableName);

        return jdbcTemplate.query(sql,
                new Object[]{Timestamp.from(startTime), Timestamp.from(endTime), limit},
                (rs, rowNum) -> new UserActivityStatistics(
                        rs.getString("user_id"),
                        rs.getLong("action_count"),
                        rs.getLong("unique_sessions"),
                        rs.getTimestamp("first_action").toInstant(),
                        rs.getTimestamp("last_action").toInstant(),
                        rs.getDouble("avg_duration")
                ));
    }

    @Override
    public List<EndpointStatistics> getEndpointStatistics(Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT endpoint,
                   http_method,
                   COUNT(*) as request_count,
                   AVG(duration_ms) as avg_duration,
                   %s as p95_duration,
                   COUNT(*) FILTER (WHERE response_status >= 400) as error_count
            FROM %s 
            WHERE timestamp >= ? AND timestamp <= ?
            GROUP BY endpoint, http_method
            ORDER BY request_count DESC
            """, getPercentileFunction(95, "duration_ms"), tableName);

        return jdbcTemplate.query(sql,
                new Object[]{Timestamp.from(startTime), Timestamp.from(endTime)},
                (rs, rowNum) -> {
                    long requestCount = rs.getLong("request_count");
                    long errorCount = rs.getLong("error_count");

                    // Get status code counts for this endpoint/method
                    Map<Integer, Long> statusCounts = getStatusCodeCounts(
                            rs.getString("endpoint"),
                            rs.getString("http_method"),
                            startTime,
                            endTime
                    );

                    return new EndpointStatistics(
                            rs.getString("endpoint"),
                            rs.getString("http_method"),
                            requestCount,
                            rs.getDouble("avg_duration"),
                            rs.getDouble("p95_duration"),
                            errorCount,
                            errorCount * 100.0 / requestCount,
                            statusCounts
                    );
                });
    }

    @Override
    public List<ErrorRateStatistics> getErrorRateStatistics(Instant startTime, Instant endTime, TimeBucket timeBucket) {
        String dateFormat = getDateTruncFunction(timeBucket);
        String sql = String.format("""
            SELECT %s as bucket_start,
                   COUNT(*) as total_requests,
                   COUNT(*) FILTER (WHERE response_status >= 400) as error_requests
            FROM %s 
            WHERE timestamp >= ? AND timestamp <= ?
            GROUP BY bucket_start
            ORDER BY bucket_start
            """, dateFormat, tableName);

        return jdbcTemplate.query(sql,
                new Object[]{Timestamp.from(startTime), Timestamp.from(endTime)},
                (rs, rowNum) -> {
                    Instant bucketStart = rs.getTimestamp("bucket_start").toInstant();
                    long totalRequests = rs.getLong("total_requests");
                    long errorRequests = rs.getLong("error_requests");

                    return new ErrorRateStatistics(
                            bucketStart,
                            getEndOfBucket(bucketStart, timeBucket),
                            totalRequests,
                            errorRequests,
                            totalRequests > 0 ? errorRequests * 100.0 / totalRequests : 0.0
                    );
                });
    }

    // ==================== SEARCH OPERATIONS ====================

    @Override
    public List<UserAction> searchByCriteria(SearchCriteria criteria) {
        SearchResult<UserAction> result = searchWithPagination(criteria, 0, Integer.MAX_VALUE);
        return result.content();
    }

    @Override
    public SearchResult<UserAction> searchWithPagination(SearchCriteria criteria, int page, int size) {
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

        List<UserAction> content = jdbcTemplate.query(sql.toString(), params.toArray(), userActionRowMapper);

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
        logger.info("Deleted {} user actions older than {}", deletedCount, cutoffTime);
        return deletedCount;
    }

    @Override
    @Transactional
    public int deleteByTraceId(UUID traceId) {
        String sql = String.format("DELETE FROM %s WHERE trace_id = ?", tableName);
        int deletedCount = jdbcTemplate.update(sql, uuidConverter.convertToDatabase(traceId));
        logger.info("Deleted {} user actions for trace {}", deletedCount, traceId);
        return deletedCount;
    }

    @Override
    public TableStatistics getTableStatistics() {
        return getTableStatisticsFromDatabase();
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private final RowMapper<UserAction> userActionRowMapper = new RowMapper<UserAction>() {
        @Override
        public UserAction mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                JsonNode actionData = null;
                String actionDataStr = rs.getString("action_data");
                if (actionDataStr != null) {
                    actionData = objectMapper.readTree(actionDataStr);
                }

                return new UserAction(
                        rs.getLong("id"),
                        uuidConverter.convertFromDatabase(rs.getObject("trace_id")),
                        rs.getString("user_id"),
                        rs.getString("action"),
                        actionData,
                        rs.getString("session_id"),
                        rs.getString("ip_address"),
                        rs.getString("user_agent"),
                        rs.getString("http_method"),
                        rs.getString("endpoint"),
                        (Integer) rs.getObject("request_size"),
                        (Integer) rs.getObject("response_status"),
                        (Integer) rs.getObject("response_size"),
                        (Long) rs.getObject("duration_ms"),
                        rs.getTimestamp("timestamp").toInstant(),
                        rs.getTimestamp("created_at").toInstant()
                );
            } catch (Exception e) {
                throw new SQLException("Failed to map user action row", e);
            }
        }
    };

    private Object[] convertUserActionToArray(UserAction userAction) {
        try {
            String actionDataJson = userAction.actionData() != null ?
                    objectMapper.writeValueAsString(userAction.actionData()) : null;

            return new Object[]{
                    uuidConverter.convertToDatabase(userAction.traceId()),
                    userAction.userId(),
                    userAction.action(),
                    actionDataJson,
                    userAction.sessionId(),
                    userAction.ipAddress(),
                    userAction.userAgent(),
                    userAction.httpMethod(),
                    userAction.endpoint(),
                    userAction.requestSize(),
                    userAction.responseStatus(),
                    userAction.responseSize(),
                    userAction.durationMs(),
                    Timestamp.from(userAction.timestamp()),
                    Timestamp.from(userAction.createdAt())
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert user action to array", e);
        }
    }

    private void buildWhereClause(StringBuilder sql, List<Object> params, SearchCriteria criteria) {
        if (criteria.traceId() != null) {
            sql.append(" AND trace_id = ?");
            params.add(uuidConverter.convertToDatabase(criteria.traceId()));
        }
        if (criteria.userId() != null) {
            sql.append(" AND user_id = ?");
            params.add(criteria.userId());
        }
        if (criteria.action() != null) {
            sql.append(" AND action = ?");
            params.add(criteria.action());
        }
        if (criteria.sessionId() != null) {
            sql.append(" AND session_id = ?");
            params.add(criteria.sessionId());
        }
        if (criteria.ipAddress() != null) {
            sql.append(" AND ip_address = ").append(getInetComparison());
            params.add(criteria.ipAddress());
        }
        if (criteria.endpoint() != null) {
            sql.append(" AND endpoint = ?");
            params.add(criteria.endpoint());
        }
        if (criteria.httpMethod() != null) {
            sql.append(" AND http_method = ?");
            params.add(criteria.httpMethod());
        }
        if (criteria.minResponseStatus() != null) {
            sql.append(" AND response_status >= ?");
            params.add(criteria.minResponseStatus());
        }
        if (criteria.maxResponseStatus() != null) {
            sql.append(" AND response_status <= ?");
            params.add(criteria.maxResponseStatus());
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
        if (criteria.actionDataQuery() != null) {
            sql.append(" AND ").append(getJsonQuery("action_data", criteria.actionDataQuery()));
        }
    }

    private String getSortColumn(SearchCriteria.SortBy sortBy) {
        return switch (sortBy) {
            case TIMESTAMP -> "timestamp";
            case DURATION -> "duration_ms";
            case RESPONSE_STATUS -> "response_status";
            case ACTION -> "action";
            case USER_ID -> "user_id";
        };
    }

    private Map<Integer, Long> getStatusCodeCounts(String endpoint, String httpMethod, Instant startTime, Instant endTime) {
        String sql = String.format("""
            SELECT response_status, COUNT(*) as count 
            FROM %s 
            WHERE endpoint = ? AND http_method = ? AND timestamp >= ? AND timestamp <= ?
            GROUP BY response_status
            """, tableName);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql,
                endpoint, httpMethod, Timestamp.from(startTime), Timestamp.from(endTime));

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row.get("response_status"),
                        row -> ((Number) row.get("count")).longValue()
                ));
    }

    private Instant getEndOfBucket(Instant bucketStart, TimeBucket timeBucket) {
        return switch (timeBucket) {
            case MINUTE -> bucketStart.plusSeconds(60);
            case HOUR -> bucketStart.plusSeconds(3600);
            case DAY -> bucketStart.plusSeconds(86400);
            case WEEK -> bucketStart.plusSeconds(604800);
            case MONTH -> bucketStart.plusSeconds(2629746); // Average month
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
                    counts.get("newest_record") != null ? ((Timestamp) counts.get("newest_record")).toInstant() : null
            );
        } catch (Exception e) {
            logger.error("Failed to get table statistics", e);
            return new TableStatistics(0, 0, 0, 0, "unknown", "unknown", null, null);
        }
    }

    // Database-specific SQL functions (to be overridden in subclasses if needed)
    protected String getJsonCast() {
        return switch (databaseType) {
            case POSTGRESQL -> "?::jsonb";
            case MYSQL, MARIADB -> "?";
            default -> "?";
        };
    }

    protected String getInetCast() {
        return switch (databaseType) {
            case POSTGRESQL -> "?::inet";
            case MYSQL, MARIADB -> "?";
            default -> "?";
        };
    }

    protected String getInetComparison() {
        return switch (databaseType) {
            case POSTGRESQL -> "?::inet";
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

    protected String getDateTruncFunction(TimeBucket timeBucket) {
        String interval = switch (timeBucket) {
            case MINUTE -> "minute";
            case HOUR -> "hour";
            case DAY -> "day";
            case WEEK -> "week";
            case MONTH -> "month";
        };

        return switch (databaseType) {
            case POSTGRESQL -> String.format("DATE_TRUNC('%s', timestamp)", interval);
            case MYSQL, MARIADB -> switch (timeBucket) {
                case MINUTE -> "DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:00')";
                case HOUR -> "DATE_FORMAT(timestamp, '%Y-%m-%d %H:00:00')";
                case DAY -> "DATE_FORMAT(timestamp, '%Y-%m-%d 00:00:00')";
                case WEEK -> "DATE_FORMAT(timestamp, '%Y-%m-%d 00:00:00')"; // Simplified
                case MONTH -> "DATE_FORMAT(timestamp, '%Y-%m-01 00:00:00')";
            };
            default -> "timestamp";
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