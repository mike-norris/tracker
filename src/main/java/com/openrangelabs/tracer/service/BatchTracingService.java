package com.openrangelabs.tracer.service;

import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.event.UserActionEvent;
import com.openrangelabs.tracer.event.JobExecutionEvent;
import com.openrangelabs.tracer.model.UserAction;
import com.openrangelabs.tracer.model.JobExecution;
import com.openrangelabs.tracer.repository.UserActionRepository;
import com.openrangelabs.tracer.repository.JobExecutionRepository;
import com.openrangelabs.tracer.metrics.TracingMetrics;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class BatchTracingService {

    private static final Logger logger = LoggerFactory.getLogger(BatchTracingService.class);

    private final UserActionRepository userActionRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final TracingProperties properties;
    private final TracingMetrics tracingMetrics;

    private final List<UserAction> userActionBatch = new ArrayList<>();
    private final List<JobExecution> jobExecutionBatch = new ArrayList<>();
    private final ReentrantLock userActionLock = new ReentrantLock();
    private final ReentrantLock jobExecutionLock = new ReentrantLock();

    public BatchTracingService(UserActionRepository userActionRepository,
                               JobExecutionRepository jobExecutionRepository,
                               TracingProperties properties,
                               Optional<TracingMetrics> tracingMetrics) {
        this.userActionRepository = userActionRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.properties = properties;
        this.tracingMetrics = tracingMetrics.orElse(null);
    }

    @EventListener
    @Async("tracingExecutor")
    public void handleUserAction(UserActionEvent event) {
        userActionLock.lock();
        try {
            userActionBatch.add(event.getUserAction());
            if (tracingMetrics != null) {
                tracingMetrics.incrementBatchSize();
            }

            if (userActionBatch.size() >= properties.database().batchSize()) {
                flushUserActions();
            }
        } finally {
            userActionLock.unlock();
        }
    }

    @EventListener
    @Async("tracingExecutor")
    public void handleJobExecution(JobExecutionEvent event) {
        jobExecutionLock.lock();
        try {
            jobExecutionBatch.add(event.getJobExecution());
            if (tracingMetrics != null) {
                tracingMetrics.incrementBatchSize();
            }

            if (jobExecutionBatch.size() >= properties.database().batchSize()) {
                flushJobExecutions();
            }
        } finally {
            jobExecutionLock.unlock();
        }
    }

    @Scheduled(fixedRate = 5000) // Flush every 5 seconds
    public void scheduledFlush() {
        flushAllBatches();
    }

    @Scheduled(fixedRate = 30000) // Log batch status every 30 seconds
    public void logBatchStatus() {
        int userActionCount = userActionBatch.size();
        int jobExecutionCount = jobExecutionBatch.size();

        if (userActionCount > 0 || jobExecutionCount > 0) {
            logger.debug("Batch status - UserActions: {}, JobExecutions: {}",
                    userActionCount, jobExecutionCount);
        }
    }

    public void flushAllBatches() {
        try {
            flushUserActions();
            flushJobExecutions();
        } catch (Exception e) {
            logger.error("Error during scheduled batch flush", e);
            tracingMetrics.incrementTracingErrors("batch_flush_error");
        }
    }

    private void flushUserActions() {
        if (userActionBatch.isEmpty()) {
            return;
        }

        userActionLock.lock();
        try {
            if (!userActionBatch.isEmpty()) {
                List<UserAction> toSave = new ArrayList<>(userActionBatch);
                userActionBatch.clear();

                long startTime = System.currentTimeMillis();
                try {
                    userActionRepository.saveBatch(toSave);
                    long duration = System.currentTimeMillis() - startTime;

                    if (tracingMetrics != null) {
                        tracingMetrics.recordBatchOperation(toSave.size(), duration);
                        tracingMetrics.setBatchSize(userActionBatch.size());
                    }

                    logger.debug("Flushed {} user actions in {}ms", toSave.size(), duration);

                } catch (Exception e) {
                    logger.error("Failed to flush user actions batch of size {}", toSave.size(), e);
                    tracingMetrics.incrementTracingErrors("user_action_batch_save");

                    // Re-add to batch for retry (with size limit to prevent memory issues)
                    if (userActionBatch.size() < properties.database().batchSize() * 2) {
                        userActionBatch.addAll(toSave);
                    }
                    throw e;
                }
            }
        } finally {
            userActionLock.unlock();
        }
    }

    private void flushJobExecutions() {
        if (jobExecutionBatch.isEmpty()) {
            return;
        }

        jobExecutionLock.lock();
        try {
            if (!jobExecutionBatch.isEmpty()) {
                List<JobExecution> toSave = new ArrayList<>(jobExecutionBatch);
                jobExecutionBatch.clear();

                long startTime = System.currentTimeMillis();
                try {
                    jobExecutionRepository.saveBatch(toSave);
                    long duration = System.currentTimeMillis() - startTime;

                    tracingMetrics.recordBatchOperation(toSave.size(), duration);
                    tracingMetrics.setBatchSize(jobExecutionBatch.size());

                    logger.debug("Flushed {} job executions in {}ms", toSave.size(), duration);

                } catch (Exception e) {
                    logger.error("Failed to flush job executions batch of size {}", toSave.size(), e);
                    tracingMetrics.incrementTracingErrors("job_execution_batch_save");

                    // Re-add to batch for retry (with size limit to prevent memory issues)
                    if (jobExecutionBatch.size() < properties.database().batchSize() * 2) {
                        jobExecutionBatch.addAll(toSave);
                    }
                    throw e;
                }
            }
        } finally {
            jobExecutionLock.unlock();
        }
    }

    // Manual flush methods for testing or emergency situations
    public void forceFlushUserActions() {
        logger.info("Force flushing user actions batch");
        flushUserActions();
    }

    public void forceFlushJobExecutions() {
        logger.info("Force flushing job executions batch");
        flushJobExecutions();
    }

    public void forceFlushAll() {
        logger.info("Force flushing all batches");
        flushAllBatches();
    }

    // Monitoring methods
    public int getUserActionBatchSize() {
        return userActionBatch.size();
    }

    public int getJobExecutionBatchSize() {
        return jobExecutionBatch.size();
    }

    public boolean isUserActionBatchEmpty() {
        return userActionBatch.isEmpty();
    }

    public boolean isJobExecutionBatchEmpty() {
        return jobExecutionBatch.isEmpty();
    }
}