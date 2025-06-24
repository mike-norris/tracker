package com.openrangelabs.tracer.service;

import com.openrangelabs.tracer.context.TraceContext;
import com.openrangelabs.tracer.model.JobExecution;
import com.openrangelabs.tracer.model.JobStatus;
import com.openrangelabs.tracer.repository.JobExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

@Service
public class JobTracingService {

    private final JobExecutionRepository jobExecutionRepository;
    private final ObjectMapper objectMapper;

    public JobTracingService(JobExecutionRepository jobExecutionRepository,
                             ObjectMapper objectMapper) {
        this.jobExecutionRepository = jobExecutionRepository;
        this.objectMapper = objectMapper;
    }

    public UUID startJob(String jobType, String jobName, Object inputData) {
        return startJob(jobType, jobName, inputData, null, "default", 5);
    }

    public UUID startJob(String jobType, String jobName, Object inputData,
                           String parentJobId, String queueName, int priority) {
        UUID jobId = UUID.randomUUID();
        UUID traceId = TraceContext.getTraceId();
        String userId = TraceContext.getUserId();

        JsonNode inputDataNode = null;
        if (inputData != null) {
            inputDataNode = objectMapper.valueToTree(inputData);
        }

        JobExecution jobExecution = JobExecution.builder()
                .traceId(traceId)
                .jobId(jobId)
                .jobType(jobType)
                .jobName(jobName)
                .parentJobId(parentJobId)
                .userId(userId)
                .status(JobStatus.PENDING)
                .priority(priority)
                .queueName(queueName)
                .inputData(inputDataNode)
                .scheduledAt(Instant.now())
                .build();

        saveJobExecutionAsync(jobExecution);

        return jobId;
    }

    public void updateJobStatus(UUID jobId, JobStatus status) {
        updateJobStatus(jobId, status, null, null);
    }

    public void updateJobStatus(UUID jobId, JobStatus status, Object outputData, String errorMessage) {
        // For status updates, we could either update in place or create a new record
        // This implementation updates in place for simplicity
        jobExecutionRepository.updateStatus(jobId, status, errorMessage);
    }

    public void completeJob(UUID jobId, Object outputData) {
        JsonNode outputDataNode = null;
        if (outputData != null) {
            outputDataNode = objectMapper.valueToTree(outputData);
        }

        // Implementation would update the job with completion time and output data
        updateJobStatus(jobId, JobStatus.COMPLETED, outputData, null);
    }

    public void failJob(UUID jobId, String errorMessage, String stackTrace) {
        updateJobStatus(jobId, JobStatus.FAILED, null, errorMessage);
    }

    @Async("tracingExecutor")
    public void saveJobExecutionAsync(JobExecution jobExecution) {
        jobExecutionRepository.save(jobExecution);
    }
}
