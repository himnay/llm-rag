package com.org.generation;

import com.org.cache.SemanticCacheService;
import com.org.dto.GenerateRequest;
import com.org.dto.GenerateResponse;
import com.org.eval.ContextSufficiencyJudge;
import com.org.eval.GenerationEvaluator;
import com.org.retrieval.model.Citation;
import com.org.retrieval.model.RetrievalResult;
import com.org.security.PromptInjectionGuard;
import com.org.security.SafeGuardProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the full RAG generation pipeline (retrieve → guard → augment → generate).
 *
 * <p><b>Manual mode</b> (default, Section 7 pattern): {@link PromptOrchestrator} retrieves and
 * assembles the prompt; {@link PromptInjectionGuard} filters retrieved chunks; the LLM is called
 * directly via {@link ChatClient}. Supports citation tracking, semantic caching, and optional
 * faithfulness evaluation.</p>
 *
 * <p><b>Advisor mode</b> (Section 12 pattern): delegates retrieval, augmentation, and generation to
 * Spring AI's modular RAG advisor pipeline ({@link RetrievalAugmentationAdvisor} +
 * {@link VectorStoreDocumentRetriever}). Simpler than manual mode — bypasses the custom
 * post-processing chain — but does track real citations from the documents the advisor
 * retrieved.</p>
 *
 * <p>Enable this service via {@code app.generation.enabled=true}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.generation.enabled", havingValue = "true")
public class GenerationService {

    private final GenerationProperties properties;
    private final PromptOrchestrator promptOrchestrator;
    private final ChatClient chatClient;
    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final SemanticCacheService semanticCache;
    private final PromptInjectionGuard injectionGuard;
    private final GenerationEvaluator generationEvaluator;
    private final ContextSufficiencyJudge sufficiencyJudge;
    private final SafeGuardProperties safeGuardProperties;
    private final MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository())
                        .build();

    private ChatClient advisorClient;

    @PostConstruct
    void init() {
        chatClientBuilder.defaultAdvisors(
                RetrievalAugmentationAdvisor.builder()
                        .documentRetriever(VectorStoreDocumentRetriever.builder()
                                .vectorStore(vectorStore)
                                .topK(properties.getTopK())
                                .similarityThreshold(properties.getAdvisor().getSimilarityThreshold())
                                .build())
                        .build());
        if (safeGuardProperties.isEnabled()) {
            chatClientBuilder.defaultAdvisors(SafeGuardAdvisor.builder()
                    .sensitiveWords(safeGuardProperties.getSensitiveWords())
                    .failureResponse(safeGuardProperties.getFailureResponse())
                    .build());
        }
        advisorClient = chatClientBuilder.build();
    }

    /**
     * Answers the request, serving from the semantic cache on a hit, otherwise running either the
     * manual or advisor pipeline depending on {@code app.generation.mode}.
     */
    public GenerateResponse generate(GenerateRequest request) {
        String query = request.query();
        int topK = request.topK() != null ? request.topK() : properties.getTopK();
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId() : UUID.randomUUID().toString();

        Optional<String> cached = semanticCache.get(query);
        if (cached.isPresent()) {
            log.info("SemanticCache hit for query='{}'", query);
            return new GenerateResponse(cached.get(), List.of(), null, true, false);
        }

        return "advisor".equalsIgnoreCase(properties.getMode())
                ? generateWithAdvisor(query)
                : generateManual(query, topK, conversationId);
    }

    private GenerateResponse generateManual(String query, int topK, String conversationId) {
        PromptOrchestrator.ChatPrompt chatPrompt = promptOrchestrator.build(query, topK);
        RetrievalResult retrieval = chatPrompt.retrievalResult();

        List<com.org.chunking.model.Chunk> safeChunks = injectionGuard.filter(retrieval.chunks());
        RetrievalResult safeRetrieval = new RetrievalResult(safeChunks, retrieval.citations());
        PromptOrchestrator.ChatPrompt safePrompt = promptOrchestrator.rebuild(query, safeRetrieval);

        if (properties.getJudge().isEnabled() && !sufficiencyJudge.isSufficient(query, safePrompt.context())) {
            log.info("Context sufficiency judge: INSUFFICIENT — skipping generation | query='{}'", query);
            return new GenerateResponse(properties.getJudge().getInsufficientAnswer(), List.of(), null, false, true);
        }

        String answer = chatClient.prompt()
                .system(safePrompt.systemInstructions())
                .user(safePrompt.userMessage())
                .advisors(spec -> spec
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        log.info("LLM response generated | query='{}' | chunks={} | answerLength={}",
                query, safeChunks.size(), answer != null ? answer.length() : 0);

        Boolean faithful = null;
        if (properties.isEvaluateFaithfulness() && answer != null) {
            List<Document> contextDocs = safeChunks.stream()
                    .map(c -> new Document(c.content(), c.metadata()))
                    .toList();
            faithful = generationEvaluator.isFaithful(query, contextDocs, answer);
            log.info("Faithfulness check: {} | query='{}'", faithful, query);
        }

        semanticCache.put(query, answer);
        List<com.org.retrieval.model.Citation> citations = properties.isIncludeCitations()
                ? safeRetrieval.citations() : List.of();
        return new GenerateResponse(answer, citations, faithful, false, false);
    }

    /**
     * Streaming generation via SSE. Returns a token-by-token {@link reactor.core.publisher.Flux}
     * so the HTTP connection is not held until the full answer is ready.
     */
    public reactor.core.publisher.Flux<String> stream(GenerateRequest request) {
        String query = request.query();
        int topK = request.topK() != null ? request.topK() : properties.getTopK();
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId() : UUID.randomUUID().toString();

        PromptOrchestrator.ChatPrompt chatPrompt = promptOrchestrator.build(query, topK);
        RetrievalResult retrieval = chatPrompt.retrievalResult();
        List<com.org.chunking.model.Chunk> safeChunks = injectionGuard.filter(retrieval.chunks());
        RetrievalResult safeRetrieval = new RetrievalResult(safeChunks, retrieval.citations());
        PromptOrchestrator.ChatPrompt safePrompt = promptOrchestrator.rebuild(query, safeRetrieval);

        return chatClient.prompt()
                .system(safePrompt.systemInstructions())
                .user(safePrompt.userMessage())
                .advisors(spec -> spec
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    private GenerateResponse generateWithAdvisor(String query) {
        ChatClientResponse response = advisorClient.prompt().user(query).call().chatClientResponse();
        String answer = response.chatResponse() != null
                ? response.chatResponse().getResult().getOutput().getText() : null;
        List<Citation> citations = toCitations(response.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT));
        semanticCache.put(query, answer);
        return new GenerateResponse(answer, citations, null, false, false);
    }

    /**
     * Builds citations from the documents {@link RetrievalAugmentationAdvisor} retrieved (exposed
     * via its {@code rag_document_context} response-context entry) — the same field extraction
     * {@code RetrievalService.toCitations} does for manual mode, adapted for {@link Document}.
     */
    @SuppressWarnings("unchecked")
    private List<Citation> toCitations(Object documentContext) {
        if (!(documentContext instanceof List<?> rawDocuments)) {
            return List.of();
        }
        LinkedHashMap<String, Citation> seen = new LinkedHashMap<>();
        for (Document doc : (List<Document>) rawDocuments) {
            Map<String, Object> m = doc.getMetadata();
            String identity = Objects.toString(m.get("identity"), "");
            Object pageRaw = m.getOrDefault("page_number", m.get("page"));
            Integer page = pageRaw instanceof Number n ? n.intValue() : null;
            int chunkIndex = m.get("chunkIndex") instanceof Number n ? n.intValue() : 0;
            String key = identity + "#" + page + "#" + chunkIndex;
            seen.putIfAbsent(key, new Citation(
                    Objects.toString(m.get("source"), ""), Objects.toString(m.get("fileName"), ""),
                    identity.isEmpty() ? null : identity, page, chunkIndex, doc.getScore()));
        }
        return new ArrayList<>(seen.values());
    }
}
