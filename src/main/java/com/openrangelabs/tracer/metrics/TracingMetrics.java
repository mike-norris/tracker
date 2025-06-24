package com.openrangelabs.tracer.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnClass({MeterRegistry.class, CompositeMeterRegistryAutoConfiguration.class})
public class TracingMetrics implements MeterBinder {

    private final AtomicLong activeBatchSize = new AtomicLong(0);
    private final AtomicInteger activeTraces = new AtomicInteger(0);
    private final AtomicLong databaseErrors = new AtomicLong(0);

    private MeterRegistry meterRegistry;
    private Counter userActionsCounter;
    private Counter jobExecutionsCounter;
    private Counter tracingErrorsCounter;
    private Timer userActionTimer;
    private Timer jobExecutionTimer;
    private Timer databaseOperationTimer;

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // Counters for tracking volumes
        this.userActionsCounter = Counter.builder("tracer.user.actions.total")
                .description("Total number of user actions traced")
                .tag("component", "tracer")
                .register(meterRegistry);

        this.jobExecutionsCounter = Counter.builder("tracer.job.executions.total")
                .description("Total number of job executions traced")
                .tag("component", "tracer")
                .register(meterRegistry);

        this.tracingErrorsCounter = Counter.builder("tracer.errors.total")
                .description("Total number of tracing errors")
                .tag("component", "tracer")
                .register(meterRegistry);

        // Timers for measuring performance
        this.userActionTimer = Timer.builder("tracer.user.action.duration")
                .description("Time taken to process user actions")
                .tag("component", "tracer")
                .register(meterRegistry);

        this.jobExecutionTimer = Timer.builder("tracer.job.execution.duration")
                .description("Time taken to process job executions")
                .tag("component", "tracer")
                .register(meterRegistry);

        this.databaseOperationTimer = Timer.builder("tracer.database.operation.duration")
                .description("Time taken for database operations")
                .tag("component", "tracer")
                .register(meterRegistry);

        // Gauges for current state
        Gauge.builder("tracer.batch.size.current", this, TracingMetrics::getActiveBatchSize)
                .description("Current size of pending batch operations")
                .tag("component", "tracer")
                .register(meterRegistry);

        Gauge.builder("tracer.traces.active", this, TracingMetrics::getActiveTraces)
                .description("Number of currently active traces")
                .tag("component", "tracer")
                .register(meterRegistry);

        Gauge.builder("tracer.database.errors.total", this, TracingMetrics::getDatabaseErrors)
                .description("Total database errors encountered")
                .tag("component", "tracer")
                .register(meterRegistry);
    }

    // Counter methods
    public void incrementUserActions() {
        if (userActionsCounter != null) {
            userActionsCounter.increment();
        }
    }

    public void incrementUserActions(String action) {
        if (meterRegistry != null) {
            Counter.builder("tracer.user.actions.by.type")
                    .description("User actions by type")
                    .tag("action", action)
                    .tag("component", "tracer")
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void incrementJobExecutions() {
        if (jobExecutionsCounter != null) {
            jobExecutionsCounter.increment();
        }
    }

    public void incrementJobExecutions(String jobType, String status) {
        if (meterRegistry != null) {
            Counter.builder("tracer.job.executions.by.type.status")
                    .description("Job executions by type and status")
                    .tag("job_type", jobType)
                    .tag("status", status)
                    .tag("component", "tracer")
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void incrementTracingErrors() {
        if (tracingErrorsCounter != null) {
            tracingErrorsCounter.increment();
        }
    }

    public void incrementTracingErrors(String errorType) {
        if (meterRegistry != null) {
            Counter.builder("tracer.errors.by.type")
                    .description("Tracing errors by type")
                    .tag("error_type", errorType)
                    .tag("component", "tracer")
                    .register(meterRegistry)
                    .increment();
        }
    }

    // Timer methods
    public Timer.Sample startUserActionTimer() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }

    public Timer.Sample startJobExecutionTimer() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }

    public Timer.Sample startDatabaseOperationTimer() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }

    public void recordUserActionTime(long milliseconds) {
        if (userActionTimer != null) {
            userActionTimer.record(java.time.Duration.ofMillis(milliseconds));
        }
    }

    public void recordJobExecutionTime(long milliseconds) {
        if (jobExecutionTimer != null) {
            jobExecutionTimer.record(java.time.Duration.ofMillis(milliseconds));
        }
    }

    public void recordDatabaseOperationTime(long milliseconds) {
        if (databaseOperationTimer != null) {
            databaseOperationTimer.record(java.time.Duration.ofMillis(milliseconds));
        }
    }

    // Gauge state management
    public void setBatchSize(long size) {
        activeBatchSize.set(size);
    }

    public void incrementBatchSize() {
        activeBatchSize.incrementAndGet();
    }

    public void decrementBatchSize() {
        activeBatchSize.decrementAndGet();
    }

    public void incrementActiveTraces() {
        activeTraces.incrementAndGet();
    }

    public void decrementActiveTraces() {
        activeTraces.decrementAndGet();
    }

    public void incrementDatabaseErrors() {
        databaseErrors.incrementAndGet();
    }

    // Gauge accessor methods
    private double getActiveBatchSize() {
        return activeBatchSize.get();
    }

    private double getActiveTraces() {
        return activeTraces.get();
    }

    private double getDatabaseErrors() {
        return databaseErrors.get();
    }

    // Utility methods for complex metrics
    public void recordBatchOperation(int batchSize, long processingTimeMs) {
        if (meterRegistry != null) {
            Timer batchTimer = Timer.builder("tracer.batch.processing.duration")
                    .description("Time taken to process batches")
                    .tag("component", "tracer")
                    .register(meterRegistry);
            batchTimer.record(java.time.Duration.ofMillis(processingTimeMs));

            Counter batchSizeCounter = Counter.builder("tracer.batch.records.processed")
                    .description("Number of records processed in batches")
                    .tag("component", "tracer")
                    .register(meterRegistry);
            batchSizeCounter.increment(batchSize);
        }
    }

    public void recordAsyncOperation(String operationType, boolean success, long durationMs) {
        if (meterRegistry != null) {
            Timer asyncTimer = Timer.builder("tracer.async.operation.duration")
                    .description("Time taken for async operations")
                    .tag("operation_type", operationType)
                    .tag("success", String.valueOf(success))
                    .tag("component", "tracer")
                    .register(meterRegistry);
            asyncTimer.record(java.time.Duration.ofMillis(durationMs));
        }
    }
}