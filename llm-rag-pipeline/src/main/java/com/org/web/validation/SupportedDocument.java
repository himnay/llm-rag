package com.org.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that an uploaded {@code MultipartFile} is a non-empty document whose extension is
 * supported by the ingestion pipeline. Keeps file-type validation declarative (handled by the
 * Bean Validation provider + {@code GlobalExceptionHandler}) rather than imperative in a controller
 * or service.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = SupportedDocumentValidator.class)
public @interface SupportedDocument {

    String message() default "Unsupported or empty document (allowed: pdf, md, txt, json, docx, pptx, xlsx, html)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
