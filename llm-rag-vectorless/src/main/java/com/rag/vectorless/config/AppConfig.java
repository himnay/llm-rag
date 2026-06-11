package com.rag.vectorless.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        You are a helpful assistant. Answer questions based ONLY on the provided context.
                        If the answer is not in the context, say "I don't have information about that in my knowledge base."
                        Be concise and accurate. Do not add information beyond what the context provides.
                        """)
                .build();
    }
}
