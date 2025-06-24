package com.openrangelabs.tracer.aspect;

import com.openrangelabs.tracer.annotation.TraceUserAction;
import com.openrangelabs.tracer.service.TracingService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
public class TraceUserActionAspect {

    private static final Logger logger = LoggerFactory.getLogger(TraceUserActionAspect.class);

    private final TracingService tracingService;
    private final ObjectMapper objectMapper;

    public TraceUserActionAspect(TracingService tracingService, ObjectMapper objectMapper) {
        this.tracingService = tracingService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(traceUserAction)")
    public Object traceUserAction(ProceedingJoinPoint joinPoint, TraceUserAction traceUserAction) throws Throwable {

        // Get HTTP request from context
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            logger.warn("No HTTP request context found for @TraceUserAction on method: {}",
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();

        // Determine action name
        String actionName = traceUserAction.value().isEmpty() ? methodName : traceUserAction.value();

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            exception = throwable;
            throw throwable;
        } finally {
            try {
                // Build action data
                Map<String, Object> actionData = new HashMap<>();
                actionData.put("category", traceUserAction.category());
                actionData.put("method", methodName);
                actionData.put("class", signature.getDeclaringTypeName());

                if (traceUserAction.includeArgs() && joinPoint.getArgs().length > 0) {
                    actionData.put("arguments", joinPoint.getArgs());
                }

                if (traceUserAction.includeResult() && result != null) {
                    actionData.put("result", result);
                }

                if (exception != null) {
                    actionData.put("error", exception.getMessage());
                    actionData.put("errorType", exception.getClass().getSimpleName());
                }

                if (traceUserAction.measureTiming()) {
                    long duration = System.currentTimeMillis() - startTime;
                    actionData.put("executionTimeMs", duration);
                }

                // Log the action
                if (traceUserAction.async()) {
                    tracingService.logUserAction(actionName, actionData, request);
                } else {
                    tracingService.logUserAction(actionName, actionData, request);
                }

            } catch (Exception e) {
                logger.error("Failed to log user action for method: {}", methodName, e);
            }
        }
    }

    @Around("@within(traceUserAction) && !@annotation(com.openrangelabs.tracer.annotation.TraceUserAction)")
    public Object traceUserActionClass(ProceedingJoinPoint joinPoint, TraceUserAction traceUserAction) throws Throwable {
        // Handle class-level annotation (apply to all public methods)
        return traceUserAction(joinPoint, traceUserAction);
    }
}
