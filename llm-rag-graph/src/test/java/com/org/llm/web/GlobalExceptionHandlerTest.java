package com.org.llm.web;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Constraint violations are mapped to a 400 response with the violation message")
    void handleConstraintReturnsBadRequestWithMessage() {
        ConstraintViolationException ex = new ConstraintViolationException("question must not be blank", Collections.emptySet());

        ResponseEntity<ProblemDetail> response = handler.handleConstraint(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("question must not be blank");
        assertThat(response.getBody().getTitle()).isEqualTo("Validation failed");
    }

    @Test
    @DisplayName("Unreadable request bodies are mapped to a 400 response with a fixed message")
    void handleUnreadableReturnsBadRequestWithFixedMessage() {
        HttpInputMessage body = new MockHttpInputMessage(new byte[0]);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("parse error", body);

        ResponseEntity<ProblemDetail> response = handler.handleUnreadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("Request body is missing or not valid JSON");
        assertThat(response.getBody().getTitle()).isEqualTo("Malformed request");
    }

    @Test
    @DisplayName("IllegalArgumentException is mapped to a 400 response with the exception message")
    void handleIllegalArgumentReturnsBadRequestWithExceptionMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("limit must be positive");

        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("limit must be positive");
        assertThat(response.getBody().getTitle()).isEqualTo("Bad request");
    }

    @Test
    @DisplayName("Unhandled exceptions are mapped to a 500 response with a generic message")
    void handleGenericReturns500WithGenericMessage() {
        ResponseEntity<ProblemDetail> response = handler.handleGeneric(new RuntimeException("db down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getTitle()).isEqualTo("Internal error");
    }
}
