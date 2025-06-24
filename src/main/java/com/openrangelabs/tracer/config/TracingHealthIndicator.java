package com.openrangelabs.tracer.config;

import com.openrangelabs.tracer.repository.TracingRepository;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Health indicator for the tracing system.
 * Checks database connectivity, queue status, and overall system health.
 */
@Component("tracing")
public class TracingHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(TracingHealthIndicator.class);

    private final TracingRepository tracingRepository;
    private final TracingProperties properties;

    public TracingHealthIndicator(TracingRepository tracingRepository, TracingProperties properties) {
        this.tracingRepository = tracingRepository;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            // Get database health information
            TracingRepository.DatabaseHealthInfo healthInfo = tracingRepository.getHealthInfo();

            if (!healthInfo.isHealthy()) {
                return Health.down()
                        .withDetail("database", "DOWN")
                        .withDetail("databaseType", healthInfo.databaseType())
                        .withDetail("canConnect", healthInfo.canConnect())
                        .withDetail("error", "Database connectivity check failed")
                        .build();
            }

            // Get repository metrics
            Map<String, Object> metrics = tracingRepository.getMetrics();

            // Check for any error indicators in metrics
            if (metrics.containsKey("error")) {
                return Health.down()
                        .withDetail("database", "UP")
                        .withDetail("databaseType", healthInfo.databaseType())
                        .withDetail("version", healthInfo.version())
                        .withDetail("metrics", "ERROR")
                        .withDetail("error", metrics.get("error"))
                        .build();
            }

            // Build healthy response with details
            Health.Builder healthBuilder = Health.up()
                    .withDetail("database", "UP")
                    .withDetail("databaseType", healthInfo.databaseType())
                    .withDetail("version", healthInfo.version())
                    .withDetail("connectionCount", healthInfo.connectionCount())
                    .withDetail("tracingEnabled", properties.enabled())
                    .withDetail("asyncEnabled", properties.async().enabled());

            // Add database-specific health details
            healthInfo.additionalInfo().forEach(healthBuilder::withDetail);

            // Add metrics summary
            if (metrics.containsKey("userActions")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userActionMetrics = (Map<String, Object>) metrics.get("userActions");
                healthBuilder.withDetail("userActionsToday", userActionMetrics.get("todayRecords"));
            }

            if (metrics.containsKey("jobExecutions")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> jobMetrics = (Map<String, Object>) metrics.get("jobExecutions");
                healthBuilder.withDetail("jobExecutionsToday", jobMetrics.get("todayRecords"));
            }

            return healthBuilder.build();

        } catch (Exception e) {
            logger.error("Health check failed", e);
            return Health.down()
                    .withDetail("database", "UNKNOWN")
                    .withDetail("error", e.getMessage())
                    .withDetail("tracingEnabled", properties.enabled())
                    .build();
        }
    }
}