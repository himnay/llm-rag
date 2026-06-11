package com.rag.vectorless.rag;

import com.rag.vectorless.config.RagProperties;
import com.rag.vectorless.dto.Chunk;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentLoader {

    private final RagProperties ragProperties;
    private final List<Chunk> chunks = new ArrayList<>();

    @PostConstruct
    public void load() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:documents/*.txt");

        for (Resource resource : resources) {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String filename = resource.getFilename();
            List<String> split = splitIntoChunks(content.strip());

            for (int i = 0; i < split.size(); i++) {
                chunks.add(new Chunk(split.get(i), filename, i));
            }
        }

        log.info("Loaded {} chunks from {} document(s)", chunks.size(), resources.length);
    }

    private List<String> splitIntoChunks(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int chunkSize = ragProperties.chunkSize();
        int chunkOverlap = ragProperties.chunkOverlap();

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('.', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int boundary = Math.max(lastPeriod, lastNewline);
                if (boundary > start + chunkSize / 2) {
                    end = boundary + 1;
                }
            }

            String chunk = text.substring(start, end).strip();
            if (!chunk.isBlank()) {
                result.add(chunk);
            }

            int next = end - chunkOverlap;
            if (next <= start) break;
            start = next;
        }

        return result;
    }

    public List<Chunk> getChunks() {
        return Collections.unmodifiableList(chunks);
    }
}
