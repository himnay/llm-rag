package com.org.web;

import com.org.chunking.strategy.UnknownChunkingStrategyException;
import com.org.exception.ExcelReadException;
import com.org.exception.RerankingException;
import com.org.exception.SearchStrategyNotFoundException;
import com.org.ingestion.db.UnknownIngestionTableException;
import com.org.ingestion.reader.UnsupportedDocumentTypeException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into consistent {@link ApiError} JSON responses with appropriate HTTP codes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean-validation failures on @Valid request bodies.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage(), null);
    }

    /**
     * Malformed / empty JSON body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request", "Request body is missing or not valid JSON", null);
    }

    /**
     * File type not supported by any registered {@link com.org.ingestion.reader.DocumentReaderStrategy}.
     */
    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<ApiError> handleUnsupportedDocumentType(UnsupportedDocumentTypeException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type", ex.getMessage(), null);
    }

    @ExceptionHandler(UnknownChunkingStrategyException.class)
    public ResponseEntity<ApiError> handleUnknownChunkingStrategy(UnknownChunkingStrategyException ex) {
        return build(HttpStatus.BAD_REQUEST, "Unknown chunking strategy", ex.getMessage(), null);
    }

    @ExceptionHandler(UnknownIngestionTableException.class)
    public ResponseEntity<ApiError> handleUnknownIngestionTable(UnknownIngestionTableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Unknown ingestion table", ex.getMessage(), null);
    }

    /**
     * Malformed/unreadable Excel workbook supplied for ingestion.
     */
    @ExceptionHandler(ExcelReadException.class)
    public ResponseEntity<ApiError> handleExcelRead(ExcelReadException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid Excel file", ex.getMessage(), null);
    }

    /**
     * Retrieval configuration error: no {@code SearchStrategy} registered for the resolved mode.
     */
    @ExceptionHandler(SearchStrategyNotFoundException.class)
    public ResponseEntity<ApiError> handleSearchStrategyNotFound(SearchStrategyNotFoundException ex) {
        log.error("Retrieval misconfiguration", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Retrieval configuration error", ex.getMessage(), null);
    }

    /**
     * LLM-based reranking failed or was interrupted.
     */
    @ExceptionHandler(RerankingException.class)
    public ResponseEntity<ApiError> handleReranking(RerankingException ex) {
        log.error("Reranking failed", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Reranking error", ex.getMessage(), null);
    }

    /**
     * Catch-all for bad arguments not covered by a more specific handler.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage(), null);
    }

    /**
     * Ingestion / IO failures.
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> handleIo(IOException ex) {
        log.error("IO error handling request", ex);
        return build(HttpStatus.BAD_GATEWAY, "Ingestion error", ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", ex.getMessage(), null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message,
                                           Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), error, message, fieldErrors));
    }
}
