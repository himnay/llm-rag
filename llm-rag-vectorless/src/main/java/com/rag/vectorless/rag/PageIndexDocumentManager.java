package com.rag.vectorless.rag;

import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@ConditionalOnProperty(name = "rag.pageindex.enabled", havingValue = "true")
public class PageIndexDocumentManager {

    private final PageIndexClient client;

    // filename → doc_id, populated at startup
    private final Map<String, String> docIds = new LinkedHashMap<>();

    public PageIndexDocumentManager(PageIndexClient client) {
        this.client = client;
    }

    @PostConstruct
    public void uploadAll() throws IOException, InterruptedException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:documents/*.txt");

        // Upload all documents first (non-blocking on PageIndex side)
        Map<String, String> pending = new LinkedHashMap<>();
        for (Resource r : resources) {
            String filename = r.getFilename();
            String text = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            byte[] pdf = textToPdf(filename, text);
            String docId = client.uploadPdf(filename.replace(".txt", ""), pdf);
            pending.put(filename, docId);
            log.info("Uploaded '{}' to PageIndex as {}", filename, docId);
        }

        // Now wait for all to finish processing
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            client.waitUntilReady(entry.getValue());
            docIds.put(entry.getKey(), entry.getValue());
            log.info("PageIndex ready: {} → {}", entry.getKey(), entry.getValue());
        }
    }

    /**
     * Returns a read-only map of filename → doc_id for all indexed documents.
     */
    public Map<String, String> getDocIds() {
        return Collections.unmodifiableMap(docIds);
    }

    // ── PDF generation ──────────────────────────────────────────────────────

    private byte[] textToPdf(String name, String text) throws IOException {
        // Strip non-Latin1 chars (PDType1Font/Helvetica only handles Latin-1)
        String safe = text.replaceAll("[^\\x20-\\x7E\\n\\r\\t]", "?");
        List<String> lines = wrapLines(safe, 90);

        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float fontSize = 10f;
            float leading = 13f;
            float margin = 50f;
            float maxY = PDRectangle.A4.getHeight() - margin;
            float minY = margin;

            float y = maxY;
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            cs.setFont(font, fontSize);
            cs.setLeading(leading);
            cs.beginText();
            cs.newLineAtOffset(margin, y);

            for (String line : lines) {
                if (y - leading < minY) {
                    cs.endText();
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    cs.setFont(font, fontSize);
                    cs.setLeading(leading);
                    cs.beginText();
                    y = maxY;
                    cs.newLineAtOffset(margin, y);
                }
                cs.showText(line.isEmpty() ? " " : line);
                cs.newLine();
                y -= leading;
            }

            cs.endText();
            cs.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private List<String> wrapLines(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            if (raw.length() <= maxChars) {
                result.add(raw);
                continue;
            }
            String remaining = raw;
            while (remaining.length() > maxChars) {
                int cut = remaining.lastIndexOf(' ', maxChars);
                if (cut <= 0) cut = maxChars;
                result.add(remaining.substring(0, cut));
                remaining = remaining.substring(cut).stripLeading();
            }
            if (!remaining.isEmpty()) result.add(remaining);
        }
        return result;
    }
}
