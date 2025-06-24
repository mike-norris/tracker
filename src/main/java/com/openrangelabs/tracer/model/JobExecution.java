package com.openrangelabs.tracer.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record JobExecution(
        Long id,
        UUID traceId,        // Changed from String to UUID
        UUID jobId,          // Changed from String to UUID
        String jobType,
        String jobName,
        UUID parentJobId,    // Changed from String to UUID
        String userId,
        JobStatus status,
        Integer priority,
        String queueName,
        String workerId,
        JsonNode inputData,
        JsonNode outputData,
        String errorMessage,
        String errorStackTrace,
        Integer retryCount,
        Integer maxRetries,
        Instant scheduledAt,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        Integer memoryUsageMb,
        Double cpuUsagePercent,
        Instant timestamp,
        Instant createdAt,
        Instant updatedAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID traceId;       // Changed from String to UUID
        private UUID jobId;         // Changed from String to UUID
        private String jobType;
        private String jobName;
        private UUID parentJobId;   // Changed from String to UUID
        private String userId;
        private JobStatus status = JobStatus.PENDING;
        private Integer priority = 5;
        private String queueName;
        private String workerId;
        private JsonNode inputData;
        private JsonNode outputData;
        private String errorMessage;
        private String errorStackTrace;
        private Integer retryCount = 0;
        private Integer maxRetries = 3;
        private Instant scheduledAt;
        private Instant startedAt;
        private Instant completedAt;
        private Long durationMs;
        private Integer memoryUsageMb;
        private Double cpuUsagePercent;

        // Builder methods with UUID support
        public Builder traceId(UUID traceId) {
            this.traceId = traceId;
            return this;
        }

        // Convenience method to accept String and convert to UUID
        public Builder traceId(String traceId) {
            this.traceId = traceId != null ? UUID.fromString(traceId) : null;
            return this;
        }

        public Builder jobId(UUID jobId) {
            this.jobId = jobId;
            return this;
        }

        // Convenience method to accept String and convert to UUID
        public Builder jobId(String jobId) {
            this.jobId = jobId != null ? UUID.fromString(jobId) : null;
            return this;
        }

        public Builder jobType(String jobType) { this.jobType = jobType; return this; }
        public Builder jobName(String jobName) { this.jobName = jobName; return this; }

        public Builder parentJobId(UUID parentJobId) {
            this.parentJobId = parentJobId;
            return this;
        }

        // Convenience method to accept String and convert to UUID
        public Builder parentJobId(String parentJobId) {
            this.parentJobId = parentJobId != null ? UUID.fromString(parentJobId) : null;
            return this;
        }

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder status(JobStatus status) { this.status = status; return this; }
        public Builder priority(Integer priority) { this.priority = priority; return this; }
        public Builder queueName(String queueName) { this.queueName = queueName; return this; }
        public Builder workerId(String workerId) { this.workerId = workerId; return this; }
        public Builder inputData(JsonNode inputData) { this.inputData = inputData; return this; }
        public Builder outputData(JsonNode outputData) { this.outputData = outputData; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder errorStackTrace(String errorStackTrace) { this.errorStackTrace = errorStackTrace; return this; }
        public Builder retryCount(Integer retryCount) { this.retryCount = retryCount; return this; }
        public Builder maxRetries(Integer maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder scheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder durationMs(Long durationMs) { this.durationMs = durationMs; return this; }
        public Builder memoryUsageMb(Integer memoryUsageMb) { this.memoryUsageMb = memoryUsageMb; return this; }
        public Builder cpuUsagePercent(Double cpuUsagePercent) { this.cpuUsagePercent = cpuUsagePercent; return this; }

        public JobExecution build() {
            Instant now = Instant.now();
            return new JobExecution(null, traceId, jobId, jobType, jobName, parentJobId, userId,
                    status, priority, queueName, workerId, inputData, outputData, errorMessage,
                    errorStackTrace, retryCount, maxRetries, scheduledAt, startedAt, completedAt,
                    durationMs, memoryUsageMb, cpuUsagePercent, now, now, now);
        }
    }
}