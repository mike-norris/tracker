package com.openrangelabs.tracer.util;

import com.openrangelabs.tracer.context.TraceContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Utility class for executing code with trace context propagation
 */
@Component
public class TraceContextExecutor {

    /**
     * Execute a task asynchronously while preserving trace context
     */
    @Async("tracingExecutor")
    public <T> CompletableFuture<T> executeWithTrace(Supplier<T> task) {
        UUID traceId = TraceContext.getTraceId();
        String userId = TraceContext.getUserId();

        return CompletableFuture.supplyAsync(() -> {
            try {
                TraceContext.copyFromParent(traceId, userId);
                return task.get();
            } finally {
                TraceContext.clear();
            }
        });
    }

    /**
     * Execute a runnable asynchronously while preserving trace context
     */
    @Async("tracingExecutor")
    public CompletableFuture<Void> executeWithTrace(Runnable task) {
        UUID traceId = TraceContext.getTraceId();
        String userId = TraceContext.getUserId();

        return CompletableFuture.runAsync(() -> {
            try {
                TraceContext.copyFromParent(traceId, userId);
                task.run();
            } finally {
                TraceContext.clear();
            }
        });
    }
}

