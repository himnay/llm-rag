package com.org.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Logs every request that reaches a controller (method, path, query string, resolved handler,
 * status, duration) so individual controllers don't each need their own logging boilerplate.
 * Registered globally via {@link WebConfig}; {@link RequestIdFilter} already puts a correlation
 * ID in the MDC, so every line emitted here carries it automatically.
 */
@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_ATTR = "requestLogging.startNanos";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_ATTR, System.nanoTime());
        log.info("--> {} {}{} | handler={}", request.getMethod(), request.getRequestURI(),
                queryString(request), handlerName(handler));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object start = request.getAttribute(START_ATTR);
        long durationMs = start instanceof Long startNanos
                ? (System.nanoTime() - startNanos) / 1_000_000 : -1;
        if (ex != null) {
            log.warn("<-- {} {} | status={} | {}ms | failed: {}", request.getMethod(),
                    request.getRequestURI(), response.getStatus(), durationMs, ex.getMessage());
        } else {
            log.info("<-- {} {} | status={} | {}ms", request.getMethod(),
                    request.getRequestURI(), response.getStatus(), durationMs);
        }
    }

    private static String queryString(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? "" : "?" + query;
    }

    private static String handlerName(Object handler) {
        return handler instanceof HandlerMethod hm
                ? hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName()
                : String.valueOf(handler);
    }
}
