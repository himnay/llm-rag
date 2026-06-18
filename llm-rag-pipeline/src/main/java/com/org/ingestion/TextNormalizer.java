package com.org.ingestion;

import com.org.ingestion.model.IngestedDocument;
import com.org.security.pii.PiiRedactor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Cleans / normalizes raw document text before chunking + embedding, so noise from PDF/Office
 * extraction (control chars, weird unicode spaces, ragged whitespace) doesn't pollute chunks
 * or embeddings.
 *
 * <p>Steps: Unicode NFKC normalization → unify line endings → drop control chars (keep tab/newline)
 * → normalize exotic spaces → collapse intra-line whitespace → trim trailing line spaces →
 * collapse 3+ blank lines to one → PII redaction → trim. Case is preserved (matters for retrieval).</p>
 */
@Component
@RequiredArgsConstructor
public class TextNormalizer {

    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern UNICODE_SPACES =
            Pattern.compile("[\\u00A0\\u2007\\u202F\\u2000-\\u200A\\u205F\\u3000]");
    private static final Pattern INTRA_LINE_WS = Pattern.compile("[ \\t]{2,}");
    private static final Pattern TRAILING_WS = Pattern.compile("[ \\t]+\\n");
    private static final Pattern MANY_BLANK_LINES = Pattern.compile("\\n{3,}");

    private final PiiRedactor piiRedactor;

    /**
     * Cleans raw extracted text: Unicode normalization, control-char/whitespace cleanup, PII
     * redaction, and trimming. Returns an empty string for null/blank input.
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String out = Normalizer.normalize(text, Normalizer.Form.NFKC);
        out = out.replace("\r\n", "\n").replace('\r', '\n');
        out = CONTROL_CHARS.matcher(out).replaceAll("");
        out = UNICODE_SPACES.matcher(out).replaceAll(" ");
        out = INTRA_LINE_WS.matcher(out).replaceAll(" ");
        out = TRAILING_WS.matcher(out).replaceAll("\n");
        out = MANY_BLANK_LINES.matcher(out).replaceAll("\n\n");
        out = piiRedactor.redact(out);
        return out.strip();
    }

    /**
     * Returns a copy of the document with normalized content (metadata + source unchanged).
     */
    public IngestedDocument normalize(IngestedDocument document) {
        return new IngestedDocument(document.source(), normalize(document.content()), document.metadata());
    }
}
