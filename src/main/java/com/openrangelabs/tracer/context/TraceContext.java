package com.openrangelabs.tracer.context;

import org.slf4j.MDC;
import java.util.UUID;

public class TraceContext {
    private static final ThreadLocal<UUID> traceIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> userIdHolder = new ThreadLocal<>();

    public static UUID getTraceId() {
        return traceIdHolder.get();
    }

    public static String getUserId() {
        return userIdHolder.get();
    }

    public static void setTraceId(UUID traceId) {
        traceIdHolder.set(traceId);
        MDC.put("traceId", traceId.toString());
    }

    public static void setUserId(String userId) {
        userIdHolder.set(userId);
        MDC.put("userId", userId);
    }

    public static UUID generateTraceId() {
        return UUID.randomUUID();
    }

    public static void clear() {
        traceIdHolder.remove();
        userIdHolder.remove();
        MDC.clear();
    }

    public static void copyFromParent(UUID traceId, String userId) {
        if (traceId != null) setTraceId(traceId);
        if (userId != null) setUserId(userId);
    }
}
