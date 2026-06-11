package com.org.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link ChatClient} backed by the auto-configured OpenAI {@link ChatModel}.
 *
 * <p>Used by the in-pipeline LLM features (LLM-based chunking and metadata enrichment). This is the
 * only direct LLM usage in the service and is invoked only when those features are enabled.</p>
 */
@Configuration
class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
