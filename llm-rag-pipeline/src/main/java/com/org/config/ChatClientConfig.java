package com.org.config;

import com.org.security.SafeGuardProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link ChatClient} backed by the auto-configured OpenAI {@link ChatModel}.
 *
 * <p>Used by the in-pipeline LLM features (LLM-based chunking, metadata enrichment, the
 * {@code llm-pointwise}/{@code llm-listwise} rerankers, the retrieval/chunking-strategy
 * classifiers, the context-sufficiency judge, and manual-mode generation). When
 * {@code app.security.safeguard.enabled=true}, every one of those calls is also gated by Spring
 * AI's {@link SafeGuardAdvisor}.</p>
 */
@Configuration
class ChatClientConfig {

    /**
     * Builds the shared {@link ChatClient} used by LLM-based chunking, enrichment, reranking, the
     * classifiers/judge, and manual-mode generation.
     */
    @Bean
    ChatClient chatClient(ChatModel chatModel, SafeGuardProperties safeGuardProperties) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (safeGuardProperties.isEnabled()) {
            builder.defaultAdvisors(SafeGuardAdvisor.builder()
                    .sensitiveWords(safeGuardProperties.getSensitiveWords())
                    .failureResponse(safeGuardProperties.getFailureResponse())
                    .build());
        }
        return builder.build();
    }
}
