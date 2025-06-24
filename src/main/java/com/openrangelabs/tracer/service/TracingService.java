package com.openrangelabs.tracer.service;

import com.openrangelabs.tracer.context.TraceContext;
import com.openrangelabs.tracer.model.UserAction;
import com.openrangelabs.tracer.repository.UserActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class TracingService {

    private final UserActionRepository userActionRepository;
    private final JobTracingService jobTracingService;
    private final ObjectMapper objectMapper;

    public TracingService(UserActionRepository userActionRepository,
                          JobTracingService jobTracingService,
                          ObjectMapper objectMapper) {
        this.userActionRepository = userActionRepository;
        this.jobTracingService = jobTracingService;
        this.objectMapper = objectMapper;
    }

    public void logUserAction(String action, HttpServletRequest request) {
        logUserAction(action, null, request);
    }

    public void logUserAction(String action, Object actionData, HttpServletRequest request) {
        String traceId = TraceContext.getTraceId();
        String userId = TraceContext.getUserId();

        if (traceId == null) {
            traceId = TraceContext.generateTraceId();
            TraceContext.setTraceId(traceId);
        }

        JsonNode actionDataNode = null;
        if (actionData != null) {
            actionDataNode = objectMapper.valueToTree(actionData);
        }

        UserAction userAction = UserAction.builder()
                .traceId(traceId)
                .userId(userId)
                .action(action)
                .actionData(actionDataNode)
                .sessionId(request.getSession().getId())
                .ipAddress(getClientIpAddress(request))
                .userAgent(request.getHeader("User-Agent"))
                .httpMethod(request.getMethod())
                .endpoint(request.getRequestURI())
                .build();

        saveUserActionAsync(userAction);
    }

    @Async("tracingExecutor")
    public void saveUserActionAsync(UserAction userAction) {
        userActionRepository.save(userAction);
    }

    public void logUserActionWithTiming(String action, HttpServletRequest request,
                                        long startTime, int responseStatus) {
        long duration = System.currentTimeMillis() - startTime;

        String traceId = TraceContext.getTraceId();
        String userId = TraceContext.getUserId();

        UserAction userAction = UserAction.builder()
                .traceId(traceId)
                .userId(userId)
                .action(action)
                .sessionId(request.getSession().getId())
                .ipAddress(getClientIpAddress(request))
                .userAgent(request.getHeader("User-Agent"))
                .httpMethod(request.getMethod())
                .endpoint(request.getRequestURI())
                .durationMs(duration)
                .responseStatus(responseStatus)
                .build();

        saveUserActionAsync(userAction);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
