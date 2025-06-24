package com.openrangelabs.tracer.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record UserAction(
        Long id,
        UUID traceId,
        String userId,
        String action,
        JsonNode actionData,
        String sessionId,
        String ipAddress,
        String userAgent,
        String httpMethod,
        String endpoint,
        Integer requestSize,
        Integer responseStatus,
        Integer responseSize,
        Long durationMs,
        Instant timestamp,
        Instant createdAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID traceId;
        private String userId;
        private String action;
        private JsonNode actionData;
        private String sessionId;
        private String ipAddress;
        private String userAgent;
        private String httpMethod;
        private String endpoint;
        private Integer requestSize;
        private Integer responseStatus;
        private Integer responseSize;
        private Long durationMs;

        public Builder traceId(UUID traceId) { this.traceId = traceId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder actionData(JsonNode actionData) { this.actionData = actionData; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder httpMethod(String httpMethod) { this.httpMethod = httpMethod; return this; }
        public Builder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
        public Builder requestSize(Integer requestSize) { this.requestSize = requestSize; return this; }
        public Builder responseStatus(Integer responseStatus) { this.responseStatus = responseStatus; return this; }
        public Builder responseSize(Integer responseSize) { this.responseSize = responseSize; return this; }
        public Builder durationMs(Long durationMs) { this.durationMs = durationMs; return this; }

        public UserAction build() {
            Instant now = Instant.now();
            return new UserAction(null, traceId, userId, action, actionData, sessionId,
                    ipAddress, userAgent, httpMethod, endpoint, requestSize, responseStatus,
                    responseSize, durationMs, now, now);
        }
    }
}
