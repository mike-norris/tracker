package com.openrangelabs.tracer.repository;

import com.openrangelabs.tracer.config.DatabaseType;
import com.openrangelabs.tracer.config.TracingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for creating appropriate repository implementations based on configuration.
 * Uses Spring's ApplicationContext to find and instantiate the correct repository.
 */
@Component
public class TracingRepositoryFactory {

    private final ApplicationContext applicationContext;
    private final TracingProperties properties;
    private final Map<DatabaseType, String> repositoryBeans;

    @Autowired
    public TracingRepositoryFactory(ApplicationContext applicationContext, TracingProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
        this.repositoryBeans = initializeRepositoryBeans();
    }

    /**
     * Create the main tracing repository based on configuration
     */
    public TracingRepository createTracingRepository() {
        DatabaseType databaseType = properties.database().type();
        String beanName = repositoryBeans.get(databaseType);

        if (beanName == null) {
            throw new UnsupportedDatabaseException(
                    "No repository implementation found for database type: " + databaseType
            );
        }

        try {
            return applicationContext.getBean(beanName, TracingRepository.class);
        } catch (Exception e) {
            throw new RepositoryCreationException(
                    "Failed to create repository for database type: " + databaseType, e
            );
        }
    }

    /**
     * Create user action repository based on configuration
     */
    public UserActionRepository createUserActionRepository() {
        DatabaseType databaseType = properties.database().type();
        String beanName = getUserActionRepositoryBean(databaseType);

        if (beanName == null) {
            throw new UnsupportedDatabaseException(
                    "No user action repository implementation found for database type: " + databaseType
            );
        }

        try {
            return applicationContext.getBean(beanName, UserActionRepository.class);
        } catch (Exception e) {
            throw new RepositoryCreationException(
                    "Failed to create user action repository for database type: " + databaseType, e
            );
        }
    }

    /**
     * Create job execution repository based on configuration
     */
    public JobExecutionRepository createJobExecutionRepository() {
        DatabaseType databaseType = properties.database().type();
        String beanName = getJobExecutionRepositoryBean(databaseType);

        if (beanName == null) {
            throw new UnsupportedDatabaseException(
                    "No job execution repository implementation found for database type: " + databaseType
            );
        }

        try {
            return applicationContext.getBean(beanName, JobExecutionRepository.class);
        } catch (Exception e) {
            throw new RepositoryCreationException(
                    "Failed to create job execution repository for database type: " + databaseType, e
            );
        }
    }

    /**
     * Check if a database type is supported
     */
    public boolean isSupported(DatabaseType databaseType) {
        return repositoryBeans.containsKey(databaseType) &&
                applicationContext.containsBean(repositoryBeans.get(databaseType));
    }

    /**
     * Get all supported database types
     */
    public DatabaseType[] getSupportedDatabaseTypes() {
        return repositoryBeans.keySet().stream()
                .filter(this::isSupported)
                .toArray(DatabaseType[]::new);
    }

    /**
     * Get repository information for diagnostics
     */
    public RepositoryInfo getRepositoryInfo() {
        DatabaseType databaseType = properties.database().type();
        String beanName = repositoryBeans.get(databaseType);
        boolean isSupported = isSupported(databaseType);

        return new RepositoryInfo(
                databaseType,
                beanName,
                isSupported,
                getSupportedDatabaseTypes()
        );
    }

    /**
     * Validate that the configured database type is supported
     */
    public void validateConfiguration() {
        DatabaseType databaseType = properties.database().type();

        if (!isSupported(databaseType)) {
            throw new UnsupportedDatabaseException(
                    String.format(
                            "Database type '%s' is not supported or not available. " +
                                    "Supported types: %s. " +
                                    "Make sure the required dependencies are included in your classpath.",
                            databaseType,
                            String.join(", ", getSupportedDatabaseTypeNames())
                    )
            );
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private Map<DatabaseType, String> initializeRepositoryBeans() {
        Map<DatabaseType, String> beans = new HashMap<>();
        beans.put(DatabaseType.POSTGRESQL, "postgresqlTracingRepository");
        beans.put(DatabaseType.MYSQL, "mysqlTracingRepository");
        beans.put(DatabaseType.MARIADB, "mariadbTracingRepository");
        beans.put(DatabaseType.MONGODB, "mongoTracingRepository");
        return beans;
    }

    private String getUserActionRepositoryBean(DatabaseType databaseType) {
        return switch (databaseType) {
            case POSTGRESQL -> "postgresqlUserActionRepository";
            case MYSQL -> "mysqlUserActionRepository";
            case MARIADB -> "mariadbUserActionRepository";
            case MONGODB -> "mongoUserActionRepository";
        };
    }

    private String getJobExecutionRepositoryBean(DatabaseType databaseType) {
        return switch (databaseType) {
            case POSTGRESQL -> "postgresqlJobExecutionRepository";
            case MYSQL -> "mysqlJobExecutionRepository";
            case MARIADB -> "mariadbJobExecutionRepository";
            case MONGODB -> "mongoJobExecutionRepository";
        };
    }

    private String[] getSupportedDatabaseTypeNames() {
        return java.util.Arrays.stream(getSupportedDatabaseTypes())
                .map(DatabaseType::name)
                .toArray(String[]::new);
    }

    // ==================== DATA TRANSFER OBJECTS ====================

    /**
     * Repository configuration information
     */
    public record RepositoryInfo(
            DatabaseType configuredType,
            String beanName,
            boolean isSupported,
            DatabaseType[] supportedTypes
    ) {}

    // ==================== EXCEPTION CLASSES ====================

    /**
     * Exception thrown when requested database type is not supported
     */
    public static class UnsupportedDatabaseException extends RuntimeException {
        public UnsupportedDatabaseException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when repository creation fails
     */
    public static class RepositoryCreationException extends RuntimeException {
        public RepositoryCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}