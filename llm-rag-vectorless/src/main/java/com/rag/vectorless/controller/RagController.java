package com.rag.vectorless.controller;

import com.rag.vectorless.config.RagProperties;
import com.rag.vectorless.dto.ChatRequest;
import com.rag.vectorless.dto.ChatResponse;
import com.rag.vectorless.dto.Chunk;
import com.rag.vectorless.dto.Citation;
import com.rag.vectorless.eval.GenerationEvaluator;
import com.rag.vectorless.rag.BM25Retriever;
import com.rag.vectorless.rag.DocumentLoader;
import com.rag.vectorless.rag.PageIndexClient;
import com.rag.vectorless.rag.PageIndexDocumentManager;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Vector-less RAG", description = "BM25-based retrieval-augmented generation without vector stores")
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final ChatClient chatClient;
    private final BM25Retriever bm25Retriever;
    private final DocumentLoader documentLoader;
    private final Optional<PageIndexClient> pageIndexClient;
    private final Optional<PageIndexDocumentManager> pageIndexManager;
    private final GenerationEvaluator generationEvaluator;
    private final RagProperties ragProperties;

    @Operation(summary = "Answer a question using BM25 retrieval over local documents")
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        List<Document> docs = bm25Retriever.retrieve(request.getQuestion());

        if (docs.isEmpty()) {
            return ChatResponse.builder().answer("I don't have information about that in my knowledge base.").citations(List.of()).faithful(null).build();
        }

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        List<Citation> citations = toCitations(docs);

        return generate(context, docs, citations, request.getQuestion());
    }

    @Operation(summary = "Answer a question using PageIndex cloud retrieval")
    @PostMapping("/chat-pageindex")
    public ChatResponse chatPageIndex(@Valid @RequestBody ChatRequest request) {
        if (pageIndexClient.isEmpty() || pageIndexManager.isEmpty()) {
            return ChatResponse.builder()
                    .answer("PageIndex is not enabled. Set RAG_PAGEINDEX_ENABLED=true and PAGEINDEX_API_KEY.")
                    .citations(List.of()).faithful(null).build();
        }

        Map<String, String> docIds = pageIndexManager.get().getDocIds();
        if (docIds.isEmpty()) {
            return ChatResponse.builder().answer("No documents have been indexed in PageIndex yet.").citations(List.of()).faithful(null).build();
        }

        List<String> allContent = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        for (Map.Entry<String, String> entry : docIds.entrySet()) {
            try {
                List<String> content = pageIndexClient.get().retrieve(entry.getValue(), request.getQuestion());
                if (!content.isEmpty()) {
                    allContent.addAll(content);
                    sources.add(entry.getKey());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ChatResponse.builder().answer("Retrieval interrupted.").citations(List.of()).faithful(null).build();
            }
        }

        if (allContent.isEmpty()) {
            return ChatResponse.builder().answer("I don't have information about that in my knowledge base.").citations(List.of()).faithful(null).build();
        }

        String context = String.join("\n\n---\n\n", allContent);
        // PageIndex doesn't expose a per-chunk score/index — filename-only citations.
        List<Citation> citations = sources.stream()
                .distinct().sorted()
                .map(source -> Citation.builder().source(source).chunkIndex(null).score(null).build())
                .toList();
        List<Document> contextDocs = allContent.stream().map(Document::new).toList();
        return generate(context, contextDocs, citations, request.getQuestion());
    }

    @Operation(summary = "List all indexed documents and total chunk count")
    @GetMapping("/documents")
    public Map<String, Object> documents() {
        List<Chunk> chunks = documentLoader.getChunks();
        List<String> sources = chunks.stream()
                .map(Chunk::getSource)
                .distinct().sorted()
                .toList();
        return Map.of("documents", sources, "totalChunks", chunks.size());
    }

    @Operation(summary = "Check the health of the RAG service and document index")
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

    @CircuitBreaker(name = "llm-vectorless", fallbackMethod = "generateFallback")
    ChatResponse generate(String context, List<Document> contextDocs, List<Citation> citations, String question) {
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

        Boolean faithful = null;
        if (ragProperties.evaluateFaithfulness()) {
            faithful = generationEvaluator.isFaithful(question, contextDocs, answer);
        }

        return ChatResponse.builder().answer(answer).citations(citations).faithful(faithful).build();
    }

    @SuppressWarnings("unused")
    ChatResponse generateFallback(String context, List<Document> contextDocs, List<Citation> citations, String question, Throwable t) {
        log.warn("LLM circuit breaker triggered for question='{}': {}", question, t.getMessage());
        return ChatResponse.builder().answer("Service temporarily unavailable. Please try again in a moment.").citations(List.of()).faithful(null).build();
    }

    private List<Citation> toCitations(List<Document> docs) {
        Map<String, Citation> bySourceAndChunk = new LinkedHashMap<>();
        for (Document d : docs) {
            String source = (String) d.getMetadata().get("source");
            Integer chunkIndex = (Integer) d.getMetadata().get("chunkIndex");
            Double score = (Double) d.getMetadata().get("score");
            if (source == null) continue;
            bySourceAndChunk.putIfAbsent(source + "#" + chunkIndex, Citation.builder().source(source).chunkIndex(chunkIndex).score(score).build());
        }
        return List.copyOf(bySourceAndChunk.values());
    }
}
