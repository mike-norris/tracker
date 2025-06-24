package com.openrangelabs.tracer.context;

import org.slf4j.MDC;
import java.util.UUID;

public class TraceContext {
    private static final ThreadLocal<String> traceIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> userIdHolder = new ThreadLocal<>();

    public static String getTraceId() {
        return traceIdHolder.get();
    }

    public static String getUserId() {
        return userIdHolder.get();
    }

    public static void setTraceId(String traceId) {
        traceIdHolder.set(traceId);
        MDC.put("traceId", traceId);
    }

    public static void setUserId(String userId) {
        userIdHolder.set(userId);
        MDC.put("userId", userId);
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    public static void clear() {
        traceIdHolder.remove();
        userIdHolder.remove();
        MDC.clear();
    }

    public static void copyFromParent(String traceId, String userId) {
        if (traceId != null) setTraceId(traceId);
        if (userId != null) setUserId(userId);
    }
}
