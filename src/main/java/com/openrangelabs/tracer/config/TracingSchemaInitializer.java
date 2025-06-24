package com.openrangelabs.tracer.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingSchemaInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TracingSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final TracingProperties properties;

    public TracingSchemaInitializer(JdbcTemplate jdbcTemplate, TracingProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void run(String... args) throws Exception {
        if (properties.autoCreateTables()) {
            logger.info("Initializing tracing database schema...");
            createTracingTables();
            createTracingIndexes();
            createTracingFunctions();
            logger.info("Tracing database schema initialization completed");
        }
    }

    private void createTracingTables() {
        // Create job status enum if it doesn't exist
        jdbcTemplate.execute("""
            DO $$ BEGIN
                CREATE TYPE job_status_enum AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'RETRYING');
            EXCEPTION
                WHEN duplicate_object THEN null;
            END $$;
        """);

        // Create user_actions table
        jdbcTemplate.execute(String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGSERIAL PRIMARY KEY,
                trace_id VARCHAR(36) NOT NULL,
                user_id VARCHAR(50) NOT NULL,
                action VARCHAR(100) NOT NULL,
                action_data JSONB,
                session_id VARCHAR(50),
                ip_address INET,
                user_agent TEXT,
                http_method VARCHAR(10),
                endpoint VARCHAR(255),
                request_size INTEGER,
                response_status INTEGER,
                response_size INTEGER,
                duration_ms INTEGER,
                timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """, properties.database().userActionsTable()));

        // Create job_executions table
        jdbcTemplate.execute(String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGSERIAL PRIMARY KEY,
                trace_id VARCHAR(36) NOT NULL,
                job_id VARCHAR(50) NOT NULL,
                job_type VARCHAR(50) NOT NULL,
                job_name VARCHAR(100) NOT NULL,
                parent_job_id VARCHAR(50),
                user_id VARCHAR(50),
                status job_status_enum NOT NULL DEFAULT 'PENDING',
                priority INTEGER DEFAULT 5 CHECK (priority BETWEEN 1 AND 10),
                queue_name VARCHAR(50),
                worker_id VARCHAR(50),
                input_data JSONB,
                output_data JSONB,
                error_message TEXT,
                error_stack_trace TEXT,
                retry_count INTEGER DEFAULT 0,
                max_retries INTEGER DEFAULT 3,
                scheduled_at TIMESTAMPTZ,
                started_at TIMESTAMPTZ,
                completed_at TIMESTAMPTZ,
                duration_ms INTEGER,
                memory_usage_mb INTEGER,
                cpu_usage_percent DECIMAL(5,2),
                timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """, properties.database().jobExecutionsTable()));
    }

    private void createTracingIndexes() {
        String userActionsTable = properties.database().userActionsTable();
        String jobExecutionsTable = properties.database().jobExecutionsTable();

        // Primary trace lookup indexes
        jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_trace_id ON %s(trace_id)",
                userActionsTable, userActionsTable));
        jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_trace_id ON %s(trace_id)",
                jobExecutionsTable, jobExecutionsTable));

        // User-based queries
        jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_user_id ON %s(user_id)",
                userActionsTable, userActionsTable));
        jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_user_id_timestamp ON %s(user_id, timestamp DESC)",
                userActionsTable, userActionsTable));

        // Time-based queries
        jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_timestamp ON %s(timestamp)",
                userActionsTable, userActionsTable));
        jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_timestamp ON %s(timestamp)",
                jobExecutionsTable, jobExecutionsTable));

        // Job monitoring indexes
        jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_status ON %s(status)",
                jobExecutionsTable, jobExecutionsTable));
        jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_job_type ON %s(job_type)",
                jobExecutionsTable, jobExecutionsTable));
        jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_queue_status ON %s(queue_name, status)",
                jobExecutionsTable, jobExecutionsTable));
    }

    private void createTracingFunctions() {
        // Create cleanup function
        jdbcTemplate.execute(String.format("""
            CREATE OR REPLACE FUNCTION cleanup_old_trace_records()
            RETURNS INTEGER AS $
            DECLARE
                cutoff_date TIMESTAMPTZ;
                deleted_user_actions INTEGER;
                deleted_job_executions INTEGER;
                total_deleted INTEGER;
            BEGIN
                cutoff_date := CURRENT_TIMESTAMP - INTERVAL '%d months';
                
                DELETE FROM %s WHERE created_at < cutoff_date;
                GET DIAGNOSTICS deleted_user_actions = ROW_COUNT;
                
                DELETE FROM %s WHERE created_at < cutoff_date;
                GET DIAGNOSTICS deleted_job_executions = ROW_COUNT;
                
                total_deleted := deleted_user_actions + deleted_job_executions;
                
                RETURN total_deleted;
            END;
            $ LANGUAGE plpgsql;
        """, properties.retentionMonths(),
                properties.database().userActionsTable(),
                properties.database().jobExecutionsTable()));
    }
}
