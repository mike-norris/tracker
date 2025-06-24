-- ===========================================
-- OPTIMIZED TRACING TABLES SCHEMA FOR POSTGRESQL 17
-- Performance optimized for 1000 records/minute workload
-- ===========================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- ===========================================
-- CORE TABLES WITH PERFORMANCE OPTIMIZATIONS
-- ===========================================

-- User Actions Table
CREATE TABLE user_actions (
                              id BIGSERIAL,
                              trace_id UUID NOT NULL,
                              user_id VARCHAR(50) NOT NULL,
                              action VARCHAR(100) NOT NULL,
                              action_data JSONB,
                              session_id VARCHAR(50),
                              ip_address INET,
                              user_agent TEXT,
                              http_method VARCHAR(10),
                              endpoint VARCHAR(255),
                              request_size INTEGER,
                              response_status SMALLINT, -- Changed from INTEGER to SMALLINT
                              response_size INTEGER,
                              duration_ms INTEGER,
                              timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Add primary key with created_at for future partitioning
                              PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Job Executions Table
CREATE TABLE job_executions (
                                id BIGSERIAL,
                                trace_id UUID NOT NULL,
                                job_id VARCHAR(50) NOT NULL,
                                job_type VARCHAR(50) NOT NULL,
                                job_name VARCHAR(100) NOT NULL,
                                parent_job_id VARCHAR(50),
                                user_id VARCHAR(50),
                                status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'RETRYING')),
                                priority SMALLINT DEFAULT 5 CHECK (priority BETWEEN 1 AND 10), -- Changed to SMALLINT
                                queue_name VARCHAR(50),
                                worker_id VARCHAR(50),
                                input_data JSONB,
                                output_data JSONB,
                                error_message TEXT,
                                error_stack_trace TEXT,
                                retry_count SMALLINT DEFAULT 0, -- Changed to SMALLINT
                                max_retries SMALLINT DEFAULT 3, -- Changed to SMALLINT
                                scheduled_at TIMESTAMPTZ,
                                started_at TIMESTAMPTZ,
                                completed_at TIMESTAMPTZ,
                                duration_ms INTEGER,
                                memory_usage_mb INTEGER,
                                cpu_usage_percent DECIMAL(5,2),
                                timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Add primary key with created_at for future partitioning
                                PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- ===========================================
-- PARTITION CREATION FOR CURRENT PERIOD
-- ===========================================

-- Create initial partitions for current and next few months
-- These should be created dynamically in production

-- Current month partition
CREATE TABLE user_actions_2025_06 PARTITION OF user_actions
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');

CREATE TABLE job_executions_2025_06 PARTITION OF job_executions
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');

-- Next month partition
CREATE TABLE user_actions_2025_07 PARTITION OF user_actions
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');

CREATE TABLE job_executions_2025_07 PARTITION OF job_executions
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');

-- ===========================================
-- OPTIMIZED INDEXES FOR HIGH-WRITE WORKLOAD
-- ===========================================

-- Primary trace lookup indexes (UUID performance optimized)
CREATE INDEX CONCURRENTLY idx_user_actions_trace_id ON user_actions USING hash(trace_id);
CREATE INDEX CONCURRENTLY idx_job_executions_trace_id ON job_executions USING hash(trace_id);

-- User-based queries (most critical for performance)
CREATE INDEX CONCURRENTLY idx_user_actions_user_id ON user_actions(user_id);
CREATE INDEX CONCURRENTLY idx_user_actions_user_timestamp ON user_actions(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY idx_job_executions_user_id ON job_executions(user_id);

-- Time-based queries (essential for cleanup and analytics)
CREATE INDEX CONCURRENTLY idx_user_actions_created_at ON user_actions(created_at);
CREATE INDEX CONCURRENTLY idx_job_executions_created_at ON job_executions(created_at);

-- Job monitoring indexes (optimized for operational queries)
CREATE INDEX CONCURRENTLY idx_job_executions_status ON job_executions(status) WHERE status IN ('PENDING', 'RUNNING', 'FAILED');
CREATE INDEX CONCURRENTLY idx_job_executions_job_type ON job_executions(job_type);
CREATE INDEX CONCURRENTLY idx_job_executions_queue_status ON job_executions(queue_name, status) WHERE status IN ('PENDING', 'RUNNING');

-- Composite indexes for analytics (partial indexes for better performance)
CREATE INDEX CONCURRENTLY idx_user_actions_action_recent ON user_actions(action, created_at DESC)
    WHERE created_at >= CURRENT_DATE - INTERVAL '7 days';

CREATE INDEX CONCURRENTLY idx_job_executions_parent_job ON job_executions(parent_job_id)
    WHERE parent_job_id IS NOT NULL;

-- JSONB indexes for action_data queries (if needed)
CREATE INDEX CONCURRENTLY idx_user_actions_action_data_gin ON user_actions USING GIN(action_data);
CREATE INDEX CONCURRENTLY idx_job_executions_input_data_gin ON job_executions USING GIN(input_data);

-- ===========================================
-- PARTITION MANAGEMENT FUNCTIONS
-- ===========================================

CREATE OR REPLACE FUNCTION create_monthly_partitions(months_ahead INTEGER DEFAULT 2)
    RETURNS TEXT AS $$
DECLARE
    partition_date DATE;
    partition_name TEXT;
    start_date TEXT;
    end_date TEXT;
    result_msg TEXT := '';
BEGIN
    -- Create partitions for the next N months
    FOR i IN 1..months_ahead LOOP
            partition_date := DATE_TRUNC('month', CURRENT_DATE + (i || ' months')::INTERVAL)::DATE;
            start_date := partition_date::TEXT;
            end_date := (partition_date + INTERVAL '1 month')::DATE::TEXT;

            -- Create user_actions partition
            partition_name := 'user_actions_' || TO_CHAR(partition_date, 'YYYY_MM');

            BEGIN
                EXECUTE FORMAT('CREATE TABLE %I PARTITION OF user_actions FOR VALUES FROM (%L) TO (%L)',
                               partition_name, start_date, end_date);
                result_msg := result_msg || 'Created ' || partition_name || '. ';
            EXCEPTION
                WHEN duplicate_table THEN
                    result_msg := result_msg || partition_name || ' already exists. ';
            END;

            -- Create job_executions partition
            partition_name := 'job_executions_' || TO_CHAR(partition_date, 'YYYY_MM');

            BEGIN
                EXECUTE FORMAT('CREATE TABLE %I PARTITION OF job_executions FOR VALUES FROM (%L) TO (%L)',
                               partition_name, start_date, end_date);
                result_msg := result_msg || 'Created ' || partition_name || '. ';
            EXCEPTION
                WHEN duplicate_table THEN
                    result_msg := result_msg || partition_name || ' already exists. ';
            END;
        END LOOP;

    RETURN result_msg;
END;
$$ LANGUAGE plpgsql;

-- ===========================================
-- MAINTENANCE TRACKING TABLE
-- ===========================================

-- Table to track all maintenance operations
CREATE TABLE maintenance_log (
                                 id BIGSERIAL PRIMARY KEY,
                                 job_id VARCHAR(50) NOT NULL,
                                 job_type VARCHAR(50) NOT NULL,
                                 job_name VARCHAR(100) NOT NULL,
                                 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
                                 input_data JSONB,
                                 output_data JSONB,
                                 error_message TEXT,
                                 started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMPTZ,
                                 duration_ms INTEGER,
                                 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for maintenance log queries
CREATE INDEX idx_maintenance_log_job_type_status ON maintenance_log(job_type, status);
CREATE INDEX idx_maintenance_log_created_at ON maintenance_log(created_at);

-- ===========================================
-- OPTIMIZED CLEANUP FUNCTION WITH TRACKING
-- ===========================================

CREATE OR REPLACE FUNCTION cleanup_old_trace_records(retention_months INTEGER DEFAULT 18)
    RETURNS JSONB AS $$
DECLARE
    cutoff_date TIMESTAMPTZ;
    deleted_user_actions INTEGER;
    deleted_job_executions INTEGER;
    total_deleted INTEGER;
    cleanup_start TIMESTAMPTZ;
    cleanup_duration INTERVAL;
    result JSONB;
    job_uuid VARCHAR(50);
    maintenance_id BIGINT;
    BEGIN
    cleanup_start := CURRENT_TIMESTAMP;
    cutoff_date := CURRENT_TIMESTAMP - (retention_months || ' months')::INTERVAL;
    job_uuid := 'cleanup_' || EXTRACT(EPOCH FROM cleanup_start)::BIGINT;

    -- Log start of maintenance operation
INSERT INTO maintenance_log (
    job_id, job_type, job_name, status, input_data, started_at
) VALUES (
             job_uuid,
             'DATA_CLEANUP',
             'cleanup_old_trace_records',
             'RUNNING',
             jsonb_build_object('retention_months', retention_months, 'cutoff_date', cutoff_date),
             cleanup_start
         ) RETURNING id INTO maintenance_id;

BEGIN
-- Delete old user_actions records (use partitioned delete for better performance)
DELETE FROM user_actions
WHERE created_at < cutoff_date;

GET DIAGNOSTICS deleted_user_actions = ROW_COUNT;

        -- Delete old job_executions records
DELETE FROM job_executions
WHERE created_at < cutoff_date;

GET DIAGNOSTICS deleted_job_executions = ROW_COUNT;

total_deleted := deleted_user_actions + deleted_job_executions;
cleanup_duration := CURRENT_TIMESTAMP - cleanup_start;

        -- Build result
result := jsonb_build_object(
            'timestamp', cleanup_start,
            'cutoff_date', cutoff_date,
            'retention_months', retention_months,
            'deleted_user_actions', deleted_user_actions,
            'deleted_job_executions', deleted_job_executions,
            'total_deleted', total_deleted,
            'duration_seconds', EXTRACT(EPOCH FROM cleanup_duration),
            'job_id', job_uuid
        );

        -- Update maintenance log with success
UPDATE maintenance_log
SET
    status = 'COMPLETED',
    completed_at = CURRENT_TIMESTAMP,
    duration_ms = EXTRACT(EPOCH FROM cleanup_duration) * 1000,
    output_data = result
WHERE id = maintenance_id;

-- Log the cleanup operation
RAISE NOTICE 'Cleanup completed: % user_actions, % job_executions, % total records deleted in %',
            deleted_user_actions, deleted_job_executions, total_deleted, cleanup_duration;

RETURN result;

EXCEPTION
        WHEN OTHERS THEN
            -- Update maintenance log with failure
UPDATE maintenance_log
SET
    status = 'FAILED',
    completed_at = CURRENT_TIMESTAMP,
    duration_ms = EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - cleanup_start)) * 1000,
    error_message = SQLERRM
WHERE id = maintenance_id;

RAISE;
END;
END;
$$ LANGUAGE plpgsql;

-- ===========================================
-- PERFORMANCE MONITORING FUNCTIONS
-- ===========================================

CREATE OR REPLACE FUNCTION get_table_performance_stats()
    RETURNS TABLE (
                      table_name TEXT,
                      total_size TEXT,
                      table_size TEXT,
                      index_size TEXT,
                      row_count BIGINT,
                      avg_row_size TEXT,
                      seq_scans BIGINT,
                      seq_tup_read BIGINT,
                      idx_scans BIGINT,
                      idx_tup_fetch BIGINT
                  ) AS $$
BEGIN
    RETURN QUERY
        SELECT
            c.relname::TEXT,
            pg_size_pretty(pg_total_relation_size(c.oid)),
            pg_size_pretty(pg_relation_size(c.oid)),
            pg_size_pretty(pg_total_relation_size(c.oid) - pg_relation_size(c.oid)),
            c.reltuples::BIGINT,
            pg_size_pretty((pg_relation_size(c.oid) / GREATEST(c.reltuples, 1))::BIGINT),
            s.seq_scan,
            s.seq_tup_read,
            s.idx_scan,
            s.idx_tup_fetch
        FROM pg_class c
                 LEFT JOIN pg_stat_user_tables s ON c.oid = s.relid
        WHERE c.relname IN ('user_actions', 'job_executions')
           OR c.relname LIKE 'user_actions_%'
           OR c.relname LIKE 'job_executions_%'
        ORDER BY pg_total_relation_size(c.oid) DESC;
END;
$$ LANGUAGE plpgsql;

-- ===========================================
-- WRITE-OPTIMIZED BATCH INSERT FUNCTIONS
-- ===========================================

-- Optimized batch insert for user actions
CREATE OR REPLACE FUNCTION batch_insert_user_actions(
    trace_ids UUID[],
    user_ids VARCHAR(50)[],
    actions VARCHAR(100)[],
    action_data_array JSONB[],
    session_ids VARCHAR(50)[],
    ip_addresses INET[],
    user_agents TEXT[],
    http_methods VARCHAR(10)[],
    endpoints VARCHAR(255)[],
    request_sizes INTEGER[],
    response_statuses SMALLINT[],
    response_sizes INTEGER[],
    durations_ms INTEGER[]
) RETURNS INTEGER AS $$
DECLARE
    inserted_count INTEGER;
BEGIN
    INSERT INTO user_actions (
        trace_id, user_id, action, action_data, session_id,
        ip_address, user_agent, http_method, endpoint,
        request_size, response_status, response_size, duration_ms
    )
    SELECT
        trace_ids[i],
        user_ids[i],
        actions[i],
        action_data_array[i],
        session_ids[i],
        ip_addresses[i],
        user_agents[i],
        http_methods[i],
        endpoints[i],
        request_sizes[i],
        response_statuses[i],
        response_sizes[i],
        durations_ms[i]
    FROM generate_subscripts(trace_ids, 1) AS i;

    GET DIAGNOSTICS inserted_count = ROW_COUNT;
    RETURN inserted_count;
END;
$$ LANGUAGE plpgsql;

-- ===========================================
-- CONFIGURATION FOR HIGH-WRITE WORKLOAD
-- ===========================================

-- Recommended PostgreSQL configuration changes for 1000 records/minute:
/*
Add to postgresql.conf:

# Memory settings
shared_buffers = 256MB                    # 25% of RAM for dedicated server
work_mem = 4MB                           # For sort operations
maintenance_work_mem = 64MB              # For maintenance operations
effective_cache_size = 1GB               # 75% of RAM

# Write performance settings
wal_buffers = 16MB                       # WAL buffer size
checkpoint_completion_target = 0.9       # Spread checkpoints
max_wal_size = 1GB                       # Maximum WAL size before checkpoint
min_wal_size = 80MB                      # Minimum WAL size

# Connection settings
max_connections = 100                    # Adjust based on your needs

# Background writer settings
bgwriter_delay = 200ms                   # Background writer delay
bgwriter_lru_maxpages = 100              # Pages written per round

# Logging for monitoring
log_statement = 'mod'                    # Log modifications
log_min_duration_statement = 1000        # Log slow queries (1 second)
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '

# Auto vacuum settings for high write load
autovacuum_max_workers = 3               # Number of autovacuum workers
autovacuum_naptime = 20s                 # Time between autovacuum runs
autovacuum_vacuum_threshold = 50         # Minimum number of tuple updates
autovacuum_analyze_threshold = 50        # Minimum number of tuple inserts
autovacuum_vacuum_scale_factor = 0.1     # Fraction of table size before vacuum
autovacuum_analyze_scale_factor = 0.05   # Fraction of table size before analyze
*/

-- ===========================================
-- SIMPLIFIED MATERIALIZED VIEWS FOR PERFORMANCE
-- ===========================================

-- Real-time monitoring view (refresh every 5 minutes)
CREATE MATERIALIZED VIEW mv_realtime_monitoring AS
SELECT
            CURRENT_TIMESTAMP as snapshot_time,

            -- User activity (last hour)
            (SELECT COUNT(*) FROM user_actions WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour') as user_actions_last_hour,
            (SELECT COUNT(DISTINCT user_id) FROM user_actions WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour') as active_users_last_hour,

            -- Job status
            (SELECT COUNT(*) FROM job_executions WHERE status = 'RUNNING') as jobs_running,
            (SELECT COUNT(*) FROM job_executions WHERE status = 'PENDING') as jobs_pending,
            (SELECT COUNT(*) FROM job_executions WHERE status = 'FAILED' AND created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour') as jobs_failed_last_hour,

            -- Performance metrics
            (SELECT ROUND(AVG(duration_ms), 2) FROM user_actions WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour' AND duration_ms IS NOT NULL) as avg_response_time_ms,
            (SELECT ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms), 2) FROM user_actions WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour' AND duration_ms IS NOT NULL) as p95_response_time_ms,

            -- Error rate
            (SELECT ROUND((COUNT(*) FILTER (WHERE response_status >= 400) * 100.0 / NULLIF(COUNT(*), 0)), 2)
             FROM user_actions WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour') as error_rate_percent;

-- Create unique index for concurrent refresh
CREATE UNIQUE INDEX idx_mv_realtime_monitoring_snapshot ON mv_realtime_monitoring (snapshot_time);

-- Daily summary view (refresh daily)
CREATE MATERIALIZED VIEW mv_daily_summary AS
SELECT
    DATE_TRUNC('day', created_at) as summary_date,
    'user_actions' as table_name,
    COUNT(*) as record_count,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT session_id) as unique_sessions,
    ROUND(AVG(duration_ms), 2) as avg_duration_ms,
    COUNT(*) FILTER (WHERE response_status >= 400) as error_count,
    -- Add unique row identifier
    ROW_NUMBER() OVER (ORDER BY DATE_TRUNC('day', created_at)) as row_id
FROM user_actions
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', created_at)

UNION ALL

SELECT
    DATE_TRUNC('day', created_at) as summary_date,
    'job_executions' as table_name,
    COUNT(*) as record_count,
    COUNT(DISTINCT user_id) as unique_users,
    0 as unique_sessions,
    ROUND(AVG(duration_ms), 2) as avg_duration_ms,
    COUNT(*) FILTER (WHERE status = 'FAILED') as error_count,
    -- Add offset to avoid conflicts
    ROW_NUMBER() OVER (ORDER BY DATE_TRUNC('day', created_at)) + 1000000 as row_id
FROM job_executions
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', created_at)
ORDER BY summary_date DESC;

-- Create unique index
CREATE UNIQUE INDEX idx_mv_daily_summary_row_id ON mv_daily_summary (row_id);

-- ===========================================
-- LIGHTWEIGHT UTILITY VIEWS
-- ===========================================

-- Current partition information
CREATE OR REPLACE VIEW partition_info AS
SELECT
    n.nspname as schema_name,
    parent.relname as table_name,
    child.relname as partition_name,
    pg_get_expr(child.relpartbound, child.oid) as partition_bound,
    pg_size_pretty(pg_total_relation_size(child.oid)) as partition_size,
    pg_size_pretty(pg_relation_size(child.oid)) as table_size,
    pg_size_pretty(pg_total_relation_size(child.oid) - pg_relation_size(child.oid)) as index_size,
    (SELECT COUNT(*) FROM pg_stat_user_tables WHERE relid = child.oid) > 0 as has_stats,
    CASE
        WHEN pg_relation_size(child.oid) = 0 THEN 'EMPTY'
        WHEN pg_relation_size(child.oid) < 8192 THEN 'MINIMAL'
        ELSE 'HAS_DATA'
        END as data_status
FROM pg_class parent
         JOIN pg_inherits i ON i.inhparent = parent.oid
         JOIN pg_class child ON i.inhrelid = child.oid
         JOIN pg_namespace n ON parent.relnamespace = n.oid
WHERE parent.relname IN ('user_actions', 'job_executions')
  AND parent.relkind = 'p'  -- partitioned table
  AND child.relkind = 'r'   -- regular table (partition)
ORDER BY parent.relname, child.relname;

-- Quick stats view (no materialization for real-time data)
CREATE OR REPLACE VIEW quick_stats AS
SELECT
    'user_actions' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE) as today_records,
    COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE - INTERVAL '1 hour') as last_hour_records,
    MAX(created_at) as latest_record
FROM user_actions
UNION ALL
SELECT
    'job_executions' as table_name,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE) as today_records,
    COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE - INTERVAL '1 hour') as last_hour_records,
    MAX(created_at) as latest_record
FROM job_executions;

-- ===========================================
-- AUTOMATED MAINTENANCE PROCEDURES WITH TRACKING
-- ===========================================

-- Comprehensive maintenance function with logging
CREATE OR REPLACE FUNCTION run_maintenance()
    RETURNS JSONB AS $$
    DECLARE
        result JSONB;
    start_time TIMESTAMPTZ;
    partition_result TEXT;
    job_uuid VARCHAR(50);
    maintenance_id BIGINT;
    maintenance_duration INTERVAL;
    BEGIN
    start_time := CURRENT_TIMESTAMP;
    job_uuid := 'maintenance_' || EXTRACT(EPOCH FROM start_time)::BIGINT;

        -- Log start of maintenance operation
    INSERT INTO maintenance_log (
        job_id, job_type, job_name, status, input_data, started_at
    ) VALUES (
                 job_uuid,
                 'ROUTINE_MAINTENANCE',
                 'run_maintenance',
                 'RUNNING',
                 jsonb_build_object('operation_type', 'routine_maintenance'),
                 start_time
             ) RETURNING id INTO maintenance_id;

    BEGIN
    -- Create future partitions
    partition_result := create_monthly_partitions(2);

            -- Refresh materialized views
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_realtime_monitoring;

    maintenance_duration := CURRENT_TIMESTAMP - start_time;

            -- Build result
    result := jsonb_build_object(
                'maintenance_time', start_time,
                'partition_creation', partition_result,
                'materialized_views_refreshed', true,
                'duration_seconds', EXTRACT(EPOCH FROM maintenance_duration),
                'job_id', job_uuid
            );

            -- Update maintenance log with success
    UPDATE maintenance_log
    SET
        status = 'COMPLETED',
        completed_at = CURRENT_TIMESTAMP,
        duration_ms = EXTRACT(EPOCH FROM maintenance_duration) * 1000,
        output_data = result
    WHERE id = maintenance_id;

    RETURN result;

    EXCEPTION
            WHEN OTHERS THEN
                -- Update maintenance log with failure
    UPDATE maintenance_log
    SET
        status = 'FAILED',
        completed_at = CURRENT_TIMESTAMP,
        duration_ms = EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - start_time)) * 1000,
        error_message = SQLERRM
    WHERE id = maintenance_id;

    RAISE;
    END;
    END;
    $$ LANGUAGE plpgsql;

-- Monthly cleanup and optimization with logging
CREATE OR REPLACE FUNCTION run_monthly_maintenance()
    RETURNS JSONB AS $$
    DECLARE
        result JSONB;
    cleanup_result JSONB;
    start_time TIMESTAMPTZ;
    job_uuid VARCHAR(50);
    maintenance_id BIGINT;
    maintenance_duration INTERVAL;
    BEGIN
    start_time := CURRENT_TIMESTAMP;
    job_uuid := 'monthly_maintenance_' || EXTRACT(EPOCH FROM start_time)::BIGINT;

        -- Log start of maintenance operation
    INSERT INTO maintenance_log (
        job_id, job_type, job_name, status, input_data, started_at
    ) VALUES (
                 job_uuid,
                 'MONTHLY_MAINTENANCE',
                 'run_monthly_maintenance',
                 'RUNNING',
                 jsonb_build_object('operation_type', 'monthly_maintenance'),
                 start_time
             ) RETURNING id INTO maintenance_id;

    BEGIN
    -- Run cleanup
    cleanup_result := cleanup_old_trace_records(18);

            -- Refresh daily summary
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_summary;

    -- Run ANALYZE on main tables for better query planning
    ANALYZE user_actions;
    ANALYZE job_executions;

    maintenance_duration := CURRENT_TIMESTAMP - start_time;

            -- Build result
    result := jsonb_build_object(
                'maintenance_time', start_time,
                'cleanup_results', cleanup_result,
                'analyze_completed', true,
                'duration_seconds', EXTRACT(EPOCH FROM maintenance_duration),
                'job_id', job_uuid
            );

            -- Update maintenance log with success
    UPDATE maintenance_log
    SET
        status = 'COMPLETED',
        completed_at = CURRENT_TIMESTAMP,
        duration_ms = EXTRACT(EPOCH FROM maintenance_duration) * 1000,
        output_data = result
    WHERE id = maintenance_id;

    RETURN result;

    EXCEPTION
            WHEN OTHERS THEN
                -- Update maintenance log with failure
    UPDATE maintenance_log
    SET
        status = 'FAILED',
        completed_at = CURRENT_TIMESTAMP,
        duration_ms = EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - start_time)) * 1000,
        error_message = SQLERRM
    WHERE id = maintenance_id;

    RAISE;
    END;
    END;
$$ LANGUAGE plpgsql;

-- ===========================================
-- MAINTENANCE MONITORING FUNCTIONS
-- ===========================================

-- View recent maintenance operations
CREATE OR REPLACE VIEW recent_maintenance_operations AS
SELECT
    job_id,
    job_type,
    job_name,
    status,
    started_at,
    completed_at,
    duration_ms,
    CASE
        WHEN duration_ms IS NOT NULL THEN (duration_ms / 1000.0)::NUMERIC(10,2)
        ELSE NULL
        END as duration_seconds,
    CASE
        WHEN status = 'COMPLETED' THEN 'SUCCESS'
        WHEN status = 'FAILED' THEN 'ERROR'
        WHEN status = 'RUNNING' THEN 'IN_PROGRESS'
        ELSE status
        END as result_status,
    error_message
FROM maintenance_log
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
ORDER BY started_at DESC;

-- Function to get maintenance operation details
CREATE OR REPLACE FUNCTION get_maintenance_summary(days_back INTEGER DEFAULT 7)
    RETURNS TABLE (
                      job_type TEXT,
                      total_runs BIGINT,
                      successful_runs BIGINT,
                      failed_runs BIGINT,
                      avg_duration_seconds NUMERIC,
                      last_run_status TEXT,
                      last_run_time TIMESTAMPTZ
                  ) AS $$
    BEGIN
    RETURN QUERY
    SELECT
        ml.job_type::TEXT,
        COUNT(*)::BIGINT as total_runs,
        COUNT(*) FILTER (WHERE ml.status = 'COMPLETED')::BIGINT as successful_runs,
        COUNT(*) FILTER (WHERE ml.status = 'FAILED')::BIGINT as failed_runs,
        ROUND(AVG(ml.duration_ms / 1000.0), 2) as avg_duration_seconds,
        (SELECT status FROM maintenance_log ml2
         WHERE ml2.job_type = ml.job_type
         ORDER BY started_at DESC LIMIT 1)::TEXT as last_run_status,
        (SELECT started_at FROM maintenance_log ml2
         WHERE ml2.job_type = ml.job_type
         ORDER BY started_at DESC LIMIT 1) as last_run_time
    FROM maintenance_log ml
    WHERE ml.created_at >= CURRENT_DATE - (days_back || ' days')::INTERVAL
    GROUP BY ml.job_type
    ORDER BY last_run_time DESC;
    END;
$$ LANGUAGE plpgsql;

-- Get partition constraint details
CREATE OR REPLACE FUNCTION get_partition_details(table_name TEXT DEFAULT NULL)
    RETURNS TABLE (
                      schema_name TEXT,
                      parent_table TEXT,
                      partition_name TEXT,
                      partition_constraint TEXT,
                      partition_size TEXT,
                      row_estimate BIGINT,
                      last_vacuum TIMESTAMPTZ,
                      last_analyze TIMESTAMPTZ
                  ) AS $$
BEGIN
    RETURN QUERY
        SELECT
            n.nspname::TEXT,
            parent.relname::TEXT,
            child.relname::TEXT,
            pg_get_expr(child.relpartbound, child.oid)::TEXT,
            pg_size_pretty(pg_total_relation_size(child.oid)),
            child.reltuples::BIGINT,
            s.last_vacuum,
            s.last_analyze
        FROM pg_class parent
                 JOIN pg_inherits i ON i.inhparent = parent.oid
                 JOIN pg_class child ON i.inhrelid = child.oid
                 JOIN pg_namespace n ON parent.relnamespace = n.oid
                 LEFT JOIN pg_stat_user_tables s ON s.relid = child.oid
        WHERE (table_name IS NULL OR parent.relname = table_name)
          AND parent.relkind = 'p'
          AND child.relkind = 'r'
        ORDER BY parent.relname, child.relname;
END;
$$ LANGUAGE plpgsql;

-- ===========================================
-- EXAMPLE USAGE FOR SPRING BOOT INTEGRATION
-- ===========================================

/*
Example Spring Boot JPA configuration for optimal writes:

@Entity
@Table(name = "user_actions")
public class UserAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", columnDefinition = "UUID")
    private UUID traceId;

    // ... other fields

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

// Batch insert configuration
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/yourdb");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        return new HikariDataSource(config);
    }
}

// Service for batch operations
@Service
@Transactional
public class TracingService {

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.batch.size:100}")
    private int batchSize;

    public void batchInsertUserActions(List<UserAction> actions) {
        for (int i = 0; i < actions.size(); i++) {
            entityManager.persist(actions.get(i));
            if (i % batchSize == 0 && i > 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
    }
}

// Application properties for optimal performance
spring.jpa.properties.hibernate.jdbc.batch_size=100
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true
spring.jpa.properties.hibernate.generate_statistics=false
*/