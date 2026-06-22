package com.org.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.llm.dto.RagRequest;
import com.org.llm.dto.RagResponse;
import com.org.llm.service.GraphRAGService;
import com.org.llm.web.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {RagController.class, GlobalExceptionHandler.class},
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class RagControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private GraphRAGService ragService;

    @DisplayName("A valid question returns 200 with the LLM-generated answer")
    @Test
    void validQuestionReturns200() throws Exception {
        RagResponse response = RagResponse.builder()
                .question("Who is Alice?")
                .answer("Alice is an engineer.")
                .graphContext("context")
                .relevantEntities(List.of("Alice"))
                .citations(List.of())
                .processingTimeMs(100L)
                .timestamp(OffsetDateTime.now())
                .build();
        when(ragService.query(any(RagRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RagRequest("Who is Alice?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Alice is an engineer."));
    }

    @DisplayName("A blank question returns 400")
    @Test
    void blankQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("A null question returns 400")
    @Test
    void nullQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":null}"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("An exception from the RAG service returns 500")
    @Test
    void llmServiceThrowingReturns500() throws Exception {
        when(ragService.query(any(RagRequest.class))).thenThrow(new RuntimeException("LLM unavailable"));

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RagRequest("Who is Bob?"))))
                .andExpect(status().isInternalServerError());
    }
}
