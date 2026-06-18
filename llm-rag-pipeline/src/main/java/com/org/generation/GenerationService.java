package com.org.generation;

import com.org.cache.SemanticCacheService;
import com.org.dto.GenerateRequest;
import com.org.dto.GenerateResponse;
import com.org.eval.GenerationEvaluator;
import com.org.retrieval.model.RetrievalResult;
import com.org.security.PromptInjectionGuard;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
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
 * <p><b>Advisor mode</b> (Section 12 pattern): delegates everything to Spring AI's
 * {@link QuestionAnswerAdvisor}, which handles retrieval, augmentation, and generation internally.
 * Simpler but bypasses the custom filtering and citation chain.</p>
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
    private final ContextBuilder contextBuilder;
    private final ChatClient chatClient;
    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final SemanticCacheService semanticCache;
    private final PromptInjectionGuard injectionGuard;
    private final GenerationEvaluator generationEvaluator;

    private final MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .build();
    private ChatClient advisorClient;

    @PostConstruct
    void init() {
        advisorClient = chatClientBuilder
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(properties.getTopK())
                                        .similarityThreshold(properties.getAdvisor().getSimilarityThreshold())
                                        .build())
                                .build())
                .build();
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
            return new GenerateResponse(cached.get(), List.of(), null, true);
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

        PromptOrchestrator.ChatPrompt safePrompt = new PromptOrchestrator.ChatPrompt(
                chatPrompt.systemInstructions(),
                rebuildContext(safeChunks, retrieval),
                chatPrompt.groundingRules(),
                safeRetrieval);

        String answer = chatClient.prompt()
                .system(safePrompt.systemInstructions())
                .user(safePrompt.toLlmUserMessage(query))
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
        return new GenerateResponse(answer, citations, faithful, false);
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
        PromptOrchestrator.ChatPrompt safePrompt = new PromptOrchestrator.ChatPrompt(
                chatPrompt.systemInstructions(),
                rebuildContext(safeChunks, retrieval),
                chatPrompt.groundingRules(),
                new RetrievalResult(safeChunks, retrieval.citations()));

        return chatClient.prompt()
                .system(safePrompt.systemInstructions())
                .user(safePrompt.toLlmUserMessage(query))
                .advisors(spec -> spec
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    private GenerateResponse generateWithAdvisor(String query) {
        String answer = advisorClient.prompt().user(query).call().content();
        semanticCache.put(query, answer);
        return new GenerateResponse(answer, List.of(), null, false);
    }

    private String rebuildContext(List<com.org.chunking.model.Chunk> chunks,
                                  RetrievalResult original) {
        RetrievalResult toRender = chunks.size() == original.chunks().size()
                ? original : new RetrievalResult(chunks, original.citations());
        return contextBuilder.build(toRender);
    }
}
