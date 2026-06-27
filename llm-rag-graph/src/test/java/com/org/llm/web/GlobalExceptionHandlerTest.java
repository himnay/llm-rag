package com.org.llm.web;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest req = new MockHttpServletRequest();

    @Test
    @DisplayName("Constraint violations are mapped to a 400 response with the violation message")
    void handleConstraintReturnsBadRequestWithMessage() {
        req.setRequestURI("/api/graph/stats");
        ConstraintViolationException ex = new ConstraintViolationException("question must not be blank", Collections.emptySet());

        ResponseEntity<ApiError> response = handler.handleConstraint(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("question must not be blank");
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("Unreadable request bodies are mapped to a 400 response with a fixed message")
    void handleUnreadableReturnsBadRequestWithFixedMessage() {
        req.setRequestURI("/api/rag/query");
        HttpInputMessage body = new MockHttpInputMessage(new byte[0]);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("parse error", body);

        ResponseEntity<ApiError> response = handler.handleUnreadable(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Request body is missing or not valid JSON");
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("IllegalArgumentException is mapped to a 400 response with the exception message")
    void handleIllegalArgumentReturnsBadRequestWithExceptionMessage() {
        req.setRequestURI("/api/graph/companies/Acme/employees");
        IllegalArgumentException ex = new IllegalArgumentException("limit must be positive");

        ResponseEntity<ApiError> response = handler.handleIllegalArgument(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("limit must be positive");
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("Unhandled exceptions are mapped to a 500 response with a generic message")
    void handleGenericReturns500WithGenericMessage() {
        req.setRequestURI("/api/graph/export");

        ResponseEntity<ApiError> response = handler.handleGeneric(new RuntimeException("db down"), req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
    }
}
