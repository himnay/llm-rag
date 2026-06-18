package com.rag.vectorless.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.vectorless.config.RagProperties;
import com.rag.vectorless.dto.ChatRequest;
import com.rag.vectorless.eval.GenerationEvaluator;
import com.rag.vectorless.rag.BM25Retriever;
import com.rag.vectorless.rag.DocumentLoader;
import com.rag.vectorless.rag.PageIndexClient;
import com.rag.vectorless.rag.PageIndexDocumentManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({RagController.class, GlobalExceptionHandler.class})
class RagControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private BM25Retriever bm25Retriever;

    @MockitoBean
    private DocumentLoader documentLoader;

    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private Optional<PageIndexClient> pageIndexClient;

    @MockitoBean
    private Optional<PageIndexDocumentManager> pageIndexManager;

    @MockitoBean
    private GenerationEvaluator generationEvaluator;

    @MockitoBean
    private RagProperties ragProperties;

    @Test
    @DisplayName("Returns 200 with an answer for a valid question")
    void validQuestionReturns200() throws Exception {
        Document doc = new Document("BM25 is a ranking algorithm.", Map.of("source", "ir.txt"));
        when(bm25Retriever.retrieve(anyString())).thenReturn(List.of(doc));

        ChatClient.ChatClientRequestSpec requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("BM25 is a ranking function used in IR.");

        mockMvc.perform(post("/api/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("What is BM25?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists());
    }

    @Test
    @DisplayName("Returns 400 when the question is blank")
    void blankQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Returns 400 when the question is null")
    void nullQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Returns 200 with a not-found message when no documents are retrieved")
    void noDocumentsReturnsNotFoundMessage() throws Exception {
        when(bm25Retriever.retrieve(anyString())).thenReturn(List.of());

        mockMvc.perform(post("/api/rag/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("What is quantum physics?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsString("don't have information")));
    }
}
