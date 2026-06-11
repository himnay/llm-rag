package com.org.web.validation;

import com.org.ingestion.reader.DocumentReaderFactory;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * Backs {@link SupportedDocument} by delegating to {@link DocumentReaderFactory#supports(String)},
 * so the single source of truth for "which file types can we ingest" stays in the reader factory.
 * Spring injects the bean into this validator (Spring-managed {@code ConstraintValidatorFactory}).
 */
@RequiredArgsConstructor
public class SupportedDocumentValidator implements ConstraintValidator<SupportedDocument, MultipartFile> {

    private final DocumentReaderFactory readerFactory;

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String name = file.getOriginalFilename();
        return name != null && !name.isBlank() && readerFactory.supports(name);
    }
}
