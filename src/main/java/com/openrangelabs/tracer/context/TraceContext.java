package com.openrangelabs.tracer.context;

import org.slf4j.MDC;
import java.util.UUID;

public class TraceContext {
    private static final ThreadLocal<UUID> traceIdHolder = new ThreadLocal<>();  // Changed from String to UUID
    private static final ThreadLocal<String> userIdHolder = new ThreadLocal<>();

    public static UUID getTraceId() {  // Changed return type from String to UUID
        return traceIdHolder.get();
    }

    public static String getUserId() {
        return userIdHolder.get();
    }

    public static void setTraceId(UUID traceId) {  // Changed parameter from String to UUID
        traceIdHolder.set(traceId);
        // Store string representation in MDC for logging
        MDC.put("traceId", traceId != null ? traceId.toString() : null);
    }

    // Convenience method to accept String and convert to UUID
    public static void setTraceId(String traceId) {
        UUID uuid = traceId != null ? UUID.fromString(traceId) : null;
        setTraceId(uuid);
    }

    public static void setUserId(String userId) {
        userIdHolder.set(userId);
        MDC.put("userId", userId);
    }

    public static UUID generateTraceId() {  // Changed return type from String to UUID
        return UUID.randomUUID();
    }

    public static void clear() {
        traceIdHolder.remove();
        userIdHolder.remove();
        MDC.clear();
    }

    public static void copyFromParent(UUID traceId, String userId) {  // Changed parameter from String to UUID
        if (traceId != null) setTraceId(traceId);
        if (userId != null) setUserId(userId);
    }

    // Convenience method to accept String and convert to UUID
    public static void copyFromParent(String traceId, String userId) {
        UUID uuid = traceId != null ? UUID.fromString(traceId) : null;
        copyFromParent(uuid, userId);
    }

    // Helper methods for string conversion (backward compatibility)
    public static String getTraceIdAsString() {
        UUID traceId = getTraceId();
        return traceId != null ? traceId.toString() : null;
    }

    public static void setTraceIdFromString(String traceId) {
        setTraceId(traceId);
    }
}