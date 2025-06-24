package com.openrangelabs.tracer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

@ConfigurationProperties(prefix = "tracing")
@Validated
public record TracingProperties(
        @DefaultValue("true") boolean enabled,
        @NotBlank @DefaultValue("X-Trace-ID") String traceIdHeader,
        @NotBlank @DefaultValue("traceId") String mdcKey,
        @DefaultValue("true") boolean autoCreateTables,
        @Min(1) @Max(60) @DefaultValue("18") int retentionMonths,
        @Valid Database database,
        @Valid Async async,
        @Valid Monitoring monitoring
) {
    public record Database(
            @NotBlank @DefaultValue("user_actions") String userActionsTable,
            @NotBlank @DefaultValue("job_executions") String jobExecutionsTable,
            @DefaultValue("true") boolean enablePartitioning,
            @Min(100) @Max(10000) @DefaultValue("1000") int batchSize
    ) {}

    public record Async(
            @DefaultValue("true") boolean enabled,
            @NotBlank @DefaultValue("tracing-executor") String threadPoolName,
            @Min(1) @Max(50) @DefaultValue("5") int corePoolSize,
            @Min(1) @Max(100) @DefaultValue("20") int maxPoolSize,
            @Min(10) @Max(300) @DefaultValue("60") int keepAliveSeconds,
            @Min(10) @Max(10000) @DefaultValue("500") int queueCapacity
    ) {}

    public record Monitoring(
            @DefaultValue("true") boolean metricsEnabled,
            @NotBlank @DefaultValue("/actuator/tracing") String endpoint,
            @DefaultValue("true") boolean healthCheck
    ) {}
}