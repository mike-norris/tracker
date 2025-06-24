package com.openrangelabs.tracer.config;

import com.openrangelabs.tracer.repository.TracingRepository;
import com.openrangelabs.tracer.repository.UserActionRepository;
import com.openrangelabs.tracer.repository.JobExecutionRepository;
import com.openrangelabs.tracer.repository.mongodb.MongoUserActionRepository;
import com.openrangelabs.tracer.repository.mongodb.MongoJobExecutionRepository;
import com.openrangelabs.tracer.repository.BaseTracingRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for MongoDB-based tracing repositories.
 */
@AutoConfiguration
@ConditionalOnClass({MongoTemplate.class})
@ConditionalOnProperty(prefix = "tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "tracing.database", name = "type", havingValue = "mongodb")
@EnableConfigurationProperties(TracingProperties.class)
public class MongoTracingAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MongoTemplate.class)
    static class MongoDbConfiguration {

        @Bean(name = "mongoUserActionRepository")
        @ConditionalOnMissingBean(name = "mongoUserActionRepository")
        public UserActionRepository mongoUserActionRepository(
                MongoTemplate mongoTemplate,
                TracingProperties properties) {
            return new MongoUserActionRepository(mongoTemplate, properties);
        }

        @Bean(name = "mongoJobExecutionRepository")
        @ConditionalOnMissingBean(name = "mongoJobExecutionRepository")
        public JobExecutionRepository mongoJobExecutionRepository(
                MongoTemplate mongoTemplate,
                TracingProperties properties) {
            return new MongoJobExecutionRepository(mongoTemplate, properties);
        }

        @Bean(name = "mongoTracingRepository")
        @ConditionalOnMissingBean(name = "mongoTracingRepository")
        public TracingRepository mongoTracingRepository(
                TracingProperties properties,
                @Bean(name = "mongoUserActionRepository") UserActionRepository userActionRepository,
                @Bean(name = "mongoJobExecutionRepository") JobExecutionRepository jobExecutionRepository,
                MongoTemplate mongoTemplate) {
            return new MongoTracingRepository(properties, userActionRepository, jobExecutionRepository, mongoTemplate);
        }
    }

    /**
     * MongoDB-specific TracingRepository implementation
     */
    static class MongoTracingRepository extends BaseTracingRepository {
        private final MongoTemplate mongoTemplate;

        public MongoTracingRepository(TracingProperties properties,
                                      UserActionRepository userActionRepository,
                                      JobExecutionRepository jobExecutionRepository,
                                      MongoTemplate mongoTemplate) {
            super(properties, userActionRepository, jobExecutionRepository);
            this.mongoTemplate = mongoTemplate;
        }

        @Override
        public DatabaseType getDatabaseType() {
            return DatabaseType.MONGODB;
        }

        @Override
        protected boolean performHealthCheck() {
            try {
                // MongoDB-specific health check
                mongoTemplate.getCollection("test").estimatedDocumentCount();
                return true;
            } catch (Exception e) {
                logger.error("MongoDB health check failed", e);
                return false;
            }
        }

        @Override
        protected Map<String, Object> getDatabaseSpecificMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("databaseType", "MongoDB");
            metrics.put("supportsNativeJson", true);
            metrics.put("supportsNativeUuid", true);
            metrics.put("databaseName", mongoTemplate.getDb().getName());

            try {
                // Get MongoDB server status
                org.bson.Document serverStatus = mongoTemplate.getDb().runCommand(
                        new org.bson.Document("serverStatus", 1)
                );
                metrics.put("mongoVersion", serverStatus.getString("version"));
                metrics.put("uptime", serverStatus.getInteger("uptime"));

                // Connection metrics
                org.bson.Document connections = serverStatus.get("connections", org.bson.Document.class);
                if (connections != null) {
                    metrics.put("currentConnections", connections.getInteger("current"));
                    metrics.put("availableConnections", connections.getInteger("available"));
                }
            } catch (Exception e) {
                logger.debug("Could not get MongoDB server status", e);
                metrics.put("mongoVersion", "unknown");
            }

            return metrics;
        }

        @Override
        protected <T> T doExecuteInTransaction(TransactionCallback<T> callback) {
            // MongoDB transactions (if supported by the MongoDB version and deployment)
            try {
                return callback.execute(this);
            } catch (Exception e) {
                throw new RuntimeException("Transaction failed", e);
            }
        }

        @Override
        protected TraceStatistics calculateTraceStatistics(Instant startTime, Instant endTime) {
            // MongoDB-optimized statistics calculation using aggregation pipeline
            try {
                String userActionsCollection = properties.database().mongodb().userActionsCollection();
                String jobExecutionsCollection = properties.database().mongodb().jobExecutionsCollection();

                // Use MongoDB aggregation for efficient statistics
                // This is a simplified implementation - full implementation would use complex aggregation pipelines
                long totalTraces = 0;
                long totalUserActions = mongoTemplate.getCollection(userActionsCollection).estimatedDocumentCount();
                long totalJobExecutions = mongoTemplate.getCollection(jobExecutionsCollection).estimatedDocumentCount();
                long uniqueUsers = 0;

                return new TraceStatistics(
                        totalTraces,
                        totalUserActions,
                        totalJobExecutions,
                        uniqueUsers,
                        totalTraces > 0 ? (double) totalUserActions / totalTraces : 0.0,
                        totalTraces > 0 ? (double) totalJobExecutions / totalTraces : 0.0,
                        Map.of(), // actionCounts - would be calculated via aggregation
                        Map.of(), // jobTypeCounts - would be calculated via aggregation
                        Map.of()  // jobStatusCounts - would be calculated via aggregation
                );
            } catch (Exception e) {
                logger.error("Failed to calculate MongoDB trace statistics", e);
                return new TraceStatistics(0, 0, 0, 0, 0.0, 0.0, Map.of(), Map.of(), Map.of());
            }
        }

        @Override
        protected String getDatabaseVersion() {
            try {
                org.bson.Document buildInfo = mongoTemplate.getDb().runCommand(
                        new org.bson.Document("buildInfo", 1)
                );
                return buildInfo.getString("version");
            } catch (Exception e) {
                logger.debug("Could not get MongoDB version", e);
                return "unknown";
            }
        }

        @Override
        protected long getConnectionCount() {
            try {
                org.bson.Document serverStatus = mongoTemplate.getDb().runCommand(
                        new org.bson.Document("serverStatus", 1)
                );
                org.bson.Document connections = serverStatus.get("connections", org.bson.Document.class);
                return connections != null ? connections.getInteger("current", 0) : 0;
            } catch (Exception e) {
                logger.debug("Could not get MongoDB connection count", e);
                return 0;
            }
        }
    }
}