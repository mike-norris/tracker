package com.openrangelabs.tracer.repository;

import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.model.UserAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;

@Repository
public class UserActionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public UserActionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                TracingProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tableName = properties.database().userActionsTable();
    }

    @Transactional
    public void save(UserAction userAction) {
        String sql = String.format("""
            INSERT INTO %s (trace_id, user_id, action, action_data, session_id, 
                           ip_address, user_agent, http_method, endpoint, request_size, 
                           response_status, response_size, duration_ms, timestamp, created_at)
            VALUES (?, ?, ?, ?::jsonb, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName);

        try {
            String actionDataJson = userAction.actionData() != null ?
                    objectMapper.writeValueAsString(userAction.actionData()) : null;

            jdbcTemplate.update(sql,
                    userAction.traceId(),
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
            throw new RuntimeException("Failed to save user action", e);
        }
    }

    public void saveAsync(UserAction userAction) {
        // Implementation will use async executor
        CompletableFuture.runAsync(() -> save(userAction));
    }
}

