package com.openrangelabs.tracer.repository;

import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.model.JobExecution;
import com.openrangelabs.tracer.model.JobStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.sql.PreparedStatement;

@Repository
public class JobExecutionRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String tableName;
    
    public JobExecutionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                 TracingProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tableName = properties.database().jobExecutionsTable();
    }
    
    @Transactional
    public void save(JobExecution jobExecution) {
        String sql = String.format("""
            INSERT INTO %s (trace_id, job_id, job_type, job_name, parent_job_id, user_id, 
                           status, priority, queue_name, worker_id, input_data, output_data, 
                           error_message, error_stack_trace, retry_count, max_retries, 
                           scheduled_at, started_at, completed_at, duration_ms, memory_usage_mb, 
                           cpu_usage_percent, timestamp, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::job_status_enum, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName);
        
        try {
            String inputDataJson = jobExecution.inputData() != null ? 
                objectMapper.writeValueAsString(jobExecution.inputData()) : null;
            String outputDataJson = jobExecution.outputData() != null ? 
                objectMapper.writeValueAsString(jobExecution.outputData()) : null;
                
            jdbcTemplate.update(sql,
                jobExecution.traceId(),
                jobExecution.jobId(),
                jobExecution.jobType(),
                jobExecution.jobName(),
                jobExecution.parentJobId(),
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
            throw new RuntimeException("Failed to save job execution", e);
        }
    }
    
    @Transactional
    public void updateStatus(UUID jobId, JobStatus status, String errorMessage) {
        String sql = String.format("""
            UPDATE %s 
            SET status = ?::job_status_enum, error_message = ?, updated_at = ? 
            WHERE job_id = ?
            """, tableName);
            
        jdbcTemplate.update(sql, status.name(), errorMessage, Timestamp.from(Instant.now()), jobId);
    }
    
    public void saveAsync(JobExecution jobExecution) {
        CompletableFuture.runAsync(() -> save(jobExecution));
    }

    @Transactional
    public void saveBatch(List<JobExecution> jobExecutions) {
        if (jobExecutions.isEmpty()) {
            return;
        }

        String sql = String.format("""
        INSERT INTO %s (trace_id, job_id, job_type, job_name, parent_job_id, user_id, 
                       status, priority, queue_name, worker_id, input_data, output_data, 
                       error_message, error_stack_trace, retry_count, max_retries, 
                       scheduled_at, started_at, completed_at, duration_ms, memory_usage_mb, 
                       cpu_usage_percent, timestamp, created_at, updated_at)
        VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?::job_status_enum, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, tableName);

        try {
            jdbcTemplate.batchUpdate(sql, jobExecutions, jobExecutions.size(),
                    (PreparedStatement ps, JobExecution jobExecution) -> {
                        try {
                            String inputDataJson = jobExecution.inputData() != null ?
                                    objectMapper.writeValueAsString(jobExecution.inputData()) : null;
                            String outputDataJson = jobExecution.outputData() != null ?
                                    objectMapper.writeValueAsString(jobExecution.outputData()) : null;

                            ps.setString(1, jobExecution.traceId().toString());
                            ps.setString(2, jobExecution.jobId().toString());
                            ps.setString(3, jobExecution.jobType());
                            ps.setString(4, jobExecution.jobName());
                            ps.setString(5, jobExecution.parentJobId());
                            ps.setString(6, jobExecution.userId());
                            ps.setString(7, jobExecution.status().name());
                            ps.setObject(8, jobExecution.priority());
                            ps.setString(9, jobExecution.queueName());
                            ps.setString(10, jobExecution.workerId());
                            ps.setString(11, inputDataJson);
                            ps.setString(12, outputDataJson);
                            ps.setString(13, jobExecution.errorMessage());
                            ps.setString(14, jobExecution.errorStackTrace());
                            ps.setObject(15, jobExecution.retryCount());
                            ps.setObject(16, jobExecution.maxRetries());
                            ps.setTimestamp(17, jobExecution.scheduledAt() != null ?
                                    Timestamp.from(jobExecution.scheduledAt()) : null);
                            ps.setTimestamp(18, jobExecution.startedAt() != null ?
                                    Timestamp.from(jobExecution.startedAt()) : null);
                            ps.setTimestamp(19, jobExecution.completedAt() != null ?
                                    Timestamp.from(jobExecution.completedAt()) : null);
                            ps.setObject(20, jobExecution.durationMs());
                            ps.setObject(21, jobExecution.memoryUsageMb());
                            ps.setObject(22, jobExecution.cpuUsagePercent());
                            ps.setTimestamp(23, Timestamp.from(jobExecution.timestamp()));
                            ps.setTimestamp(24, Timestamp.from(jobExecution.createdAt()));
                            ps.setTimestamp(25, Timestamp.from(jobExecution.updatedAt()));
                        } catch (Exception e) {
                            throw new RuntimeException("Error setting parameters for batch insert", e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to batch save job executions", e);
        }
    }
}