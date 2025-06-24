package com.openrangelabs.tracer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "tracing")
public record TracingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("X-Trace-ID") String traceIdHeader,
        @DefaultValue("traceId") String mdcKey,
        @DefaultValue("true") boolean autoCreateTables,
        @DefaultValue("18") int retentionMonths,
        Database database,
        Async async,
        Monitoring monitoring
) {
    public record Database(
            @DefaultValue("user_actions") String userActionsTable,
            @DefaultValue("job_executions") String jobExecutionsTable,
            @DefaultValue("true") boolean enablePartitioning,
            @DefaultValue("1000") int batchSize
    ) {}

    public record Async(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("tracing-executor") String threadPoolName,
            @DefaultValue("5") int corePoolSize,
            @DefaultValue("20") int maxPoolSize,
            @DefaultValue("60") int keepAliveSeconds,
            @DefaultValue("500") int queueCapacity
    ) {}

    public record Monitoring(
            @DefaultValue("true") boolean metricsEnabled,
            @DefaultValue("/actuator/tracing") String endpoint,
            @DefaultValue("true") boolean healthCheck
    ) {}
}
