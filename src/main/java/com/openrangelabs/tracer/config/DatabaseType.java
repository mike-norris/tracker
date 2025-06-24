package com.openrangelabs.tracer.config;

/**
 * Enumeration of supported database types for tracing storage.
 */
public enum DatabaseType {

    /**
     * PostgreSQL database - Recommended for production use
     * Features: JSONB support, advanced indexing, partitioning, excellent performance
     */
    POSTGRESQL("postgresql", "org.postgresql.Driver", "PostgreSQL"),

    /**
     * MySQL database - Widely supported
     * Features: JSON support, good performance, wide ecosystem support
     */
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "MySQL"),

    /**
     * MariaDB database - MySQL compatible with additional features
     * Features: JSON support, enhanced performance, open source
     */
    MARIADB("mariadb", "org.mariadb.jdbc.Driver", "MariaDB"),

    /**
     * MongoDB database - Document-based NoSQL
     * Features: Native JSON storage, horizontal scaling, flexible schema
     */
    MONGODB("mongodb", "mongodb", "MongoDB");

    private final String identifier;
    private final String driverClass;
    private final String displayName;

    DatabaseType(String identifier, String driverClass, String displayName) {
        this.identifier = identifier;
        this.driverClass = driverClass;
        this.displayName = displayName;
    }

    /**
     * Get the database identifier (used in configuration)
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Get the JDBC driver class name (for JDBC databases)
     */
    public String getDriverClass() {
        return driverClass;
    }

    /**
     * Get the human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this is a JDBC-based database
     */
    public boolean isJdbc() {
        return this != MONGODB;
    }

    /**
     * Check if this is a NoSQL database
     */
    public boolean isNoSql() {
        return this == MONGODB;
    }

    /**
     * Check if this database supports JSON/JSONB natively
     */
    public boolean supportsNativeJson() {
        return this == POSTGRESQL || this == MYSQL || this == MARIADB || this == MONGODB;
    }

    /**
     * Check if this database supports partitioning
     */
    public boolean supportsPartitioning() {
        return this == POSTGRESQL || this == MYSQL || this == MARIADB;
    }

    /**
     * Get the default port for this database type
     */
    public int getDefaultPort() {
        return switch (this) {
            case POSTGRESQL -> 5432;
            case MYSQL -> 3306;
            case MARIADB -> 3306;
            case MONGODB -> 27017;
        };
    }

    /**
     * Get the default database name for this database type
     */
    public String getDefaultDatabaseName() {
        return switch (this) {
            case POSTGRESQL -> "tracing";
            case MYSQL -> "tracing";
            case MARIADB -> "tracing";
            case MONGODB -> "tracing";
        };
    }

    /**
     * Get the URL prefix for this database type
     */
    public String getUrlPrefix() {
        return switch (this) {
            case POSTGRESQL -> "jdbc:postgresql://";
            case MYSQL -> "jdbc:mysql://";
            case MARIADB -> "jdbc:mariadb://";
            case MONGODB -> "mongodb://";
        };
    }

    /**
     * Create a default connection URL for this database type
     */
    public String createDefaultUrl(String host, int port, String database) {
        return switch (this) {
            case POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true", host, port, database);
            case MARIADB -> String.format("jdbc:mariadb://%s:%d/%s", host, port, database);
            case MONGODB -> String.format("mongodb://%s:%d/%s", host, port, database);
        };
    }

    /**
     * Get recommended connection pool settings for this database type
     */
    public ConnectionPoolSettings getRecommendedPoolSettings() {
        return switch (this) {
            case POSTGRESQL -> new ConnectionPoolSettings(20, 5, 30000, 600000, 1800000);
            case MYSQL -> new ConnectionPoolSettings(20, 5, 30000, 600000, 1800000);
            case MARIADB -> new ConnectionPoolSettings(20, 5, 30000, 600000, 1800000);
            case MONGODB -> new ConnectionPoolSettings(100, 10, 10000, 120000, 0); // MongoDB uses different settings
        };
    }

    /**
     * Parse database type from string (case-insensitive)
     */
    public static DatabaseType fromString(String type) {
        if (type == null) {
            throw new IllegalArgumentException("Database type cannot be null");
        }

        String normalizedType = type.trim().toLowerCase();

        for (DatabaseType dbType : values()) {
            if (dbType.getIdentifier().equals(normalizedType) ||
                    dbType.name().toLowerCase().equals(normalizedType)) {
                return dbType;
            }
        }

        throw new IllegalArgumentException(
                String.format("Unsupported database type: %s. Supported types: %s",
                        type, getSupportedTypesString())
        );
    }

    /**
     * Get a comma-separated string of supported database types
     */
    public static String getSupportedTypesString() {
        return String.join(", ",
                java.util.Arrays.stream(values())
                        .map(DatabaseType::getIdentifier)
                        .toArray(String[]::new)
        );
    }

    /**
     * Validate that the database type is supported in the current environment
     */
    public void validateSupport() {
        if (this.isJdbc()) {
            try {
                Class.forName(this.getDriverClass());
            } catch (ClassNotFoundException e) {
                throw new UnsupportedDatabaseException(
                        String.format("JDBC driver for %s not found in classpath: %s. " +
                                        "Please add the appropriate database driver dependency.",
                                this.getDisplayName(), this.getDriverClass())
                );
            }
        }
        // For MongoDB, validation would check for Spring Data MongoDB availability
        // This could be extended to check for other dependencies as needed
    }

    /**
     * Connection pool settings recommendation
     */
    public record ConnectionPoolSettings(
            int maximumPoolSize,
            int minimumIdle,
            long connectionTimeout,
            long idleTimeout,
            long maxLifetime
    ) {}

    /**
     * Exception thrown when database type is not supported
     */
    public static class UnsupportedDatabaseException extends RuntimeException {
        public UnsupportedDatabaseException(String message) {
            super(message);
        }
    }
}