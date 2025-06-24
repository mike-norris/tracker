package com.openrangelabs.tracer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "tracing")
public record TracingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("X-Trace-ID") String traceIdHeader,
        @DefaultValue("traceId") String mdcKey,
        @DefaultValue("true") boolean autoCreateTables,
        @DefaultValue("18") int retentionMonths,
        Database database,
        Async async,
        Monitoring monitoring
) {
    public record Database(
            @DefaultValue("postgresql") DatabaseType type,
            @DefaultValue("user_actions") String userActionsTable,
            @DefaultValue("job_executions") String jobExecutionsTable,
            @DefaultValue("true") boolean enablePartitioning,
            @DefaultValue("1000") int batchSize,
            Connection connection,
            MongoDB mongodb
    ) {}

    public record Connection(
            String url,
            String username,
            String password,
            String driverClassName,
            Pool pool
    ) {}

    public record Pool(
            @DefaultValue("8") int maximumPoolSize,
            @DefaultValue("4") int minimumIdle,
            @DefaultValue("30000") long connectionTimeoutMs,
            @DefaultValue("600000") long idleTimeoutMs,
            @DefaultValue("1800000") long maxLifetimeMs,
            @DefaultValue("true") boolean autoCommit,
            @DefaultValue("TRANSACTION_READ_COMMITTED") String transactionIsolation
    ) {}

    public record MongoDB(
            String uri,
            String database,
            @DefaultValue("userActions") String userActionsCollection,
            @DefaultValue("jobExecutions") String jobExecutionsCollection,
            @DefaultValue("100") int maxPoolSize,
            @DefaultValue("10") int minPoolSize,
            @DefaultValue("10000") long maxWaitTimeMs,
            @DefaultValue("120000") long maxConnectionIdleTimeMs
    ) {}

    public record Async(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("tracing-executor") String threadPoolName,
            @DefaultValue("4") int corePoolSize,
            @DefaultValue("8") int maxPoolSize,
            @DefaultValue("60") int keepAliveSeconds,
            @DefaultValue("500") int queueCapacity
    ) {}

    public record Monitoring(
            @DefaultValue("true") boolean metricsEnabled,
            @DefaultValue("/actuator/tracing") String endpoint,
            @DefaultValue("true") boolean healthCheck
    ) {}

    /**
     * Get the effective database configuration based on the database type
     */
    public Database getEffectiveDatabase() {
        Database db = database();
        if (db == null) {
            // Return default configuration for PostgreSQL
            return new Database(
                    DatabaseType.POSTGRESQL,
                    "user_actions",
                    "job_executions",
                    true,
                    1000,
                    null,
                    null
            );
        }
        return db;
    }

    /**
     * Get the effective connection configuration with defaults
     */
    public Connection getEffectiveConnection() {
        Database db = getEffectiveDatabase();
        Connection conn = db.connection();

        if (conn == null) {
            DatabaseType dbType = db.type();
            return new Connection(
                    dbType.createDefaultUrl("localhost", dbType.getDefaultPort(), dbType.getDefaultDatabaseName()),
                    "tracer",
                    "password",
                    dbType.getDriverClass(),
                    getEffectivePool()
            );
        }

        return conn;
    }

    /**
     * Get the effective pool configuration with database-specific defaults
     */
    public Pool getEffectivePool() {
        Database db = getEffectiveDatabase();
        Connection conn = db.connection();
        Pool pool = conn != null ? conn.pool() : null;

        if (pool == null) {
            DatabaseType.ConnectionPoolSettings recommended = db.type().getRecommendedPoolSettings();
            return new Pool(
                    recommended.maximumPoolSize(),
                    recommended.minimumIdle(),
                    recommended.connectionTimeout(),
                    recommended.idleTimeout(),
                    recommended.maxLifetime(),
                    true,
                    "TRANSACTION_READ_COMMITTED"
            );
        }

        return pool;
    }

    /**
     * Get the effective MongoDB configuration with defaults
     */
    public MongoDB getEffectiveMongoDB() {
        Database db = getEffectiveDatabase();
        MongoDB mongo = db.mongodb();

        if (mongo == null && db.type() == DatabaseType.MONGODB) {
            return new MongoDB(
                    "mongodb://localhost:27017/tracing",
                    "tracing",
                    "userActions",
                    "jobExecutions",
                    100,
                    10,
                    10000,
                    120000
            );
        }

        return mongo;
    }

    /**
     * Validate the configuration for the specified database type
     */
    public void validate() {
        Database db = getEffectiveDatabase();
        DatabaseType dbType = db.type();

        // Validate database type is supported
        dbType.validateSupport();

        // Validate configuration completeness
        if (dbType.isJdbc()) {
            Connection conn = getEffectiveConnection();
            if (conn.url() == null || conn.url().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Database URL is required for %s", dbType.getDisplayName())
                );
            }
        } else if (dbType == DatabaseType.MONGODB) {
            MongoDB mongo = getEffectiveMongoDB();
            if (mongo == null || mongo.uri() == null || mongo.uri().trim().isEmpty()) {
                throw new IllegalArgumentException("MongoDB URI is required for MongoDB database type");
            }
        }

        // Validate retention months
        if (retentionMonths() <= 0) {
            throw new IllegalArgumentException("Retention months must be positive");
        }

        // Validate batch size
        if (db.batchSize() <= 0 || db.batchSize() > 10000) {
            throw new IllegalArgumentException("Batch size must be between 1 and 10000");
        }
    }
}