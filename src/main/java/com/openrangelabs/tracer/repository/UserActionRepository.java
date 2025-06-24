package com.openrangelabs.tracer.repository;

import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.model.UserAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.util.List;

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

    @Transactional
    public void saveBatch(List<UserAction> userActions) {
        if (userActions.isEmpty()) {
            return;
        }

        String sql = String.format("""
        INSERT INTO %s (trace_id, user_id, action, action_data, session_id, 
                       ip_address, user_agent, http_method, endpoint, request_size, 
                       response_status, response_size, duration_ms, timestamp, created_at)
        VALUES (?::uuid, ?, ?, ?::jsonb, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, tableName);

        try {
            jdbcTemplate.batchUpdate(sql, userActions, userActions.size(),
                    (PreparedStatement ps, UserAction userAction) -> {
                        try {
                            String actionDataJson = userAction.actionData() != null ?
                                    objectMapper.writeValueAsString(userAction.actionData()) : null;

                            ps.setString(1, userAction.traceId().toString());
                            ps.setString(2, userAction.userId());
                            ps.setString(3, userAction.action());
                            ps.setString(4, actionDataJson);
                            ps.setString(5, userAction.sessionId());
                            ps.setString(6, userAction.ipAddress());
                            ps.setString(7, userAction.userAgent());
                            ps.setString(8, userAction.httpMethod());
                            ps.setString(9, userAction.endpoint());
                            ps.setObject(10, userAction.requestSize());
                            ps.setObject(11, userAction.responseStatus());
                            ps.setObject(12, userAction.responseSize());
                            ps.setObject(13, userAction.durationMs());
                            ps.setTimestamp(14, Timestamp.from(userAction.timestamp()));
                            ps.setTimestamp(15, Timestamp.from(userAction.createdAt()));
                        } catch (Exception e) {
                            throw new RuntimeException("Error setting parameters for batch insert", e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to batch save user actions", e);
        }
    }
}

