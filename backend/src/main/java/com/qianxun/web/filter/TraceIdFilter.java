package com.qianxun.web.filter;

import com.qianxun.context.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(TraceContext.HEADER);
        String traceId = incoming == null || incoming.isBlank()
                ? TraceContext.newTraceId()
                : incoming.trim();
        TraceContext.set(traceId);
        MDC.put(TraceContext.MDC_KEY, traceId);
        response.setHeader(TraceContext.HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.MDC_KEY);
            TraceContext.clear();
        }
    }
}
