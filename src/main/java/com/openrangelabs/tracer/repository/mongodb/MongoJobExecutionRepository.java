package com.openrangelabs.tracer.repository.mongodb;

import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.model.JobExecution;
import com.openrangelabs.tracer.model.JobStatus;
import com.openrangelabs.tracer.repository.JobExecutionRepository;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;


import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MongoDB implementation of JobExecutionRepository.
 * Uses Spring Data MongoDB for operations and native MongoDB aggregation for complex queries.
 */
public class MongoJobExecutionRepository implements JobExecutionRepository {

    private static final Logger logger = LoggerFactory.getLogger(MongoJobExecutionRepository.class);

    private final MongoTemplate mongoTemplate;
    private final TracingProperties properties;
    private final String collectionName;

    public MongoJobExecutionRepository(MongoTemplate mongoTemplate, TracingProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
        this.collectionName = properties.database().mongodb().jobExecutionsCollection();
    }

    // ==================== BASIC CRUD OPERATIONS ====================

    @Override
    public void save(JobExecution jobExecution) {
        try {
            mongoTemplate.save(jobExecution, collectionName);
        } catch (Exception e) {
            logger.error("Failed to save job execution: {}", jobExecution, e);
            throw new RuntimeException("Failed to save job execution", e);
        }
    }

    @Override
    public void saveAll(List<JobExecution> jobExecutions) {
        if (jobExecutions == null || jobExecutions.isEmpty()) {
            return;
        }

        try {
            mongoTemplate.insertAll(jobExecutions);
            logger.debug("Batch saved {} job executions", jobExecutions.size());
        } catch (Exception e) {
            logger.error("Failed to batch save job executions", e);
            throw new RuntimeException("Failed to batch save job executions", e);
        }
    }

    @Override
    public Optional<JobExecution> findById(Long id) {
        try {
            Query query = new Query(Criteria.where("id").is(id));
            JobExecution jobExecution = mongoTemplate.findOne(query, JobExecution.class, collectionName);
            return Optional.ofNullable(jobExecution);
        } catch (Exception e) {
            logger.error("Failed to find job execution by id: {}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<JobExecution> findByJobId(UUID jobId) {
        try {
            Query query = new Query(Criteria.where("jobId").is(jobId));
            JobExecution jobExecution = mongoTemplate.findOne(query, JobExecution.class, collectionName);
            return Optional.ofNullable(jobExecution);
        } catch (Exception e) {
            logger.error("Failed to find job execution by jobId: {}", jobId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByJobId(UUID jobId) {
        Query query = new Query(Criteria.where("jobId").is(jobId));
        return mongoTemplate.exists(query, JobExecution.class, collectionName);
    }

    // ==================== STATUS OPERATIONS ====================

    @Override
    public void updateStatus(UUID jobId, JobStatus status) {
        updateStatus(jobId, status, null);
    }

    @Override
    public void updateStatus(UUID jobId, JobStatus status, String errorMessage) {
        Query query = new Query(Criteria.where("jobId").is(jobId));
        Update update = new Update()
                .set("status", status)
                .set("updatedAt", Instant.now());

        if (errorMessage != null) {
            update.set("errorMessage", errorMessage);
        }

        mongoTemplate.updateFirst(query, update, JobExecution.class, collectionName);
    }

    @Override
    public void updateCompletion(UUID jobId, JobStatus status, Object outputData,
                                 Instant completedAt, Long durationMs) {
        Query query = new Query(Criteria.where("jobId").is(jobId));
        Update update = new Update()
                .set("status", status)
                .set("outputData", outputData)
                .set("completedAt", completedAt)
                .set("durationMs", durationMs)
                .set("updatedAt", Instant.now());

        mongoTemplate.updateFirst(query, update, JobExecution.class, collectionName);
    }

    @Override
    public void updateFailure(UUID jobId, String errorMessage, String stackTrace, Instant failedAt) {
        Query query = new Query(Criteria.where("jobId").is(jobId));
        Update update = new Update()
                .set("status", JobStatus.FAILED)
                .set("errorMessage", errorMessage)
                .set("errorStackTrace", stackTrace)
                .set("completedAt", failedAt)
                .set("updatedAt", Instant.now());

        mongoTemplate.updateFirst(query, update, JobExecution.class, collectionName);
    }

    @Override
    public void updateRetry(UUID jobId, int retryCount, Instant nextRetryAt) {
        Query query = new Query(Criteria.where("jobId").is(jobId));
        Update update = new Update()
                .set("status", JobStatus.RETRYING)
                .set("retryCount", retryCount)
                .set("scheduledAt", nextRetryAt)
                .set("updatedAt", Instant.now());

        mongoTemplate.updateFirst(query, update, JobExecution.class, collectionName);
    }

    // ==================== QUERY OPERATIONS ====================

    @Override
    public List<JobExecution> findByTraceId(UUID traceId) {
        Query query = new Query(Criteria.where("traceId").is(traceId))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime) {
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("timestamp").gte(startTime).lte(endTime))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByStatus(JobStatus status) {
        Query query = new Query(Criteria.where("status").is(status))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByStatus(JobStatus status, int limit) {
        Query query = new Query(Criteria.where("status").is(status))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(limit);
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByJobType(String jobType) {
        Query query = new Query(Criteria.where("jobType").is(jobType))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByJobTypeAndTimeRange(String jobType, Instant startTime, Instant endTime) {
        Query query = new Query(Criteria.where("jobType").is(jobType)
                .and("timestamp").gte(startTime).lte(endTime))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByQueueName(String queueName) {
        Query query = new Query(Criteria.where("queueName").is(queueName))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByQueueNameAndStatus(String queueName, JobStatus status) {
        Query query = new Query(Criteria.where("queueName").is(queueName)
                .and("status").is(status))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByQueueNameAndStatus(String queueName, JobStatus status, int limit) {
        Query query = new Query(Criteria.where("queueName").is(queueName)
                .and("status").is(status))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"))
                .limit(limit);
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByParentJobId(UUID parentJobId) {
        Query query = new Query(Criteria.where("parentJobId").is(parentJobId))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findByWorkerId(String workerId) {
        Query query = new Query(Criteria.where("workerId").is(workerId))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findLongRunningJobs(long thresholdMs) {
        Instant thresholdTime = Instant.now().minusMillis(thresholdMs);
        Query query = new Query(Criteria.where("status").is(JobStatus.RUNNING)
                .and("startedAt").lt(thresholdTime)
                .and("startedAt").ne(null))
                .with(Sort.by(Sort.Direction.ASC, "startedAt"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findStuckJobs(long thresholdMs) {
        Instant thresholdTime = Instant.now().minusMillis(thresholdMs);
        Query query = new Query(Criteria.where("status").is(JobStatus.PENDING)
                .and("createdAt").lt(thresholdTime))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobExecution> findRetryableFailedJobs(int maxRetries) {
        Query query = new Query(Criteria.where("status").is(JobStatus.FAILED)
                .and("retryCount").lt("maxRetries")
                .and("retryCount").lt(maxRetries))
                .with(Sort.by(Sort.Direction.ASC, "completedAt"));
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    // ==================== AGGREGATION OPERATIONS ====================

    @Override
    public long countByTraceId(UUID traceId) {
        Query query = new Query(Criteria.where("traceId").is(traceId));
        return mongoTemplate.count(query, JobExecution.class, collectionName);
    }

    @Override
    public long countByStatus(JobStatus status) {
        Query query = new Query(Criteria.where("status").is(status));
        return mongoTemplate.count(query, JobExecution.class, collectionName);
    }

    @Override
    public long countByJobTypeAndTimeRange(String jobType, Instant startTime, Instant endTime) {
        Query query = new Query(Criteria.where("jobType").is(jobType)
                .and("timestamp").gte(startTime).lte(endTime));
        return mongoTemplate.count(query, JobExecution.class, collectionName);
    }

    @Override
    public long countByQueueNameAndStatus(String queueName, JobStatus status) {
        Query query = new Query(Criteria.where("queueName").is(queueName)
                .and("status").is(status));
        return mongoTemplate.count(query, JobExecution.class, collectionName);
    }

    @Override
    public List<JobTypeStatistics> getJobTypeStatistics(Instant startTime, Instant endTime) {
        MatchOperation match = Aggregation.match(
                Criteria.where("timestamp").gte(startTime).lte(endTime)
        );

        GroupOperation group = Aggregation.group("jobType")
                .count().as("totalJobs")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.COMPLETED)).then(1).otherwise(0)).as("completedJobs")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.FAILED)).then(1).otherwise(0)).as("failedJobs")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.RUNNING)).then(1).otherwise(0)).as("runningJobs")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.PENDING)).then(1).otherwise(0)).as("pendingJobs")
                .avg("durationMs").as("avgDuration")
                .avg("retryCount").as("avgRetryCount");

        ProjectionOperation project = Aggregation.project("totalJobs", "completedJobs", "failedJobs",
                        "runningJobs", "pendingJobs", "avgDuration", "avgRetryCount")
                .and("_id").as("jobType")
                .and(ArithmeticOperators.Multiply.valueOf("completedJobs").multiplyBy(100.0))
                .divide("totalJobs").as("successRate");

        SortOperation sort = Aggregation.sort(Sort.Direction.DESC, "totalJobs");

        Aggregation aggregation = Aggregation.newAggregation(match, group, project, sort);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> new JobTypeStatistics(
                        doc.getString("jobType"),
                        doc.getLong("totalJobs"),
                        doc.getLong("completedJobs"),
                        doc.getLong("failedJobs"),
                        doc.getLong("runningJobs"),
                        doc.getLong("pendingJobs"),
                        doc.getDouble("avgDuration"),
                        doc.getDouble("avgDuration"), // Using avg as approximation for p95
                        doc.getDouble("successRate"),
                        doc.getDouble("avgRetryCount")
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<QueueStatistics> getQueueStatistics() {
        GroupOperation group = Aggregation.group("queueName")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.PENDING)).then(1).otherwise(0)).as("pendingJobs")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.RUNNING)).then(1).otherwise(0)).as("runningJobs")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.COMPLETED)
                        .and("completedAt").gte(Instant.now().minus(java.time.Duration.ofDays(1)))).then(1).otherwise(0)).as("completedToday")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.FAILED)
                        .and("completedAt").gte(Instant.now().minus(java.time.Duration.ofDays(1)))).then(1).otherwise(0)).as("failedToday")
                .avg("durationMs").as("avgProcessingTime")
                .min(ConditionalOperators.when(Criteria.where("status").is(JobStatus.PENDING)).then("$createdAt").otherwise(null)).as("oldestPending");

        ProjectionOperation project = Aggregation.project("pendingJobs", "runningJobs", "completedToday",
                        "failedToday", "avgProcessingTime", "oldestPending")
                .and("_id").as("queueName")
                .andLiteral(0.0).as("avgWaitTime"); // MongoDB doesn't easily calculate wait time without complex aggregation

        SortOperation sort = Aggregation.sort(Sort.Direction.DESC, "pendingJobs");

        Aggregation aggregation = Aggregation.newAggregation(group, project, sort);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> new QueueStatistics(
                        doc.getString("queueName"),
                        doc.getLong("pendingJobs"),
                        doc.getLong("runningJobs"),
                        doc.getLong("completedToday"),
                        doc.getLong("failedToday"),
                        doc.getDouble("avgWaitTime"),
                        doc.getDouble("avgProcessingTime"),
                        doc.getDate("oldestPending") != null ? doc.getDate("oldestPending").toInstant() : null
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkerStatistics> getWorkerStatistics(Instant startTime, Instant endTime) {
        MatchOperation match = Aggregation.match(
                Criteria.where("workerId").ne(null)
                        .and("timestamp").gte(startTime).lte(endTime)
        );

        GroupOperation group = Aggregation.group("workerId")
                .count().as("jobsProcessed")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.COMPLETED)).then(1).otherwise(0)).as("jobsCompleted")
                .sum(ConditionalOperators.when(Criteria.where("status").is(JobStatus.FAILED)).then(1).otherwise(0)).as("jobsFailed")
                .avg("durationMs").as("avgDuration")
                .max("updatedAt").as("lastActive");

        ProjectionOperation project = Aggregation.project("jobsProcessed", "jobsCompleted", "jobsFailed",
                        "avgDuration", "lastActive")
                .and("_id").as("workerId")
                .and(ArithmeticOperators.Multiply.valueOf("jobsCompleted").multiplyBy(100.0))
                .divide("jobsProcessed").as("successRate");

        SortOperation sort = Aggregation.sort(Sort.Direction.DESC, "jobsProcessed");

        Aggregation aggregation = Aggregation.newAggregation(match, group, project, sort);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> {
                    Instant lastActive = doc.getDate("lastActive").toInstant();
                    boolean isActive = lastActive.isAfter(Instant.now().minus(java.time.Duration.ofMinutes(5)));

                    return new WorkerStatistics(
                            doc.getString("workerId"),
                            doc.getLong("jobsProcessed"),
                            doc.getLong("jobsCompleted"),
                            doc.getLong("jobsFailed"),
                            doc.getDouble("avgDuration"),
                            doc.getDouble("successRate"),
                            lastActive,
                            isActive
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<JobPerformanceStatistics> getJobPerformanceStatistics(Instant startTime, Instant endTime) {
        MatchOperation match = Aggregation.match(
                Criteria.where("timestamp").gte(startTime).lte(endTime)
                        .and("durationMs").ne(null)
        );

        GroupOperation group = Aggregation.group("jobName", "jobType")
                .count().as("executionCount")
                .avg("durationMs").as("avgDuration")
                .max("durationMs").as("maxDuration")
                .avg("memoryUsageMb").as("avgMemory")
                .avg("cpuUsagePercent").as("avgCpu");

        ProjectionOperation project = Aggregation.project("executionCount", "avgDuration", "maxDuration",
                        "avgMemory", "avgCpu")
                .and("_id.jobName").as("jobName")
                .and("_id.jobType").as("jobType")
                .and("$avgDuration").as("p50Duration") // Approximation
                .and("$avgDuration").as("p95Duration") // Approximation
                .and("$avgDuration").as("p99Duration"); // Approximation

        SortOperation sort = Aggregation.sort(Sort.Direction.DESC, "executionCount");

        Aggregation aggregation = Aggregation.newAggregation(match, group, project, sort);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> new JobPerformanceStatistics(
                        doc.getString("jobName"),
                        doc.getString("jobType"),
                        doc.getLong("executionCount"),
                        doc.getDouble("avgDuration"),
                        doc.getDouble("p50Duration"),
                        doc.getDouble("p95Duration"),
                        doc.getDouble("p99Duration"),
                        doc.getDouble("maxDuration"),
                        doc.getDouble("avgMemory"),
                        doc.getDouble("avgCpu")
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<FailureStatistics> getFailureStatistics(Instant startTime, Instant endTime) {
        MatchOperation match = Aggregation.match(
                Criteria.where("status").is(JobStatus.FAILED)
                        .and("timestamp").gte(startTime).lte(endTime)
        );

        GroupOperation group = Aggregation.group("errorMessage")
                .count().as("occurrenceCount")
                .addToSet("jobType").as("affectedJobTypes")
                .addToSet("queueName").as("affectedQueues")
                .min("completedAt").as("firstOccurrence")
                .max("completedAt").as("lastOccurrence");

        ProjectionOperation project = Aggregation.project("occurrenceCount", "affectedJobTypes",
                        "affectedQueues", "firstOccurrence", "lastOccurrence")
                .and("_id").as("errorMessage")
                .andLiteral("Error").as("errorType") // Static value
                .andLiteral(0.0).as("failureRate"); // Would need additional calculation

        SortOperation sort = Aggregation.sort(Sort.Direction.DESC, "occurrenceCount");
        LimitOperation limit = Aggregation.limit(20);

        Aggregation aggregation = Aggregation.newAggregation(match, group, project, sort, limit);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> new FailureStatistics(
                        doc.getString("errorType"),
                        doc.getString("errorMessage"),
                        doc.getLong("occurrenceCount"),
                        doc.getList("affectedJobTypes", String.class),
                        doc.getList("affectedQueues", String.class),
                        doc.getDate("firstOccurrence").toInstant(),
                        doc.getDate("lastOccurrence").toInstant(),
                        doc.getDouble("failureRate")
                ))
                .collect(Collectors.toList());
    }

    // ==================== SEARCH OPERATIONS ====================

    @Override
    public List<JobExecution> searchByCriteria(SearchCriteria criteria) {
        Query query = buildQuery(criteria);
        return mongoTemplate.find(query, JobExecution.class, collectionName);
    }

    @Override
    public SearchResult<JobExecution> searchWithPagination(SearchCriteria criteria, int page, int size) {
        Query query = buildQuery(criteria);

        // Count total elements
        long totalElements = mongoTemplate.count(query, JobExecution.class, collectionName);

        // Apply pagination
        query.skip(page * size).limit(size);

        List<JobExecution> content = mongoTemplate.find(query, JobExecution.class, collectionName);

        return new SearchResult<>(
                content,
                page,
                size,
                totalElements,
                (int) Math.ceil((double) totalElements / size),
                (page + 1) * size < totalElements,
                page > 0
        );
    }

    // ==================== MAINTENANCE OPERATIONS ====================

    @Override
    public int deleteOlderThan(Instant cutoffTime) {
        Query query = new Query(Criteria.where("createdAt").lt(cutoffTime));
        long deletedCount = mongoTemplate.remove(query, JobExecution.class, collectionName).getDeletedCount();
        logger.info("Deleted {} job executions older than {}", deletedCount, cutoffTime);
        return (int) deletedCount;
    }

    @Override
    public int deleteByTraceId(UUID traceId) {
        Query query = new Query(Criteria.where("traceId").is(traceId));
        long deletedCount = mongoTemplate.remove(query, JobExecution.class, collectionName).getDeletedCount();
        logger.info("Deleted {} job executions for trace {}", deletedCount, traceId);
        return (int) deletedCount;
    }

    @Override
    public int cleanupCompletedJobs(Instant cutoffTime) {
        Query query = new Query(Criteria.where("status").in(JobStatus.COMPLETED, JobStatus.FAILED)
                .and("completedAt").lt(cutoffTime));
        long deletedCount = mongoTemplate.remove(query, JobExecution.class, collectionName).getDeletedCount();
        logger.info("Cleaned up {} completed/failed job executions older than {}", deletedCount, cutoffTime);
        return (int) deletedCount;
    }

    @Override
    public int archiveOldJobs(Instant cutoffTime) {
        // This would typically move data to an archive collection
        // For now, we'll just count what would be archived
        Query query = new Query(Criteria.where("status").in(JobStatus.COMPLETED, JobStatus.FAILED)
                .and("completedAt").lt(cutoffTime));

        long count = mongoTemplate.count(query, JobExecution.class, collectionName);
        logger.info("Would archive {} job executions older than {}", count, cutoffTime);
        return (int) count;
    }

    @Override
    public TableStatistics getTableStatistics() {
        return getCollectionStatistics();
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private Query buildQuery(SearchCriteria criteria) {
        Criteria mongoCriteria = new Criteria();
        List<Criteria> criteriaList = new ArrayList<>();

        if (criteria.traceId() != null) {
            criteriaList.add(Criteria.where("traceId").is(criteria.traceId()));
        }
        if (criteria.jobId() != null) {
            criteriaList.add(Criteria.where("jobId").is(criteria.jobId()));
        }
        if (criteria.jobType() != null) {
            criteriaList.add(Criteria.where("jobType").is(criteria.jobType()));
        }
        if (criteria.jobName() != null) {
            criteriaList.add(Criteria.where("jobName").is(criteria.jobName()));
        }
        if (criteria.parentJobId() != null) {
            criteriaList.add(Criteria.where("parentJobId").is(criteria.parentJobId()));
        }
        if (criteria.userId() != null) {
            criteriaList.add(Criteria.where("userId").is(criteria.userId()));
        }
        if (criteria.status() != null) {
            criteriaList.add(Criteria.where("status").is(criteria.status()));
        }
        if (criteria.queueName() != null) {
            criteriaList.add(Criteria.where("queueName").is(criteria.queueName()));
        }
        if (criteria.workerId() != null) {
            criteriaList.add(Criteria.where("workerId").is(criteria.workerId()));
        }
        if (criteria.minPriority() != null) {
            criteriaList.add(Criteria.where("priority").gte(criteria.minPriority()));
        }
        if (criteria.maxPriority() != null) {
            criteriaList.add(Criteria.where("priority").lte(criteria.maxPriority()));
        }
        if (criteria.minDurationMs() != null) {
            criteriaList.add(Criteria.where("durationMs").gte(criteria.minDurationMs()));
        }
        if (criteria.maxDurationMs() != null) {
            criteriaList.add(Criteria.where("durationMs").lte(criteria.maxDurationMs()));
        }
        if (criteria.startTime() != null) {
            criteriaList.add(Criteria.where("timestamp").gte(criteria.startTime()));
        }
        if (criteria.endTime() != null) {
            criteriaList.add(Criteria.where("timestamp").lte(criteria.endTime()));
        }
        if (criteria.hasErrors() != null) {
            if (criteria.hasErrors()) {
                criteriaList.add(Criteria.where("errorMessage").ne(null));
            } else {
                criteriaList.add(Criteria.where("errorMessage").is(null));
            }
        }
        if (criteria.inputDataQuery() != null) {
            // MongoDB JSON query - assuming the query is a valid MongoDB query
            try {
                Document queryDoc = Document.parse(criteria.inputDataQuery());
                criteriaList.add(Criteria.where("inputData").is(queryDoc));
            } catch (Exception e) {
                logger.warn("Invalid MongoDB query in inputDataQuery: {}", criteria.inputDataQuery());
            }
        }
        if (criteria.outputDataQuery() != null) {
            // MongoDB JSON query - assuming the query is a valid MongoDB query
            try {
                Document queryDoc = Document.parse(criteria.outputDataQuery());
                criteriaList.add(Criteria.where("outputData").is(queryDoc));
            } catch (Exception e) {
                logger.warn("Invalid MongoDB query in outputDataQuery: {}", criteria.outputDataQuery());
            }
        }

        if (!criteriaList.isEmpty()) {
            mongoCriteria.andOperator(criteriaList.toArray(new Criteria[0]));
        }

        Query query = new Query(mongoCriteria);

        // Add sorting
        if (criteria.sortBy() != null) {
            Sort.Direction direction = criteria.sortDirection() == SearchCriteria.SortDirection.ASC ?
                    Sort.Direction.ASC : Sort.Direction.DESC;
            String sortField = getSortField(criteria.sortBy());
            query.with(Sort.by(direction, sortField));
        } else {
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
        }

        return query;
    }

    private String getSortField(SearchCriteria.SortBy sortBy) {
        return switch (sortBy) {
            case TIMESTAMP -> "timestamp";
            case DURATION -> "durationMs";
            case PRIORITY -> "priority";
            case STATUS -> "status";
            case JOB_TYPE -> "jobType";
            case QUEUE_NAME -> "queueName";
        };
    }

    private TableStatistics getCollectionStatistics() {
        try {
            MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);

            // Get basic counts using aggregation
            Aggregation countAggregation = Aggregation.newAggregation(
                    Aggregation.facet()
                            .and(Aggregation.count().as("total")).as("totalCount")
                            .and(Aggregation.match(Criteria.where("createdAt").gte(Instant.now().minus(java.time.Duration.ofDays(1)))),
                                    Aggregation.count().as("today")).as("todayCount")
                            .and(Aggregation.match(Criteria.where("createdAt").gte(Instant.now().minus(java.time.Duration.ofDays(7)))),
                                    Aggregation.count().as("week")).as("weekCount")
                            .and(Aggregation.match(Criteria.where("createdAt").gte(Instant.now().minus(java.time.Duration.ofDays(30)))),
                                    Aggregation.count().as("month")).as("monthCount")
                            .and(Aggregation.group().min("createdAt").as("oldest").max("createdAt").as("newest")).as("timeRange")
                            .and(Aggregation.group("status").count().as("count")).as("statusCounts")
            );

            AggregationResults<Document> results = mongoTemplate.aggregate(countAggregation, collectionName, Document.class);
            Document result = results.getUniqueMappedResult();

            if (result == null) {
                return new TableStatistics(0, 0, 0, 0, "0 B", "0 B", null, null, Map.of());
            }

            // Extract counts
            long totalRecords = extractCount(result, "totalCount");
            long todayRecords = extractCount(result, "todayCount");
            long weekRecords = extractCount(result, "weekCount");
            long monthRecords = extractCount(result, "monthCount");

            // Extract time range
            List<Document> timeRange = result.getList("timeRange", Document.class);
            Instant oldest = null;
            Instant newest = null;
            if (!timeRange.isEmpty()) {
                Document timeDoc = timeRange.get(0);
                if (timeDoc.getDate("oldest") != null) {
                    oldest = timeDoc.getDate("oldest").toInstant();
                }
                if (timeDoc.getDate("newest") != null) {
                    newest = timeDoc.getDate("newest").toInstant();
                }
            }

            // Extract status counts
            List<Document> statusCountsList = result.getList("statusCounts", Document.class);
            Map<JobStatus, Long> statusCounts = statusCountsList.stream()
                    .collect(Collectors.toMap(
                            doc -> JobStatus.valueOf(doc.getString("_id")),
                            doc -> doc.getLong("count")
                    ));

            // Get collection size (approximation)
            long estimatedDocumentCount = collection.estimatedDocumentCount();
            String sizeInfo = estimatedDocumentCount + " documents (estimated)";

            return new TableStatistics(
                    totalRecords,
                    todayRecords,
                    weekRecords,
                    monthRecords,
                    sizeInfo,
                    "N/A (MongoDB)", // Index size not easily available
                    oldest,
                    newest,
                    statusCounts
            );

        } catch (Exception e) {
            logger.error("Failed to get collection statistics", e);
            return new TableStatistics(0, 0, 0, 0, "unknown", "unknown", null, null, Map.of());
        }
    }

    private long extractCount(Document result, String facetName) {
        List<Document> facetResult = result.getList(facetName, Document.class);
        if (facetResult != null && !facetResult.isEmpty()) {
            Document countDoc = facetResult.get(0);
            return countDoc.getLong("total") != null ? countDoc.getLong("total") :
                    countDoc.getLong("today") != null ? countDoc.getLong("today") :
                            countDoc.getLong("week") != null ? countDoc.getLong("week") :
                                    countDoc.getLong("month") != null ? countDoc.getLong("month") : 0;
        }
        return 0;
    }
}