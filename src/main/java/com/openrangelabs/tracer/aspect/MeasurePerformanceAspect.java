package com.openrangelabs.tracer.aspect;

import com.openrangelabs.tracer.annotation.MeasurePerformance;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Random;

@Aspect
@Component
public class MeasurePerformanceAspect {

    private static final Logger logger = LoggerFactory.getLogger(MeasurePerformanceAspect.class);
    private final Random random = new Random();

    @Around("@annotation(measurePerformance)")
    public Object measurePerformance(ProceedingJoinPoint joinPoint, MeasurePerformance measurePerformance) throws Throwable {

        // Check sampling
        if (measurePerformance.sample() && random.nextDouble() > measurePerformance.sampleRate()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        String operationName = measurePerformance.value().isEmpty() ? methodName : measurePerformance.value();

        long startTime = System.currentTimeMillis();
        long startMemory = 0;

        if (measurePerformance.measureMemory()) {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            startMemory = memoryBean.getHeapMemoryUsage().getUsed();
        }

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // Log performance metrics
            if (measurePerformance.logSlowOperations() && duration > measurePerformance.slowThresholdMs()) {
                logger.warn("Slow operation detected: {} took {}ms (threshold: {}ms)",
                        operationName, duration, measurePerformance.slowThresholdMs());
            } else {
                logger.debug("Operation {} completed in {}ms", operationName, duration);
            }

            if (measurePerformance.measureMemory() && startMemory > 0) {
                MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
                long endMemory = memoryBean.getHeapMemoryUsage().getUsed();
                long memoryUsed = (endMemory - startMemory) / (1024 * 1024);
                logger.debug("Operation {} used {}MB memory", operationName, memoryUsed);
            }

            return result;

        } catch (Throwable throwable) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Operation {} failed after {}ms: {}", operationName, duration, throwable.getMessage());
            throw throwable;
        }
    }

    @Around("@within(measurePerformance) && !@annotation(com.openrangelabs.tracer.annotation.MeasurePerformance)")
    public Object measurePerformanceClass(ProceedingJoinPoint joinPoint, MeasurePerformance measurePerformance) throws Throwable {
        return measurePerformance(joinPoint, measurePerformance);
    }
}
