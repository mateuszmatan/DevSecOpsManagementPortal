package com.bbh.itss.dso.portal.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * One log line per API call, with its route rather than its path so pipeline keys never reach the log. Changes
 * log at INFO, reads at DEBUG. Every line of the call carries its correlation id, taken from X-Correlation-Id.
 */
@Slf4j
@Component
class RequestLog extends OncePerRequestFilter {

    static final String CORRELATION_ID = "correlationId";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        MDC.put(CORRELATION_ID, Objects.requireNonNullElseGet(request.getHeader("X-Correlation-Id"),
                () -> UUID.randomUUID().toString().substring(0, 8)));
        try {
            chain.doFilter(request, response);
        } finally {
            Object route = Objects.requireNonNullElse(
                    request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE), "unmatched route");
            long millis = (System.nanoTime() - started) / 1_000_000;
            if ("GET".equals(request.getMethod())) {
                log.debug("{} {} -> {} in {} ms", request.getMethod(), route, response.getStatus(), millis);
            } else {
                log.info("{} {} -> {} in {} ms", request.getMethod(), route, response.getStatus(), millis);
            }
            MDC.remove(CORRELATION_ID);
        }
    }
}
