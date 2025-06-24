package com.openrangelabs.tracer.aspect;

import com.openrangelabs.tracer.annotation.PropagateTrace;
import com.openrangelabs.tracer.context.TraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
@Component
public class PropagateTraceAspect {

    private static final Logger logger = LoggerFactory.getLogger(PropagateTraceAspect.class);

    @Around("@annotation(propagateTrace)")
    public Object propagateTrace(ProceedingJoinPoint joinPoint, PropagateTrace propagateTrace) throws Throwable {

        // Capture current trace context
        String currentTraceId = TraceContext.getTraceId();
        String currentUserId = TraceContext.getUserId();

        // Generate new trace ID if none exists and auto-generation is enabled
        if (currentTraceId == null && propagateTrace.autoGenerate()) {
            currentTraceId = TraceContext.generateTraceId();
            TraceContext.setTraceId(currentTraceId);
        }

        try {
            return joinPoint.proceed();
        } finally {
            // Ensure trace context is maintained after method execution
            if (currentTraceId != null) {
                TraceContext.setTraceId(currentTraceId);
            }
            if (currentUserId != null && propagateTrace.copyUserContext()) {
                TraceContext.setUserId(currentUserId);
            }
        }
    }
}
