package com.openrangelabs.tracer.controller;

import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.context.TraceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${tracing.monitoring.endpoint:/actuator/tracing}")
@ConditionalOnWebApplication
public class TracingController {

    private final JdbcTemplate jdbcTemplate;
    private final TracingProperties properties;
    private final ApplicationContext applicationContext;

    public TracingController(JdbcTemplate jdbcTemplate, TracingProperties properties, ApplicationContext applicationContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();

        try {
            // Check database connectivity
            Integer userActionsCount = jdbcTemplate.queryForObject(
                    String.format("SELECT COUNT(*) FROM %s WHERE timestamp >= CURRENT_TIMESTAMP - INTERVAL '1 hour'",
                            properties.database().userActionsTable()),
                    Integer.class);

            Integer jobExecutionsCount = jdbcTemplate.queryForObject(
                    String.format("SELECT COUNT(*) FROM %s WHERE timestamp >= CURRENT_TIMESTAMP - INTERVAL '1 hour'",
                            properties.database().jobExecutionsTable()),
                    Integer.class);

            health.put("status", "UP");
            health.put("userActionsLastHour", userActionsCount);
            health.put("jobExecutionsLastHour", jobExecutionsCount);
            health.put("tracingEnabled", properties.enabled());
            health.put("asyncEnabled", properties.async().enabled());

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }

    @GetMapping("/trace/{traceId}")
    public ResponseEntity<Map<String, Object>> getTrace(@PathVariable String traceId) {
        Map<String, Object> trace = new HashMap<>();

        // Get user actions for this trace
        List<Map<String, Object>> userActions = jdbcTemplate.queryForList(
                String.format("SELECT * FROM %s WHERE trace_id = ? ORDER BY timestamp",
                        properties.database().userActionsTable()),
                traceId);

        // Get job executions for this trace
        List<Map<String, Object>> jobExecutions = jdbcTemplate.queryForList(
                String.format("SELECT * FROM %s WHERE trace_id = ? ORDER BY timestamp",
                        properties.database().jobExecutionsTable()),
                traceId);

        trace.put("traceId", traceId);
        trace.put("userActions", userActions);
        trace.put("jobExecutions", jobExecutions);
        trace.put("totalUserActions", userActions.size());
        trace.put("totalJobExecutions", jobExecutions.size());

        return ResponseEntity.ok(trace);
    }

    @GetMapping("/metrics/system-health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        List<Map<String, Object>> metrics = jdbcTemplate.queryForList(
                "SELECT * FROM system_health_dashboard");

        return ResponseEntity.ok(Map.of("systemHealth", metrics.isEmpty() ? new HashMap<>() : metrics.get(0)));
    }

    @GetMapping("/metrics/performance")
    public ResponseEntity<List<Map<String, Object>>> getPerformanceMetrics() {
        List<Map<String, Object>> metrics = jdbcTemplate.queryForList(
                "SELECT * FROM performance_bottlenecks ORDER BY p95_duration_ms DESC LIMIT 20");

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/metrics/job-queues")
    public ResponseEntity<List<Map<String, Object>>> getJobQueueMetrics() {
        List<Map<String, Object>> metrics = jdbcTemplate.queryForList(
                "SELECT * FROM job_queue_status ORDER BY queue_name, status");

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/metrics/errors")
    public ResponseEntity<List<Map<String, Object>>> getErrorMetrics() {
        List<Map<String, Object>> metrics = jdbcTemplate.queryForList(
                "SELECT * FROM error_summary ORDER BY error_hour DESC LIMIT 50");

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getCriticalAlerts() {
        List<Map<String, Object>> alerts = jdbcTemplate.queryForList(
                "SELECT * FROM critical_alerts ORDER BY severity, alert_time DESC");

        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> runCleanup() {
        try {
            Integer deletedRecords = jdbcTemplate.queryForObject(
                    "SELECT cleanup_old_trace_records()", Integer.class);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "deletedRecords", deletedRecords,
                    "retentionMonths", properties.retentionMonths()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> getCurrentContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("traceId", TraceContext.getTraceId());
        context.put("userId", TraceContext.getUserId());
        context.put("hasTraceContext", TraceContext.getTraceId() != null);

        return ResponseEntity.ok(context);
    }

    @GetMapping("/health/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealthCheck() {
        Map<String, Object> health = new HashMap<>();

        try {
            // Database connectivity check
            long start = System.currentTimeMillis();
            Integer pingResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long dbLatency = System.currentTimeMillis() - start;

            // Thread pool health
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                    applicationContext.getBean("tracingExecutor");

            health.put("status", "UP");
            health.put("database", Map.of(
                    "status", "UP",
                    "latencyMs", dbLatency
            ));
            health.put("threadPool", Map.of(
                    "activeThreads", executor.getActiveCount(),
                    "poolSize", executor.getPoolSize(),
                    "corePoolSize", executor.getCorePoolSize(),
                    "maxPoolSize", executor.getMaxPoolSize(),
                    "queueSize", executor.getThreadPoolExecutor().getQueue().size()
            ));

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }
}
