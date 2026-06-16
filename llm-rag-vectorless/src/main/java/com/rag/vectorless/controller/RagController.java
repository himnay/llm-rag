package com.rag.vectorless.controller;

import com.rag.vectorless.dto.ChatRequest;
import com.rag.vectorless.dto.ChatResponse;
import com.rag.vectorless.dto.Chunk;
import com.rag.vectorless.rag.BM25Retriever;
import com.rag.vectorless.rag.DocumentLoader;
import com.rag.vectorless.rag.PageIndexClient;
import com.rag.vectorless.rag.PageIndexDocumentManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rag")
public class RagController {

    private final ChatClient chatClient;
    private final BM25Retriever bm25Retriever;
    private final DocumentLoader documentLoader;
    private final Optional<PageIndexClient> pageIndexClient;
    private final Optional<PageIndexDocumentManager> pageIndexManager;

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        List<Document> docs = bm25Retriever.retrieve(request.question());

        if (docs.isEmpty()) {
            return new ChatResponse("I don't have information about that in my knowledge base.", List.of());
        }

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        List<String> sources = docs.stream()
                .map(d -> (String) d.getMetadata().get("source"))
                .filter(s -> s != null)
                .distinct().sorted()
                .toList();

        return generate(context, sources, request.question());
    }

    @PostMapping("/chat-pageindex")
    public ChatResponse chatPageIndex(@Valid @RequestBody ChatRequest request) {
        if (pageIndexClient.isEmpty() || pageIndexManager.isEmpty()) {
            return new ChatResponse(
                    "PageIndex is not enabled. Set RAG_PAGEINDEX_ENABLED=true and PAGEINDEX_API_KEY.",
                    List.of()
            );
        }

        Map<String, String> docIds = pageIndexManager.get().getDocIds();
        if (docIds.isEmpty()) {
            return new ChatResponse("No documents have been indexed in PageIndex yet.", List.of());
        }

        List<String> allContent = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        for (Map.Entry<String, String> entry : docIds.entrySet()) {
            try {
                List<String> content = pageIndexClient.get().retrieve(entry.getValue(), request.question());
                if (!content.isEmpty()) {
                    allContent.addAll(content);
                    sources.add(entry.getKey());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ChatResponse("Retrieval interrupted.", List.of());
            }
        }

        if (allContent.isEmpty()) {
            return new ChatResponse("I don't have information about that in my knowledge base.", List.of());
        }

        String context = String.join("\n\n---\n\n", allContent);
        sources = sources.stream().distinct().sorted().toList();
        return generate(context, sources, request.question());
    }

    @GetMapping("/documents")
    public Map<String, Object> documents() {
        List<Chunk> chunks = documentLoader.getChunks();
        List<String> sources = chunks.stream()
                .map(Chunk::source)
                .distinct().sorted()
                .toList();
        return Map.of("documents", sources, "totalChunks", chunks.size());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        int chunks = documentLoader.getChunks().size();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", chunks > 0 ? "ok" : "no documents loaded");
        result.put("chunks", chunks);
        result.put("pageindex", pageIndexManager.isPresent() ? "enabled" : "disabled");
        pageIndexManager.ifPresent(m -> result.put("pageindexDocs", m.getDocIds().size()));
        return result;
    }

    // ── shared prompt + Claude call ──────────────────────────────────────────

    private ChatResponse generate(String context, List<String> sources, String question) {
        String userMessage = """
                Use only the context below to answer the question.
                If the context does not contain the answer, say "I don't have information about that."
                
                Context:
                %s
                
                Question: %s
                """.formatted(context, question);

        String answer = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        return new ChatResponse(answer, sources);
    }
}
