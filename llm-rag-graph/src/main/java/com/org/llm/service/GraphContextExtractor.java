package com.org.llm.service;

import com.org.llm.repository.DepartmentRepository;
import com.org.llm.repository.EmployeeRepository;
import com.org.llm.repository.ProjectRepository;
import com.org.llm.repository.TechnologyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts rich multi-level graph context from Neo4j for a natural-language question.
 * <p>
 * Strategy pattern: the extraction pipeline delegates to a list of {@link GraphExtractionStrategy}
 * implementations (currently {@link PathTraversalStrategy}), making it easy to add new traversal
 * approaches (semantic search, embedding-based, etc.) without modifying this class.
 */
@Slf4j
@Service
public class GraphContextExtractor {

    private final Neo4jClient neo4jClient;
    private final EmployeeRepository employeeRepo;
    private final DepartmentRepository deptRepo;
    private final ProjectRepository projectRepo;
    private final TechnologyRepository techRepo;
    private final List<GraphExtractionStrategy> strategies;
    @Value("${app.graph.max-context-nodes:20}")
    private int maxContextNodes;
    @Value("${app.graph.context-depth:4}")
    private int contextDepth;

    public GraphContextExtractor(Neo4jClient neo4jClient,
                                 EmployeeRepository employeeRepo,
                                 DepartmentRepository deptRepo,
                                 ProjectRepository projectRepo,
                                 TechnologyRepository techRepo,
                                 List<GraphExtractionStrategy> strategies) {
        this.neo4jClient = neo4jClient;
        this.employeeRepo = employeeRepo;
        this.deptRepo = deptRepo;
        this.projectRepo = projectRepo;
        this.techRepo = techRepo;
        this.strategies = strategies;
    }

    @Transactional(readOnly = true)
    public GraphContext extract(String question) {
        log.debug("Extracting graph context for question: {}", question);

        List<String> keywords = extractKeywords(question);
        Set<String> entityNames = new LinkedHashSet<>();
        List<String> contextLines = new ArrayList<>();

        // ── 1. Full-text search across all node labels ──────────────────
        for (String keyword : keywords) {
            List<Map<String, Object>> hits = fullTextSearch(keyword);
            for (Map<String, Object> hit : hits) {
                String name = (String) hit.get("name");
                String labels = (String) hit.get("labels");
                if (name != null) entityNames.add(name);
                if (name != null && labels != null) {
                    contextLines.add("Found entity: " + name + " [" + labels + "]");
                }
            }
            if (entityNames.size() >= maxContextNodes) break;
        }

        // ── 2. Employee deep-context traversal ───────────────────────────
        for (String keyword : keywords) {
            employeeRepo.searchByKeyword(keyword).forEach(emp -> {
                entityNames.add(emp.getName());

                StringBuilder sb = new StringBuilder();
                sb.append("Employee ").append(emp.getName())
                        .append(" (").append(emp.getTitle()).append(")");

                if (emp.getManager() != null) {
                    sb.append(" reports to ").append(emp.getManager().getName());
                }
                if (emp.getSkills() != null && !emp.getSkills().isEmpty()) {
                    sb.append(". Skills: ").append(String.join(", ", emp.getSkills()));
                }
                if (emp.getProjectAssignments() != null) {
                    emp.getProjectAssignments().forEach(wa -> {
                        if (wa.getProject() != null) {
                            sb.append(". Works on ").append(wa.getProject().getName())
                                    .append(" as ").append(wa.getRole())
                                    .append(" (").append(wa.getAllocationPct()).append("% allocation)");
                            entityNames.add(wa.getProject().getName());
                        }
                    });
                }
                contextLines.add(sb.toString());
            });
        }

        // ── 3. Department-level traversal ────────────────────────────────
        for (String keyword : keywords) {
            deptRepo.searchByKeyword(keyword).forEach(dept -> {
                entityNames.add(dept.getName());
                StringBuilder sb = new StringBuilder();
                sb.append("Department ").append(dept.getName())
                        .append(" (").append(dept.getHeadcount()).append(" headcount)");

                if (dept.getTeams() != null && !dept.getTeams().isEmpty()) {
                    String teams = dept.getTeams().stream()
                            .map(t -> t.getName())
                            .collect(Collectors.joining(", "));
                    sb.append(". Teams: ").append(teams);
                }
                if (dept.getCollaborators() != null && !dept.getCollaborators().isEmpty()) {
                    String collabs = dept.getCollaborators().stream()
                            .map(d -> d.getName())
                            .collect(Collectors.joining(", "));
                    sb.append(". Collaborates with: ").append(collabs);
                }
                if (dept.getProjects() != null && !dept.getProjects().isEmpty()) {
                    String projects = dept.getProjects().stream()
                            .map(p -> p.getName())
                            .collect(Collectors.joining(", "));
                    sb.append(". Owns projects: ").append(projects);
                }
                contextLines.add(sb.toString());
            });
        }

        // ── 4. Project + Technology traversal ────────────────────────────
        for (String keyword : keywords) {
            projectRepo.searchByKeyword(keyword).forEach(proj -> {
                entityNames.add(proj.getName());
                StringBuilder sb = new StringBuilder();
                sb.append("Project ").append(proj.getName())
                        .append(" (status: ").append(proj.getStatus()).append(")");

                if (proj.getTechnologies() != null && !proj.getTechnologies().isEmpty()) {
                    String techs = proj.getTechnologies().stream()
                            .map(t -> t.getName())
                            .collect(Collectors.joining(", "));
                    sb.append(". Uses technologies: ").append(techs);
                }
                contextLines.add(sb.toString());
            });

            techRepo.searchByKeyword(keyword).forEach(tech -> {
                entityNames.add(tech.getName());
                contextLines.add("Technology " + tech.getName()
                        + " (" + tech.getCategory() + "): " + tech.getDescription());
            });
        }

        // ── 5. Pluggable extraction strategies (Strategy pattern) ────────
        for (GraphExtractionStrategy strategy : strategies) {
            contextLines.addAll(strategy.extract(keywords));
        }

        // ── 6. Deduplicate and cap ────────────────────────────────────────
        List<String> dedupedLines = contextLines.stream()
                .distinct()
                .limit(maxContextNodes * 3L)
                .collect(Collectors.toList());

        String formatted = formatContext(dedupedLines);
        log.debug("Extracted {} context lines for {} entity matches", dedupedLines.size(), entityNames.size());

        return new GraphContext(formatted, new ArrayList<>(entityNames));
    }

    // ── Full-text search via Neo4j index ─────────────────────────────────

    private List<Map<String, Object>> fullTextSearch(String keyword) {
        try {
            String cypher = """
                    CALL db.index.fulltext.queryNodes('entitySearch', $keyword + '*')
                    YIELD node, score
                    WITH node, score, labels(node) AS lbls
                    RETURN node.name AS name, reduce(s='', l IN lbls | s + l + ' ') AS labels, score
                    ORDER BY score DESC LIMIT 10
                    """;
            return neo4jClient.query(cypher)
                    .bind(keyword).to("keyword")
                    .fetch()
                    .all()
                    .stream()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Full-text search failed for '{}': {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private List<String> extractKeywords(String question) {
        Set<String> stopWords = Set.of(
                "who", "what", "where", "when", "how", "which", "why",
                "is", "are", "was", "were", "the", "a", "an", "in", "on",
                "at", "to", "for", "of", "and", "or", "with", "by", "from",
                "about", "does", "do", "can", "has", "have", "tell", "me",
                "list", "show", "find", "give", "all", "any", "their", "our");

        return Arrays.stream(question.toLowerCase().replaceAll("[^a-z0-9 ]", " ").split("\\s+"))
                .filter(w -> w.length() > 2 && !stopWords.contains(w))
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
    }

    private String formatContext(List<String> lines) {
        if (lines.isEmpty()) {
            return "No relevant graph context found.";
        }
        return "=== Graph Knowledge Context ===\n" +
                lines.stream()
                        .map(l -> "• " + l)
                        .collect(Collectors.joining("\n")) +
                "\n=== End of Context ===";
    }

    // ── Result record ─────────────────────────────────────────────────────

    public record GraphContext(String formattedContext, List<String> entityNames) {
    }
}
