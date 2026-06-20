package com.org.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLoggingInterceptorTest {

    private final RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor();
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    @DisplayName("preHandle stamps a start-time attribute and lets the request continue")
    void preHandleStampsStartTimeAndContinues() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/retrieve");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(request).setAttribute(anyString(), any(Long.class));
    }

    @Test
    @DisplayName("afterCompletion logs the outcome without throwing when there's no exception")
    void afterCompletionLogsSuccessWithoutThrowing() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/retrieve");
        when(request.getAttribute(any())).thenReturn(System.nanoTime());
        when(response.getStatus()).thenReturn(200);

        assertThatCode(() -> interceptor.afterCompletion(request, response, new Object(), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("afterCompletion logs the failure without throwing when an exception occurred")
    void afterCompletionLogsFailureWithoutThrowing() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/generate");
        when(request.getAttribute(any())).thenReturn(System.nanoTime());
        when(response.getStatus()).thenReturn(500);

        assertThatCode(() -> interceptor.afterCompletion(request, response, new Object(),
                new RuntimeException("boom")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("afterCompletion tolerates a missing start-time attribute (negative duration, no throw)")
    void afterCompletionToleratesMissingStartAttribute() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/retrieve");
        when(request.getAttribute(any())).thenReturn(null);
        when(response.getStatus()).thenReturn(200);

        assertThatCode(() -> interceptor.afterCompletion(request, response, new Object(), null))
                .doesNotThrowAnyException();
    }
}
