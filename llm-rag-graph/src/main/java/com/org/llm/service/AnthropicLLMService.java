package com.org.llm.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Wraps the Anthropic Java SDK to send graph-context-enriched prompts to Claude.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnthropicLLMService {

    private static final String SYSTEM_PROMPT = """
            You are an expert knowledge assistant with access to a rich organizational knowledge graph.
            The graph captures a 4-level company hierarchy:
              Company → Department → Team → Employee
            with cross-cutting relationships:
              Employee -[WORKS_ON]→ Project -[USES_TECHNOLOGY]→ Technology
              Employee -[REPORTS_TO]→ Employee  (management chain)
              Department -[COLLABORATES_WITH]→ Department
            
            When answering questions:
            1. Use ONLY the graph context provided — do not hallucinate facts.
            2. Cite specific nodes and relationships from the context.
            3. Explain relationship paths clearly (e.g., "Alice Chen is in the Backend Team, which is part of Engineering").
            4. If the context does not contain enough information, say so explicitly.
            5. Be concise but complete — include relevant structural details.
            """;
    private final AnthropicClient anthropicClient;
    @Value("${app.anthropic.model:claude-opus-4-8}")
    private String model;
    @Value("${app.anthropic.max-tokens:4096}")
    private int maxTokens;

    public String answer(String question, String graphContext) {
        String userMessage = buildUserMessage(question, graphContext);
        log.debug("Sending request to Claude model={} maxTokens={}", model, maxTokens);

        try {
            Message response = anthropicClient.messages().create(
                    MessageCreateParams.builder()
                            .model(model)
                            .maxTokens(maxTokens)
                            .thinking(ThinkingConfigAdaptive.builder().build())
                            .system(SYSTEM_PROMPT)
                            .addUserMessage(userMessage)
                            .build()
            );
            return extractText(response);
        } catch (Exception e) {
            log.error("Anthropic API call failed for question='{}': {}", question, e.getMessage(), e);
            throw new LlmCallException("LLM call to Claude failed: " + e.getMessage(), e);
        }
    }

    private String buildUserMessage(String question, String graphContext) {
        return """
                Here is the relevant knowledge graph context retrieved for your question:
                
                %s
                
                Question: %s
                
                Please answer the question based on the graph context above.
                """.formatted(graphContext, question);
    }

    private String extractText(Message message) {
        return message.content().stream()
                .filter(ContentBlock::isText)
                .map(block -> block.asText().text())
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse("No response generated.");
    }
}
