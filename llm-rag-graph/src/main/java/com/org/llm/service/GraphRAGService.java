package com.org.llm.service;

import com.org.llm.dto.GraphStats;
import com.org.llm.dto.RagRequest;
import com.org.llm.dto.RagResponse;
import com.org.llm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Orchestrates the Graph RAG pipeline:
 * question → GraphContextExtractor → AnthropicLLMService → RagResponse
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphRAGService {

    private final GraphContextExtractor graphContextExtractor;
    private final AnthropicLLMService llmService;
    private final CompanyRepository companyRepo;
    private final DepartmentRepository deptRepo;
    private final TeamRepository teamRepo;
    private final EmployeeRepository employeeRepo;
    private final ProjectRepository projectRepo;
    private final TechnologyRepository techRepo;
    private final Neo4jClient neo4jClient;
    @Value("${app.rag.evaluate-groundedness:false}")
    private boolean evaluateGroundedness;

    /**
     * Runs the Graph RAG pipeline for a question: extract graph context, ask the LLM, optionally
     * verify groundedness, and assemble the timed {@link RagResponse}.
     */
    public RagResponse query(RagRequest request) {
        long start = System.currentTimeMillis();
        log.info("RAG query: {}", request.getQuestion());

        // Step 1: Retrieve graph context
        GraphContextExtractor.GraphContext ctx = graphContextExtractor.extract(request.getQuestion());
        log.debug("Graph context retrieved: {} entities", ctx.entityNames().size());

        // Step 2: Call LLM with enriched context
        String answer = llmService.answer(request.getQuestion(), ctx.formattedContext());

        // Step 3 (optional, off by default): groundedness/faithfulness check — costs one extra
        // LLM call per request, so it is gated behind app.rag.evaluate-groundedness.
        Boolean groundedness = null;
        if (evaluateGroundedness) {
            groundedness = llmService.checkGroundedness(ctx.formattedContext(), answer);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("RAG query completed in {}ms", elapsed);

        return new RagResponse()
                .question(request.getQuestion())
                .answer(answer)
                .graphContext(ctx.formattedContext())
                .relevantEntities(ctx.entityNames())
                .citations(ctx.citations())
                .groundedness(groundedness)
                .processingTimeMs(elapsed)
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Returns aggregate node and relationship counts across the whole graph.
     */
    public GraphStats getStats() {
        long companies = companyRepo.count();
        long departments = deptRepo.count();
        long teams = teamRepo.count();
        long employees = employeeRepo.count();
        long projects = projectRepo.count();
        long technologies = techRepo.count();
        long totalNodes = companies + departments + teams + employees + projects + technologies;
        long totalRels = countRelationships();

        return new GraphStats(companies, departments, teams, employees,
                projects, technologies, totalNodes, totalRels);
    }

    private long countRelationships() {
        try {
            return neo4jClient.query("MATCH ()-[r]->() RETURN count(r) AS cnt")
                    .fetchAs(Long.class)
                    .mappedBy((ts, rec) -> rec.get("cnt").asLong())
                    .one()
                    .orElse(0L);
        } catch (Exception e) {
            log.warn("Could not count relationships: {}", e.getMessage());
            return 0L;
        }
    }
}
