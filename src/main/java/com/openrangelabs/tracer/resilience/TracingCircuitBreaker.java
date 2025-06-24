package com.openrangelabs.tracer.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class TracingCircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(TracingCircuitBreaker.class);

    private final CircuitBreaker databaseCircuitBreaker;
    private final CircuitBreaker metricsCircuitBreaker;

    public TracingCircuitBreaker() {
        // Database operations circuit breaker
        CircuitBreakerConfig dbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                    // 50% failure rate threshold
                .waitDurationInOpenState(Duration.ofSeconds(30))  // Wait 30 seconds before trying again
                .slidingWindowSize(10)                       // Consider last 10 calls
                .minimumNumberOfCalls(5)                     // Minimum 5 calls before calculating failure rate
                .permittedNumberOfCallsInHalfOpenState(3)    // Allow 3 calls in half-open state
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        // Metrics operations circuit breaker (more lenient)
        CircuitBreakerConfig metricsConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(70)                    // 70% failure rate threshold
                .waitDurationInOpenState(Duration.ofSeconds(10))  // Wait 10 seconds
                .slidingWindowSize(5)                        // Consider last 5 calls
                .minimumNumberOfCalls(3)                     // Minimum 3 calls
                .permittedNumberOfCallsInHalfOpenState(2)    // Allow 2 calls in half-open state
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

        this.databaseCircuitBreaker = registry.circuitBreaker("tracingDatabase", dbConfig);
        this.metricsCircuitBreaker = registry.circuitBreaker("tracingMetrics", metricsConfig);

        // Set up event listeners
        setupEventListeners();
    }

    private void setupEventListeners() {
        databaseCircuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        logger.warn("Database circuit breaker state transition: {} -> {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()));

        databaseCircuitBreaker.getEventPublisher()
                .onCallNotPermitted(event ->
                        logger.error("Database operation not permitted - circuit breaker is OPEN"));

        databaseCircuitBreaker.getEventPublisher()
                .onError(event ->
                        logger.error("Database operation failed: {}", event.getThrowable().getMessage()));

        metricsCircuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        logger.warn("Metrics circuit breaker state transition: {} -> {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()));
    }

    /**
     * Execute database operation with circuit breaker protection
     */
    public <T> T executeDatabaseOperation(Supplier<T> operation) {
        return databaseCircuitBreaker.executeSupplier(operation);
    }

    /**
     * Execute database operation with fallback supplier
     */
    public <T> T executeDatabaseOperationWithFallbackSupplier(Supplier<T> operation, Supplier<T> fallback) {
        try {
            return databaseCircuitBreaker.executeSupplier(operation);
        } catch (Exception e) {
            logger.warn("Database operation failed, using fallback: {}", e.getMessage());
            return fallback.get();
        }
    }

    /**
     * Execute database operation with fallback value
     */
    public <T> T executeDatabaseOperationWithFallbackValue(Supplier<T> operation, T fallbackValue) {
        return executeDatabaseOperationWithFallbackSupplier(operation, () -> fallbackValue);
    }

    /**
     * Execute runnable database operation (no return value)
     */
    public void executeDatabaseOperation(Runnable operation) {
        databaseCircuitBreaker.executeRunnable(operation);
    }

    /**
     * Execute runnable database operation with fallback
     */
    public void executeDatabaseOperationWithFallbackRunnable(Runnable operation, Runnable fallback) {
        try {
            databaseCircuitBreaker.executeRunnable(operation);
        } catch (Exception e) {
            logger.warn("Database operation failed, executing fallback: {}", e.getMessage());
            fallback.run();
        }
    }

    /**
     * Execute metrics operation with circuit breaker protection
     */
    public <T> T executeMetricsOperation(Supplier<T> operation) {
        return metricsCircuitBreaker.executeSupplier(operation);
    }

    /**
     * Execute metrics operation with fallback supplier (silently fails for metrics)
     */
    public <T> T executeMetricsOperationWithFallbackSupplier(Supplier<T> operation, Supplier<T> fallback) {
        try {
            return metricsCircuitBreaker.executeSupplier(operation);
        } catch (Exception e) {
            logger.debug("Metrics operation failed, using fallback: {}", e.getMessage());
            return fallback.get();
        }
    }

    /**
     * Execute metrics operation with fallback value (silently fails for metrics)
     */
    public <T> T executeMetricsOperationWithFallbackValue(Supplier<T> operation, T fallbackValue) {
        return executeMetricsOperationWithFallbackSupplier(operation, () -> fallbackValue);
    }

    /**
     * Execute runnable metrics operation (silently fails)
     */
    public void executeMetricsOperation(Runnable operation) {
        try {
            metricsCircuitBreaker.executeRunnable(operation);
        } catch (Exception e) {
            logger.debug("Metrics operation failed: {}", e.getMessage());
            // Silently ignore metrics failures
        }
    }

    /**
     * Check if database operations are currently allowed
     */
    public boolean isDatabaseAvailable() {
        return databaseCircuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }

    /**
     * Check if metrics operations are currently allowed
     */
    public boolean areMetricsAvailable() {
        return metricsCircuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }

    /**
     * Get current database circuit breaker state
     */
    public CircuitBreaker.State getDatabaseCircuitBreakerState() {
        return databaseCircuitBreaker.getState();
    }

    /**
     * Get current metrics circuit breaker state
     */
    public CircuitBreaker.State getMetricsCircuitBreakerState() {
        return metricsCircuitBreaker.getState();
    }

    /**
     * Force database circuit breaker to open state (for testing)
     */
    public void openDatabaseCircuitBreaker() {
        databaseCircuitBreaker.transitionToOpenState();
    }

    /**
     * Force database circuit breaker to closed state (for testing)
     */
    public void closeDatabaseCircuitBreaker() {
        databaseCircuitBreaker.transitionToClosedState();
    }

    /**
     * Reset database circuit breaker (for testing)
     */
    public void resetDatabaseCircuitBreaker() {
        databaseCircuitBreaker.reset();
    }

    /**
     * Get database circuit breaker metrics
     */
    public String getDatabaseCircuitBreakerMetrics() {
        var metrics = databaseCircuitBreaker.getMetrics();
        int successfulCalls = metrics.getNumberOfSuccessfulCalls();
        int bufferedCalls = metrics.getNumberOfBufferedCalls();
        float successRate = bufferedCalls > 0 ? (successfulCalls * 100.0f / bufferedCalls) : 0.0f;

        return String.format(
                "State: %s, Failure Rate: %.2f%%, Success Rate: %.2f%%, Buffered Calls: %d, Failed: %d, Successful: %d",
                databaseCircuitBreaker.getState(),
                metrics.getFailureRate(),
                successRate,
                bufferedCalls,
                metrics.getNumberOfFailedCalls(),
                successfulCalls
        );
    }

    /**
     * Get detailed metrics as a structured object
     */
    public CircuitBreakerMetricsInfo getDatabaseMetricsInfo() {
        var metrics = databaseCircuitBreaker.getMetrics();
        return new CircuitBreakerMetricsInfo(
                databaseCircuitBreaker.getState().toString(),
                metrics.getFailureRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSuccessfulCalls()
        );
    }

    /**
     * Get metrics circuit breaker info
     */
    public CircuitBreakerMetricsInfo getMetricsCircuitBreakerInfo() {
        var metrics = metricsCircuitBreaker.getMetrics();
        return new CircuitBreakerMetricsInfo(
                metricsCircuitBreaker.getState().toString(),
                metrics.getFailureRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSuccessfulCalls()
        );
    }

    /**
     * Data class for circuit breaker metrics
     */
    public static class CircuitBreakerMetricsInfo {
        private final String state;
        private final float failureRate;
        private final int bufferedCalls;
        private final int failedCalls;
        private final int successfulCalls;

        public CircuitBreakerMetricsInfo(String state, float failureRate, int bufferedCalls,
                                         int failedCalls, int successfulCalls) {
            this.state = state;
            this.failureRate = failureRate;
            this.bufferedCalls = bufferedCalls;
            this.failedCalls = failedCalls;
            this.successfulCalls = successfulCalls;
        }

        public String getState() { return state; }
        public float getFailureRate() { return failureRate; }
        public int getBufferedCalls() { return bufferedCalls; }
        public int getFailedCalls() { return failedCalls; }
        public int getSuccessfulCalls() { return successfulCalls; }
        public float getSuccessRate() {
            return bufferedCalls > 0 ? (successfulCalls * 100.0f / bufferedCalls) : 0.0f;
        }

        @Override
        public String toString() {
            return String.format(
                    "CircuitBreakerMetrics{state='%s', failureRate=%.2f%%, successRate=%.2f%%, " +
                            "bufferedCalls=%d, failedCalls=%d, successfulCalls=%d}",
                    state, failureRate, getSuccessRate(), bufferedCalls, failedCalls, successfulCalls
            );
        }
    }
}