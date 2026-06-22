package com.rag.vectorless.rag;

import com.rag.vectorless.exception.PageIndexProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Component
@ConditionalOnProperty(name = "rag.pageindex.enabled", havingValue = "true")
public class PageIndexClient {

    private static final String BASE_URL = "https://api.pageindex.ai";

    private final String apiKey;
    private final RestClient http;

    public PageIndexClient(@Value("${rag.pageindex.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.http = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("api_key", apiKey)
                .build();
    }

    /**
     * Upload a PDF and return the doc_id immediately (processing is async).
     */
    public String uploadPdf(String name, byte[] pdfBytes) {
        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return name + ".pdf";
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, fileHeaders));

        Map<?, ?> response = http.post()
                .uri("/doc/")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Map.class);

        return (String) response.get("doc_id");
    }

    /**
     * Block until the document is ready for retrieval (up to ~3 minutes).
     */
    public void waitUntilReady(String docId) throws InterruptedException {
        for (int i = 0; i < 36; i++) {
            Thread.sleep(5_000);
            Map<?, ?> status = http.get()
                    .uri("/doc/{id}/", docId)
                    .retrieve()
                    .body(Map.class);
            String s = (String) status.get("status");
            log.debug("doc {} status: {}", docId, s);
            if ("completed".equals(s)) return;
            if ("failed".equals(s)) throw new PageIndexProcessingException("PageIndex processing failed for " + docId);
        }
        throw new PageIndexProcessingException("Timeout waiting for PageIndex doc " + docId);
    }

    /**
     * Run vectorless retrieval against one document.
     * Returns the list of relevant text excerpts extracted by PageIndex's tree-reasoning.
     */
    @SuppressWarnings("unchecked")
    public List<String> retrieve(String docId, String query) throws InterruptedException {
        Map<String, Object> body = Map.of("doc_id", docId, "query", query);
        Map<?, ?> init = http.post()
                .uri("/retrieval/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        String retrievalId = (String) init.get("retrieval_id");

        for (int i = 0; i < 30; i++) {
            Thread.sleep(2_000);
            Map<?, ?> result = http.get()
                    .uri("/retrieval/{id}/", retrievalId)
                    .retrieve()
                    .body(Map.class);

            String status = (String) result.get("status");
            if ("completed".equals(status)) {
                List<Map<?, ?>> nodes = (List<Map<?, ?>>) result.get("retrieved_nodes");
                if (nodes == null) return List.of();
                return nodes.stream()
                        .flatMap(node -> {
                            List<Map<?, ?>> contents = (List<Map<?, ?>>) node.get("relevant_contents");
                            return contents == null ? Stream.empty() : contents.stream()
                                    .map(c -> (String) c.get("relevant_content"))
                                    .filter(s -> s != null && !s.isBlank());
                        })
                        .toList();
            }
            if ("failed".equals(status)) {
                log.warn("Retrieval {} failed for docId {}", retrievalId, docId);
                return List.of();
            }
        }
        log.warn("Retrieval timeout for docId {}", docId);
        return List.of();
    }
}
