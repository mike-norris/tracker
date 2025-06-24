package com.openrangelabs.tracer.config;

import com.openrangelabs.tracer.aspect.*;
import com.openrangelabs.tracer.filter.TracingFilter;
import com.openrangelabs.tracer.repository.JobExecutionRepository;
import com.openrangelabs.tracer.repository.UserActionRepository;
import com.openrangelabs.tracer.service.JobTracingService;
import com.openrangelabs.tracer.service.TracingService;
import com.openrangelabs.tracer.controller.TracingController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

import java.util.concurrent.Executor;

@AutoConfiguration
@ConditionalOnClass({JdbcTemplate.class})
@ConditionalOnProperty(prefix = "tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TracingProperties.class)
@EnableAspectJAutoProxy
@EnableAsync
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper tracingObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public UserActionRepository userActionRepository(JdbcTemplate jdbcTemplate,
                                                     ObjectMapper objectMapper,
                                                     TracingProperties properties) {
        return new UserActionRepository(jdbcTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobExecutionRepository jobExecutionRepository(JdbcTemplate jdbcTemplate,
                                                         ObjectMapper objectMapper,
                                                         TracingProperties properties) {
        return new JobExecutionRepository(jdbcTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TracingService tracingService(UserActionRepository userActionRepository,
                                         JobTracingService jobTracingService,
                                         ObjectMapper objectMapper) {
        return new TracingService(userActionRepository, jobTracingService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobTracingService jobTracingService(JobExecutionRepository jobExecutionRepository,
                                               ObjectMapper objectMapper) {
        return new JobTracingService(jobExecutionRepository, objectMapper);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public TracingFilter tracingFilter(TracingProperties properties) {
        return new TracingFilter(properties);
    }

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
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "tracing.monitoring", name = "metricsEnabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public TracingController tracingController(JdbcTemplate jdbcTemplate,
                                               TracingProperties properties) {
        return new TracingController(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "tracing", name = "autoCreateTables", havingValue = "true", matchIfMissing = true)
    public TracingSchemaInitializer tracingSchemaInitializer(JdbcTemplate jdbcTemplate,
                                                             TracingProperties properties) {
        return new TracingSchemaInitializer(jdbcTemplate, properties);
    }
}
