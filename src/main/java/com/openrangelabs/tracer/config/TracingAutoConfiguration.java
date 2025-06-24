package com.openrangelabs.tracer.config;

import com.openrangelabs.tracer.aspect.*;
import com.openrangelabs.tracer.filter.TracingFilter;
import com.openrangelabs.tracer.repository.JobExecutionRepository;
import com.openrangelabs.tracer.repository.TracingRepository;
import com.openrangelabs.tracer.repository.TracingRepositoryFactory;
import com.openrangelabs.tracer.repository.UserActionRepository;
import com.openrangelabs.tracer.service.JobTracingService;
import com.openrangelabs.tracer.service.TracingService;
import com.openrangelabs.tracer.controller.TracingController;
import com.openrangelabs.tracer.util.TraceContextExecutor;
import com.openrangelabs.tracer.util.UuidConverterFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Main auto-configuration for the Tracer library.
 * Imports database-specific configurations and sets up core components.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TracingProperties.class)
@EnableAspectJAutoProxy
@EnableAsync
@Import({
        JdbcTracingAutoConfiguration.class,
        MongoTracingAutoConfiguration.class
})
public class TracingAutoConfiguration {

    // ==================== CORE COMPONENTS ====================

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper tracingObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public UuidConverterFactory uuidConverterFactory() {
        return new UuidConverterFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public TracingRepositoryFactory tracingRepositoryFactory(
            ApplicationContext applicationContext,
            TracingProperties properties) {
        return new TracingRepositoryFactory(applicationContext, properties);
    }

    // ==================== MAIN REPOSITORY BEAN ====================

    @Bean
    @ConditionalOnMissingBean
    public TracingRepository tracingRepository(TracingRepositoryFactory factory) {
        return factory.createTracingRepository();
    }

    // ==================== SERVICE LAYER ====================

    @Bean
    @ConditionalOnMissingBean
    public JobTracingService jobTracingService(JobExecutionRepository jobExecutionRepository,
                                               ObjectMapper objectMapper) {
        return new JobTracingService(jobExecutionRepository, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TracingService tracingService(UserActionRepository userActionRepository,
                                         JobTracingService jobTracingService,
                                         ObjectMapper objectMapper) {
        return new TracingService(userActionRepository, jobTracingService, objectMapper);
    }

    // ==================== WEB COMPONENTS ====================

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public TracingFilter tracingFilter(TracingProperties properties) {
        return new TracingFilter(properties);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "tracing.monitoring", name = "metricsEnabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    public TracingController tracingController(JdbcTemplate jdbcTemplate,
                                               TracingProperties properties,
                                               ApplicationContext applicationContext) {
        return new TracingController(jdbcTemplate, properties, applicationContext);
    }

    // ==================== ASPECT CONFIGURATION ====================

    @Bean
    @ConditionalOnMissingBean
    public TraceUserActionAspect traceUserActionAspect(TracingService tracingService,
                                                       ObjectMapper objectMapper) {
        return new TraceUserActionAspect(tracingService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceJobAspect traceJobAspect(JobTracingService jobTracingService) {
        return new TraceJobAspect(jobTracingService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PropagateTraceAspect propagateTraceAspect() {
        return new PropagateTraceAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    public MeasurePerformanceAspect measurePerformanceAspect() {
        return new MeasurePerformanceAspect();
    }

    // ==================== ASYNC CONFIGURATION ====================

    @Bean("tracingExecutor")
    @ConditionalOnProperty(prefix = "tracing.async", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "tracingExecutor")
    public Executor tracingExecutor(TracingProperties properties) {
        TracingProperties.Async asyncConfig = properties.async();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncConfig.corePoolSize());
        executor.setMaxPoolSize(asyncConfig.maxPoolSize());
        executor.setQueueCapacity(asyncConfig.queueCapacity());
        executor.setKeepAliveSeconds(asyncConfig.keepAliveSeconds());
        executor.setThreadNamePrefix("tracing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceContextExecutor traceContextExecutor() {
        return new TraceContextExecutor();
    }

    // ==================== HEALTH AND METRICS ====================

    // Commented out until Actuator dependency is available
    // @Bean
    // @ConditionalOnClass(name = "org.springframework.boot.actuator.health.HealthIndicator")
    // @ConditionalOnProperty(prefix = "tracing.monitoring", name = "healthCheck", havingValue = "true", matchIfMissing = true)
    // @ConditionalOnMissingBean
    // public TracingHealthIndicator tracingHealthIndicator(TracingRepository tracingRepository,
    //                                                      TracingProperties properties) {
    //     return new TracingHealthIndicator(tracingRepository, properties);
    // }

    // ==================== CONFIGURATION VALIDATION ====================

    @Bean
    @ConditionalOnMissingBean
    public TracingConfigurationValidator tracingConfigurationValidator(TracingProperties properties,
                                                                       TracingRepositoryFactory factory) {
        return new TracingConfigurationValidator(properties, factory);
    }
}