package com.openrangelabs.tracer.config;

import com.openrangelabs.tracer.repository.TracingRepositoryFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates tracing configuration on application startup.
 * Provides helpful error messages and warnings for configuration issues.
 */
@Order(1000) // Run after other CommandLineRunners
public class TracingConfigurationValidator implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TracingConfigurationValidator.class);

    private final TracingProperties properties;
    private final TracingRepositoryFactory repositoryFactory;

    public TracingConfigurationValidator(TracingProperties properties, TracingRepositoryFactory repositoryFactory) {
        this.properties = properties;
        this.repositoryFactory = repositoryFactory;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!properties.enabled()) {
            logger.info("Tracing is disabled");
            return;
        }

        logger.info("Validating tracing configuration...");

        try {
            // Validate basic configuration
            validateBasicConfiguration();

            // Validate database configuration
            validateDatabaseConfiguration();

            // Validate repository factory
            validateRepositoryFactory();

            logger.info("✅ Tracing configuration validation completed successfully");
            logConfigurationSummary();

        } catch (Exception e) {
            logger.error("❌ Tracing configuration validation failed: {}", e.getMessage());
            throw new TracingConfigurationException("Tracing configuration validation failed", e);
        }
    }

    private void validateBasicConfiguration() {
        // Validate properties
        properties.validate();

        // Check retention months
        if (properties.retentionMonths() < 1) {
            throw new IllegalArgumentException("Retention months must be at least 1");
        }

        if (properties.retentionMonths() > 120) {
            logger.warn("⚠️  Retention period of {} months is quite long. Consider shorter retention for better performance.",
                    properties.retentionMonths());
        }

        // Validate async configuration
        if (properties.async().enabled()) {
            if (properties.async().corePoolSize() <= 0) {
                throw new IllegalArgumentException("Async core pool size must be positive");
            }
            if (properties.async().maxPoolSize() < properties.async().corePoolSize()) {
                throw new IllegalArgumentException("Async max pool size must be >= core pool size");
            }
            if (properties.async().queueCapacity() < 0) {
                throw new IllegalArgumentException("Async queue capacity cannot be negative");
            }
        }
    }

    private void validateDatabaseConfiguration() {
        DatabaseType databaseType = properties.database().type();

        // Validate database type is supported
        try {
            databaseType.validateSupport();
        } catch (DatabaseType.UnsupportedDatabaseException e) {
            throw new TracingConfigurationException(
                    String.format("Database type '%s' is not supported or drivers are missing. %s",
                            databaseType, e.getMessage()), e);
        }

        // Validate database-specific configuration
        switch (databaseType) {
            case POSTGRESQL, MYSQL, MARIADB -> validateJdbcConfiguration();
            case MONGODB -> validateMongoConfiguration();
        }
    }

    private void validateJdbcConfiguration() {
        TracingProperties.Connection connection = properties.getEffectiveConnection();

        if (connection.url() == null || connection.url().trim().isEmpty()) {
            throw new TracingConfigurationException("Database URL is required for JDBC databases");
        }

        if (connection.username() == null || connection.username().trim().isEmpty()) {
            logger.warn("⚠️  No database username specified. Make sure your database allows anonymous connections.");
        }

        // Validate connection pool settings
        TracingProperties.Pool pool = properties.getEffectivePool();
        if (pool.maximumPoolSize() <= 0) {
            throw new IllegalArgumentException("Database pool maximum size must be positive");
        }
        if (pool.minimumIdle() < 0) {
            throw new IllegalArgumentException("Database pool minimum idle cannot be negative");
        }
        if (pool.minimumIdle() > pool.maximumPoolSize()) {
            logger.warn("⚠️  Database pool minimum idle ({}) is greater than maximum size ({})",
                    pool.minimumIdle(), pool.maximumPoolSize());
        }
    }

    private void validateMongoConfiguration() {
        TracingProperties.MongoDB mongo = properties.getEffectiveMongoDB();

        if (mongo == null) {
            throw new TracingConfigurationException("MongoDB configuration is required when using MongoDB");
        }

        if (mongo.uri() == null || mongo.uri().trim().isEmpty()) {
            throw new TracingConfigurationException("MongoDB URI is required");
        }

        if (mongo.database() == null || mongo.database().trim().isEmpty()) {
            throw new TracingConfigurationException("MongoDB database name is required");
        }

        // Validate MongoDB pool settings
        if (mongo.maxPoolSize() <= 0) {
            throw new IllegalArgumentException("MongoDB max pool size must be positive");
        }
        if (mongo.minPoolSize() < 0) {
            throw new IllegalArgumentException("MongoDB min pool size cannot be negative");
        }
        if (mongo.minPoolSize() > mongo.maxPoolSize()) {
            logger.warn("⚠️  MongoDB min pool size ({}) is greater than max pool size ({})",
                    mongo.minPoolSize(), mongo.maxPoolSize());
        }
    }

    private void validateRepositoryFactory() {
        try {
            // Validate that the repository factory can create repositories
            repositoryFactory.validateConfiguration();

            // Get repository information
            TracingRepositoryFactory.RepositoryInfo info = repositoryFactory.getRepositoryInfo();

            if (!info.isSupported()) {
                throw new TracingConfigurationException(
                        String.format("Configured database type '%s' is not supported. Supported types: %s",
                                info.configuredType(),
                                java.util.Arrays.toString(info.supportedTypes())));
            }

        } catch (TracingRepositoryFactory.UnsupportedDatabaseException e) {
            throw new TracingConfigurationException("Repository factory validation failed", e);
        }
    }

    private void logConfigurationSummary() {
        DatabaseType databaseType = properties.database().type();

        logger.info("📊 Tracing Configuration Summary:");
        logger.info("   Database: {} ({})", databaseType.getDisplayName(), databaseType.getIdentifier());
        logger.info("   Tables: {} / {}",
                properties.database().userActionsTable(),
                properties.database().jobExecutionsTable());
        logger.info("   Retention: {} months", properties.retentionMonths());
        logger.info("   Async: {} (pool: {}-{})",
                properties.async().enabled() ? "enabled" : "disabled",
                properties.async().corePoolSize(),
                properties.async().maxPoolSize());
        logger.info("   Monitoring: {} (endpoint: {})",
                properties.monitoring().metricsEnabled() ? "enabled" : "disabled",
                properties.monitoring().endpoint());

        // Database-specific information
        switch (databaseType) {
            case POSTGRESQL, MYSQL, MARIADB -> {
                TracingProperties.Connection conn = properties.getEffectiveConnection();
                logger.info("   Connection: {} (pool: {}-{})",
                        maskPassword(conn.url()),
                        properties.getEffectivePool().minimumIdle(),
                        properties.getEffectivePool().maximumPoolSize());
            }
            case MONGODB -> {
                TracingProperties.MongoDB mongo = properties.getEffectiveMongoDB();
                if (mongo != null) {
                    logger.info("   MongoDB: {} (database: {}, pool: {}-{})",
                            maskPassword(mongo.uri()),
                            mongo.database(),
                            mongo.minPoolSize(),
                            mongo.maxPoolSize());
                    logger.info("   Collections: {} / {}",
                            mongo.userActionsCollection(),
                            mongo.jobExecutionsCollection());
                }
            }
        }
    }

    private String maskPassword(String url) {
        if (url == null) return "null";

        // Simple password masking for URLs
        return url.replaceAll("(://[^:]+:)[^@]+(@)", "$1****$2");
    }

    /**
     * Exception thrown when tracing configuration validation fails
     */
    public static class TracingConfigurationException extends RuntimeException {
        public TracingConfigurationException(String message) {
            super(message);
        }

        public TracingConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}