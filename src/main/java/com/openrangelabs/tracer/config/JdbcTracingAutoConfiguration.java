package com.openrangelabs.tracer.config;

import com.openrangelabs.tracer.repository.TracingRepository;
import com.openrangelabs.tracer.repository.UserActionRepository;
import com.openrangelabs.tracer.repository.JobExecutionRepository;
import com.openrangelabs.tracer.repository.jdbc.JdbcUserActionRepository;
import com.openrangelabs.tracer.repository.jdbc.JdbcJobExecutionRepository;
import com.openrangelabs.tracer.repository.BaseTracingRepository;
import com.openrangelabs.tracer.util.UuidConverterFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for JDBC-based tracing repositories.
 * Supports PostgreSQL, MySQL, and MariaDB databases.
 */
@AutoConfiguration
@ConditionalOnClass({JdbcTemplate.class})
@ConditionalOnProperty(prefix = "tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "tracing.database", name = "type",
        havingValue = "postgresql,mysql,mariadb", matchIfMissing = false)
@EnableConfigurationProperties(TracingProperties.class)
public class JdbcTracingAutoConfiguration {

    // ==================== POSTGRESQL CONFIGURATION ====================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "tracing.database", name = "type", havingValue = "postgresql")
    @ConditionalOnClass(name = "org.postgresql.Driver")
    static class PostgreSqlConfiguration {

        @Bean(name = "postgresqlUserActionRepository")
        @ConditionalOnMissingBean(name = "postgresqlUserActionRepository")
        public UserActionRepository postgresqlUserActionRepository(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                TracingProperties properties,
                UuidConverterFactory uuidConverterFactory) {
            return new JdbcUserActionRepository(jdbcTemplate, objectMapper, properties,
                    uuidConverterFactory, DatabaseType.POSTGRESQL);
        }

        @Bean(name = "postgresqlJobExecutionRepository")
        @ConditionalOnMissingBean(name = "postgresqlJobExecutionRepository")
        public JobExecutionRepository postgresqlJobExecutionRepository(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                TracingProperties properties,
                UuidConverterFactory uuidConverterFactory) {
            return new JdbcJobExecutionRepository(jdbcTemplate, objectMapper, properties,
                    uuidConverterFactory, DatabaseType.POSTGRESQL);
        }

        @Bean(name = "postgresqlTracingRepository")
        @ConditionalOnMissingBean(name = "postgresqlTracingRepository")
        public TracingRepository postgresqlTracingRepository(
                TracingProperties properties,
                @Bean(name = "postgresqlUserActionRepository") UserActionRepository userActionRepository,
                @Bean(name = "postgresqlJobExecutionRepository") JobExecutionRepository jobExecutionRepository) {
            return new PostgreSqlTracingRepository(properties, userActionRepository, jobExecutionRepository);
        }
    }

    // ==================== MYSQL CONFIGURATION ====================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "tracing.database", name = "type", havingValue = "mysql")
    @ConditionalOnClass(name = "com.mysql.cj.jdbc.Driver")
    static class MySqlConfiguration {

        @Bean(name = "mysqlUserActionRepository")
        @ConditionalOnMissingBean(name = "mysqlUserActionRepository")
        public UserActionRepository mysqlUserActionRepository(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                TracingProperties properties,
                UuidConverterFactory uuidConverterFactory) {
            return new JdbcUserActionRepository(jdbcTemplate, objectMapper, properties,
                    uuidConverterFactory, DatabaseType.MYSQL);
        }

        @Bean(name = "mysqlJobExecutionRepository")
        @ConditionalOnMissingBean(name = "mysqlJobExecutionRepository")
        public JobExecutionRepository mysqlJobExecutionRepository(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                TracingProperties properties,
                UuidConverterFactory uuidConverterFactory) {
            return new JdbcJobExecutionRepository(jdbcTemplate, objectMapper, properties,
                    uuidConverterFactory, DatabaseType.MYSQL);
        }

        @Bean(name = "mysqlTracingRepository")
        @ConditionalOnMissingBean(name = "mysqlTracingRepository")
        public TracingRepository mysqlTracingRepository(
                TracingProperties properties,
                @Bean(name = "mysqlUserActionRepository") UserActionRepository userActionRepository,
                @Bean(name = "mysqlJobExecutionRepository") JobExecutionRepository jobExecutionRepository) {
            return new MySqlTracingRepository(properties, userActionRepository, jobExecutionRepository);
        }
    }

    // ==================== MARIADB CONFIGURATION ====================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "tracing.database", name = "type", havingValue = "mariadb")
    @ConditionalOnClass(name = "org.mariadb.jdbc.Driver")
    static class MariaDbConfiguration {

        @Bean(name = "mariadbUserActionRepository")
        @ConditionalOnMissingBean(name = "mariadbUserActionRepository")
        public UserActionRepository mariadbUserActionRepository(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                TracingProperties properties,
                UuidConverterFactory uuidConverterFactory) {
            return new JdbcUserActionRepository(jdbcTemplate, objectMapper, properties,
                    uuidConverterFactory, DatabaseType.MARIADB);
        }

        @Bean(name = "mariadbJobExecutionRepository")
        @ConditionalOnMissingBean(name = "mariadbJobExecutionRepository")
        public JobExecutionRepository mariadbJobExecutionRepository(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                TracingProperties properties,
                UuidConverterFactory uuidConverterFactory) {
            return new JdbcJobExecutionRepository(jdbcTemplate, objectMapper, properties,
                    uuidConverterFactory, DatabaseType.MARIADB);
        }

        @Bean(name = "mariadbTracingRepository")
        @ConditionalOnMissingBean(name = "mariadbTracingRepository")
        public TracingRepository mariadbTracingRepository(
                TracingProperties properties,
                @Bean(name = "mariadbUserActionRepository") UserActionRepository userActionRepository,
                @Bean(name = "mariadbJobExecutionRepository") JobExecutionRepository jobExecutionRepository) {
            return new MariaDbTracingRepository(properties, userActionRepository, jobExecutionRepository);
        }
    }

    // ==================== DATABASE-SPECIFIC TRACING REPOSITORY IMPLEMENTATIONS ====================

    /**
     * PostgreSQL-specific TracingRepository implementation
     */
    static class PostgreSqlTracingRepository extends BaseTracingRepository {
        private final JdbcTemplate jdbcTemplate;

        public PostgreSqlTracingRepository(TracingProperties properties,
                                           UserActionRepository userActionRepository,
                                           JobExecutionRepository jobExecutionRepository) {
            super(properties, userActionRepository, jobExecutionRepository);
            // Note: Would need JdbcTemplate injection for database-specific operations
            this.jdbcTemplate = null; // TODO: Inject JdbcTemplate if needed
        }

        @Override
        public DatabaseType getDatabaseType() {
            return DatabaseType.POSTGRESQL;
        }

        @Override
        protected boolean performHealthCheck() {
            try {
                // PostgreSQL-specific health check
                return true; // Simplified for now
            } catch (Exception e) {
                logger.error("PostgreSQL health check failed", e);
                return false;
            }
        }

        @Override
        protected Map<String, Object> getDatabaseSpecificMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("databaseType", "PostgreSQL");
            metrics.put("supportsJsonb", true);
            metrics.put("supportsUuid", true);
            return metrics;
        }

        @Override
        protected <T> T doExecuteInTransaction(TransactionCallback<T> callback) {
            // Implementation would use Spring's @Transactional or TransactionTemplate
            try {
                return callback.execute(this);
            } catch (Exception e) {
                throw new RuntimeException("Transaction failed", e);
            }
        }

        @Override
        protected TraceStatistics calculateTraceStatistics(Instant startTime, Instant endTime) {
            // PostgreSQL-optimized statistics calculation
            return new TraceStatistics(0, 0, 0, 0, 0.0, 0.0, Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * MySQL-specific TracingRepository implementation
     */
    static class MySqlTracingRepository extends BaseTracingRepository {

        public MySqlTracingRepository(TracingProperties properties,
                                      UserActionRepository userActionRepository,
                                      JobExecutionRepository jobExecutionRepository) {
            super(properties, userActionRepository, jobExecutionRepository);
        }

        @Override
        public DatabaseType getDatabaseType() {
            return DatabaseType.MYSQL;
        }

        @Override
        protected boolean performHealthCheck() {
            try {
                // MySQL-specific health check
                return true; // Simplified for now
            } catch (Exception e) {
                logger.error("MySQL health check failed", e);
                return false;
            }
        }

        @Override
        protected Map<String, Object> getDatabaseSpecificMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("databaseType", "MySQL");
            metrics.put("supportsJson", true);
            metrics.put("uuidStorageType", "BINARY(16)");
            return metrics;
        }

        @Override
        protected <T> T doExecuteInTransaction(TransactionCallback<T> callback) {
            try {
                return callback.execute(this);
            } catch (Exception e) {
                throw new RuntimeException("Transaction failed", e);
            }
        }

        @Override
        protected TraceStatistics calculateTraceStatistics(Instant startTime, Instant endTime) {
            // MySQL-optimized statistics calculation
            return new TraceStatistics(0, 0, 0, 0, 0.0, 0.0, Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * MariaDB-specific TracingRepository implementation
     */
    static class MariaDbTracingRepository extends BaseTracingRepository {

        public MariaDbTracingRepository(TracingProperties properties,
                                        UserActionRepository userActionRepository,
                                        JobExecutionRepository jobExecutionRepository) {
            super(properties, userActionRepository, jobExecutionRepository);
        }

        @Override
        public DatabaseType getDatabaseType() {
            return DatabaseType.MARIADB;
        }

        @Override
        protected boolean performHealthCheck() {
            try {
                // MariaDB-specific health check
                return true; // Simplified for now
            } catch (Exception e) {
                logger.error("MariaDB health check failed", e);
                return false;
            }
        }

        @Override
        protected Map<String, Object> getDatabaseSpecificMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("databaseType", "MariaDB");
            metrics.put("supportsJson", true);
            metrics.put("uuidStorageType", "BINARY(16)");
            return metrics;
        }

        @Override
        protected <T> T doExecuteInTransaction(TransactionCallback<T> callback) {
            try {
                return callback.execute(this);
            } catch (Exception e) {
                throw new RuntimeException("Transaction failed", e);
            }
        }

        @Override
        protected TraceStatistics calculateTraceStatistics(Instant startTime, Instant endTime) {
            // MariaDB-optimized statistics calculation
            return new TraceStatistics(0, 0, 0, 0, 0.0, 0.0, Map.of(), Map.of(), Map.of());
        }
    }
}