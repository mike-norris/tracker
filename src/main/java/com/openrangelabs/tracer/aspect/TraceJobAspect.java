package com.openrangelabs.tracer.aspect;

import com.openrangelabs.tracer.annotation.TraceJob;
import com.openrangelabs.tracer.service.JobTracingService;
import com.openrangelabs.tracer.model.JobStatus;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.UUID;

@Aspect
@Component
public class TraceJobAspect {

    private static final Logger logger = LoggerFactory.getLogger(TraceJobAspect.class);

    private final JobTracingService jobTracingService;

    public TraceJobAspect(JobTracingService jobTracingService) {
        this.jobTracingService = jobTracingService;
    }

    @Around("@annotation(traceJob)")
    public Object traceJob(ProceedingJoinPoint joinPoint, TraceJob traceJob) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        String jobName = traceJob.jobName().isEmpty() ? methodName : traceJob.jobName();

        // Prepare input data
        Object inputData = null;
        if (traceJob.includeInputData() && joinPoint.getArgs().length > 0) {
            inputData = joinPoint.getArgs().length == 1 ? joinPoint.getArgs()[0] : joinPoint.getArgs();
        }

        // Start job tracking
        UUID jobId = jobTracingService.startJob(
                traceJob.jobType(),
                jobName,
                inputData,
                null, // parentJobId - could be enhanced to detect parent jobs
                traceJob.queueName(),
                traceJob.priority()
        );

        long startTime = System.currentTimeMillis();
        long startMemory = 0;
        double startCpu = 0;

        if (traceJob.measureResources()) {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            startMemory = memoryBean.getHeapMemoryUsage().getUsed();

            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                startCpu = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad();
            }
        }

        Object result = null;
        Throwable exception = null;

        try {
            // Update job status to running
            jobTracingService.updateJobStatus(jobId, JobStatus.RUNNING);

            result = joinPoint.proceed();

            // Complete the job successfully
            Object outputData = traceJob.includeOutputData() ? result : null;
            jobTracingService.completeJob(jobId, outputData);

            return result;

        } catch (Throwable throwable) {
            exception = throwable;

            // Fail the job
            String errorMessage = throwable.getMessage();
            String stackTrace = getStackTrace(throwable);
            jobTracingService.failJob(jobId, errorMessage, stackTrace);

            throw throwable;

        } finally {
            if (traceJob.measureResources()) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;

                // Log resource usage
                logger.info("Job {} completed in {}ms", jobId, duration);

                if (startMemory > 0) {
                    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
                    long endMemory = memoryBean.getHeapMemoryUsage().getUsed();
                    long memoryUsed = (endMemory - startMemory) / (1024 * 1024); // Convert to MB
                    logger.info("Job {} memory usage: {}MB", jobId, memoryUsed);
                }
            }
        }
    }

    private String getStackTrace(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
