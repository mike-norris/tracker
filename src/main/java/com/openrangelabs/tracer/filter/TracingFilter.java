package com.openrangelabs.tracer.filter;

import com.openrangelabs.tracer.context.TraceContext;
import com.openrangelabs.tracer.config.TracingProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Order(1)
public class TracingFilter implements Filter {

    private final TracingProperties properties;

    public TracingFilter(TracingProperties properties) {
        this.properties = properties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String traceId = httpRequest.getHeader(properties.traceIdHeader());

        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceContext.generateTraceId();
        }

        TraceContext.setTraceId(traceId);
        httpResponse.setHeader(properties.traceIdHeader(), traceId);

        // Extract user ID from common locations
        String userId = extractUserId(httpRequest);
        if (userId != null) {
            TraceContext.setUserId(userId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    private String extractUserId(HttpServletRequest request) {
        // Try to extract user ID from various sources

        // From JWT token (if using JWT)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Implementation would parse JWT and extract user ID
            // return JwtUtils.extractUserId(authHeader.substring(7));
        }

        // From session
        Object userIdFromSession = request.getSession().getAttribute("userId");
        if (userIdFromSession != null) {
            return userIdFromSession.toString();
        }

        // From custom header
        String userIdHeader = request.getHeader("X-User-ID");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            return userIdHeader;
        }

        return null;
    }
}
