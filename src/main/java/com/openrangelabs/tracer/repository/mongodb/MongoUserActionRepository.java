package com.openrangelabs.tracer.repository.mongodb;

import com.openrangelabs.tracer.config.TracingProperties;
import com.openrangelabs.tracer.model.UserAction;
import com.openrangelabs.tracer.repository.UserActionRepository;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
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
 * MongoDB implementation of UserActionRepository.
 * Uses Spring Data MongoDB for operations and native MongoDB aggregation for complex queries.
 */
public class MongoUserActionRepository implements UserActionRepository {

    private static final Logger logger = LoggerFactory.getLogger(MongoUserActionRepository.class);

    private final MongoTemplate mongoTemplate;
    private final TracingProperties properties;
    private final String collectionName;

    public MongoUserActionRepository(MongoTemplate mongoTemplate, TracingProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
        this.collectionName = properties.database().mongodb().userActionsCollection();
    }

    // ==================== BASIC CRUD OPERATIONS ====================

    @Override
    public void save(UserAction userAction) {
        try {
            mongoTemplate.save(userAction, collectionName);
        } catch (Exception e) {
            logger.error("Failed to save user action: {}", userAction, e);
            throw new RuntimeException("Failed to save user action", e);
        }
    }

    @Override
    public void saveAll(List<UserAction> userActions) {
        if (userActions == null || userActions.isEmpty()) {
            return;
        }

        try {
            mongoTemplate.insertAll(userActions);
            logger.debug("Batch saved {} user actions", userActions.size());
        } catch (Exception e) {
            logger.error("Failed to batch save user actions", e);
            throw new RuntimeException("Failed to batch save user actions", e);
        }
    }

    @Override
    public Optional<UserAction> findById(Long id) {
        try {
            Query query = new Query(Criteria.where("id").is(id));
            UserAction userAction = mongoTemplate.findOne(query, UserAction.class, collectionName);
            return Optional.ofNullable(userAction);
        } catch (Exception e) {
            logger.error("Failed to find user action by id: {}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean existsById(Long id) {
        Query query = new Query(Criteria.where("id").is(id));
        return mongoTemplate.exists(query, UserAction.class, collectionName);
    }

    // ==================== QUERY OPERATIONS ====================

    @Override
    public List<UserAction> findByTraceId(UUID traceId) {
        Query query = new Query(Criteria.where("traceId").is(traceId))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime) {
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("timestamp").gte(startTime).lte(endTime))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findByAction(String action) {
        Query query = new Query(Criteria.where("action").is(action))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findByActionAndTimeRange(String action, Instant startTime, Instant endTime) {
        Query query = new Query(Criteria.where("action").is(action)
                .and("timestamp").gte(startTime).lte(endTime))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findBySessionId(String sessionId) {
        Query query = new Query(Criteria.where("sessionId").is(sessionId))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findByIpAddress(String ipAddress) {
        Query query = new Query(Criteria.where("ipAddress").is(ipAddress))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findByEndpoint(String endpoint) {
        Query query = new Query(Criteria.where("endpoint").is(endpoint))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findByHttpMethod(String httpMethod) {
        Query query = new Query(Criteria.where("httpMethod").is(httpMethod))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findByResponseStatusRange(int minStatus, int maxStatus, Instant startTime, Instant endTime) {
        Query query = new Query(Criteria.where("responseStatus").gte(minStatus).lte(maxStatus)
                .and("timestamp").gte(startTime).lte(endTime))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public List<UserAction> findSlowActions(long durationThresholdMs, Instant startTime, Instant endTime) {
        Query query = new Query(Criteria.where("durationMs").gte(durationThresholdMs)
                .and("timestamp").gte(startTime).lte(endTime))
                .with(Sort.by(Sort.Direction.DESC, "durationMs"));
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    // ==================== AGGREGATION OPERATIONS ====================

    @Override
    public long countByTraceId(UUID traceId) {
        Query query = new Query(Criteria.where("traceId").is(traceId));
        return mongoTemplate.count(query, UserAction.class, collectionName);
    }

    @Override
    public long countByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime) {
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("timestamp").gte(startTime).lte(endTime));
        return mongoTemplate.count(query, UserAction.class, collectionName);
    }

    @Override
    public long countByActionAndTimeRange(String action, Instant startTime, Instant endTime) {
        Query query = new Query(Criteria.where("action").is(action)
                .and("timestamp").gte(startTime).lte(endTime));
        return mongoTemplate.count(query, UserAction.class, collectionName);
    }

    @Override
    public long countUniqueUsersInTimeRange(Instant startTime, Instant endTime) {
        MatchOperation match = Aggregation.match(
                Criteria.where("timestamp").gte(startTime).lte(endTime)
        );
        GroupOperation group = Aggregation.group("userId");
        CountOperation count = Aggregation.count().as("uniqueUsers");

        Aggregation aggregation = Aggregation.newAggregation(match, group, count);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        Document result = results.getUniqueMappedResult();
        return result != null ? result.getInteger("uniqueUsers", 0) : 0;
    }

    @Override
    public List<ActionStatistics> getActionStatistics(Instant startTime, Instant endTime) {
        MatchOperation match = Aggregation.match(
                Criteria.where("timestamp").gte(startTime).lte(endTime)
        );

        GroupOperation group = Aggregation.group("action")
                .count().as("count")
                .avg("durationMs").as("avgDuration")
                .sum(ConditionalOperators.when(Criteria.where("responseStatus").gte(400)).then(1).otherwise(0)).as("errorCount");

        ProjectionOperation project = Aggregation.project("count", "avgDuration", "errorCount")
                .and("_id").as("action")
                .and(ArithmeticOperators.Multiply.valueOf("errorCount").multiplyBy(100.0))
                .divide("count").as("errorRate");

        SortOperation sort = Aggregation.sort(Sort.Direction.DESC, "count");

        Aggregation aggregation = Aggregation.newAggregation(match, group, project, sort);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> new ActionStatistics(
                        doc.getString("action"),
                        doc.getLong("count"),
                        doc.getDouble("avgDuration"),
                        doc.getDouble("avgDuration"), // MongoDB doesn't have built-in percentile, using avg as approximation
                        doc.getLong("errorCount"),
                        doc.getDouble("errorRate")
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserActivityStatistics> getUserActivityStatistics(Instant startTime, Instant endTime, int limit) {
        MatchOperation match = Aggregation.match(
                Criteria.where("timestamp").gte(startTime).lte(endTime)
        );

        GroupOperation group = Aggregation.group("userId")
                .count().as("actionCount")
                .addToSet("sessionId").as("uniqueSessions")
                .min("timestamp").as("firstAction")
                .max("timestamp").as("lastAction")
                .avg("durationMs").as("avgDuration");

        ProjectionOperation project = Aggregation.project("actionCount", "firstAction", "lastAction", "avgDuration")
                .and("_id").as("userId")
                .and(ArrayOperators.Size.lengthOfArray("uniqueSessions")).as("uniqueSessionCount");

        SortOperation sort = Aggregation.sort(Sort.Direction.DESC, "actionCount");
        LimitOperation limitOp = Aggregation.limit(limit);

        Aggregation aggregation = Aggregation.newAggregation(match, group, project, sort, limitOp);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> new UserActivityStatistics(
                        doc.getString("userId"),
                        doc.getLong("actionCount"),
                        doc.getLong("uniqueSessionCount"),
                        doc.getDate("firstAction").toInstant(),
                        doc.getDate("lastAction").toInstant(),
                        doc.getDouble("avgDuration")
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<EndpointStatistics> getEndpointStatistics(Instant startTime, Instant endTime) {
        MatchOperation match = Aggregation.match(
                Criteria.where("timestamp").gte(startTime).lte(endTime)
        );

        GroupOperation group = Aggregation.group("endpoint", "httpMethod")
                .count().as("requestCount")
                .avg("durationMs").as("avgDuration")
                .sum(ConditionalOperators.when(Criteria.where("responseStatus").gte(400)).then(1).otherwise(0)).as("errorCount");

        ProjectionOperation project = Aggregation.project("requestCount", "avgDuration", "errorCount")
                .and("_id.endpoint").as("endpoint")
                .and("_id.httpMethod").as("httpMethod")
                .and(ArithmeticOperators.Multiply.valueOf("errorCount").multiplyBy(100.0))
                .divide("requestCount").as("errorRate");

        SortOperation sort = Aggregation.sort(Sort.Direction.DESC, "requestCount");

        Aggregation aggregation = Aggregation.newAggregation(match, group, project, sort);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> {
                    String endpoint = doc.getString("endpoint");
                    String httpMethod = doc.getString("httpMethod");

                    // Get status code counts for this endpoint/method
                    Map<Integer, Long> statusCounts = getStatusCodeCounts(endpoint, httpMethod, startTime, endTime);

                    return new EndpointStatistics(
                            endpoint,
                            httpMethod,
                            doc.getLong("requestCount"),
                            doc.getDouble("avgDuration"),
                            doc.getDouble("avgDuration"), // Using avg as approximation for p95
                            doc.getLong("errorCount"),
                            doc.getDouble("errorRate"),
                            statusCounts
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ErrorRateStatistics> getErrorRateStatistics(Instant startTime, Instant endTime, TimeBucket timeBucket) {
        MatchOperation match = Aggregation.match(
                Criteria.where("timestamp").gte(startTime).lte(endTime)
        );

        // Create date bucket aggregation based on time bucket
        String dateFormat = getDateFormat(timeBucket);
        GroupOperation group = Aggregation.group(DateOperators.dateOf("timestamp").toString(dateFormat))
                .count().as("totalRequests")
                .sum(ConditionalOperators.when(Criteria.where("responseStatus").gte(400)).then(1).otherwise(0)).as("errorRequests");

        ProjectionOperation project = Aggregation.project("totalRequests", "errorRequests")
                .and("_id").as("bucketStart")
                .and(ArithmeticOperators.Multiply.valueOf("errorRequests").multiplyBy(100.0))
                .divide("totalRequests").as("errorRate");

        SortOperation sort = Aggregation.sort(Sort.Direction.ASC, "bucketStart");

        Aggregation aggregation = Aggregation.newAggregation(match, group, project, sort);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .map(doc -> {
                    String bucketStartStr = doc.getString("bucketStart");
                    Instant bucketStart = parseBucketStart(bucketStartStr, timeBucket);
                    Instant bucketEnd = getEndOfBucket(bucketStart, timeBucket);

                    return new ErrorRateStatistics(
                            bucketStart,
                            bucketEnd,
                            doc.getLong("totalRequests"),
                            doc.getLong("errorRequests"),
                            doc.getDouble("errorRate")
                    );
                })
                .collect(Collectors.toList());
    }

    // ==================== SEARCH OPERATIONS ====================

    @Override
    public List<UserAction> searchByCriteria(SearchCriteria criteria) {
        Query query = buildQuery(criteria);
        return mongoTemplate.find(query, UserAction.class, collectionName);
    }

    @Override
    public SearchResult<UserAction> searchWithPagination(SearchCriteria criteria, int page, int size) {
        Query query = buildQuery(criteria);

        // Count total elements
        long totalElements = mongoTemplate.count(query, UserAction.class, collectionName);

        // Apply pagination
        query.skip(page * size).limit(size);

        List<UserAction> content = mongoTemplate.find(query, UserAction.class, collectionName);

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
        long deletedCount = mongoTemplate.remove(query, UserAction.class, collectionName).getDeletedCount();
        logger.info("Deleted {} user actions older than {}", deletedCount, cutoffTime);
        return (int) deletedCount;
    }

    @Override
    public int deleteByTraceId(UUID traceId) {
        Query query = new Query(Criteria.where("traceId").is(traceId));
        long deletedCount = mongoTemplate.remove(query, UserAction.class, collectionName).getDeletedCount();
        logger.info("Deleted {} user actions for trace {}", deletedCount, traceId);
        return (int) deletedCount;
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
        if (criteria.userId() != null) {
            criteriaList.add(Criteria.where("userId").is(criteria.userId()));
        }
        if (criteria.action() != null) {
            criteriaList.add(Criteria.where("action").is(criteria.action()));
        }
        if (criteria.sessionId() != null) {
            criteriaList.add(Criteria.where("sessionId").is(criteria.sessionId()));
        }
        if (criteria.ipAddress() != null) {
            criteriaList.add(Criteria.where("ipAddress").is(criteria.ipAddress()));
        }
        if (criteria.endpoint() != null) {
            criteriaList.add(Criteria.where("endpoint").is(criteria.endpoint()));
        }
        if (criteria.httpMethod() != null) {
            criteriaList.add(Criteria.where("httpMethod").is(criteria.httpMethod()));
        }
        if (criteria.minResponseStatus() != null) {
            criteriaList.add(Criteria.where("responseStatus").gte(criteria.minResponseStatus()));
        }
        if (criteria.maxResponseStatus() != null) {
            criteriaList.add(Criteria.where("responseStatus").lte(criteria.maxResponseStatus()));
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
        if (criteria.actionDataQuery() != null) {
            // MongoDB JSON query - assuming the query is a valid MongoDB query
            try {
                Document queryDoc = Document.parse(criteria.actionDataQuery());
                criteriaList.add(Criteria.where("actionData").is(queryDoc));
            } catch (Exception e) {
                logger.warn("Invalid MongoDB query in actionDataQuery: {}", criteria.actionDataQuery());
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
            case RESPONSE_STATUS -> "responseStatus";
            case ACTION -> "action";
            case USER_ID -> "userId";
        };
    }

    private Map<Integer, Long> getStatusCodeCounts(String endpoint, String httpMethod, Instant startTime, Instant endTime) {
        MatchOperation match = Aggregation.match(
                Criteria.where("endpoint").is(endpoint)
                        .and("httpMethod").is(httpMethod)
                        .and("timestamp").gte(startTime).lte(endTime)
        );

        GroupOperation group = Aggregation.group("responseStatus").count().as("count");

        Aggregation aggregation = Aggregation.newAggregation(match, group);
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, collectionName, Document.class);

        return results.getMappedResults().stream()
                .collect(Collectors.toMap(
                        doc -> doc.getInteger("_id"),
                        doc -> doc.getLong("count")
                ));
    }

    private String getDateFormat(TimeBucket timeBucket) {
        return switch (timeBucket) {
            case MINUTE -> "%Y-%m-%d %H:%M:00";
            case HOUR -> "%Y-%m-%d %H:00:00";
            case DAY -> "%Y-%m-%d 00:00:00";
            case WEEK -> "%Y-%m-%d 00:00:00"; // Simplified - would need week calculation
            case MONTH -> "%Y-%m-01 00:00:00";
        };
    }

    private Instant parseBucketStart(String bucketStartStr, TimeBucket timeBucket) {
        // Parse the formatted date string back to Instant
        // This is a simplified implementation - in production you'd want more robust parsing
        try {
            return Instant.parse(bucketStartStr.replace(" ", "T") + "Z");
        } catch (Exception e) {
            logger.warn("Failed to parse bucket start: {}", bucketStartStr);
            return Instant.now();
        }
    }

    private Instant getEndOfBucket(Instant bucketStart, TimeBucket timeBucket) {
        return switch (timeBucket) {
            case MINUTE -> bucketStart.plusSeconds(60);
            case HOUR -> bucketStart.plusSeconds(3600);
            case DAY -> bucketStart.plusSeconds(86400);
            case WEEK -> bucketStart.plusSeconds(604800);
            case MONTH -> bucketStart.plusSeconds(2629746); // Average month
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
            );

            AggregationResults<Document> results = mongoTemplate.aggregate(countAggregation, collectionName, Document.class);
            Document result = results.getUniqueMappedResult();

            if (result == null) {
                return new TableStatistics(0, 0, 0, 0, "0 B", "0 B", null, null);
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
                    newest
            );

        } catch (Exception e) {
            logger.error("Failed to get collection statistics", e);
            return new TableStatistics(0, 0, 0, 0, "unknown", "unknown", null, null);
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