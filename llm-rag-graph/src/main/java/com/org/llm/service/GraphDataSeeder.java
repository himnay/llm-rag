package com.org.llm.service;

import com.org.llm.config.Neo4jConfig;
import com.org.llm.domain.*;
import com.org.llm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the Neo4j database with a 4-level TechCorp knowledge graph on first startup.
 * <p>
 * Hierarchy:
 * Company (L0)
 * └── Department (L1)          ← COLLABORATES_WITH between depts
 * └── Team (L2)
 * └── Employee (L3) ← REPORTS_TO management chain
 * └── Project (cross-cutting) ← WORKS_ON (with role/allocation)
 * └── Technology (L4 leaf)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.graph.seed-data", havingValue = "true", matchIfMissing = true)
public class GraphDataSeeder {

    private final CompanyRepository companyRepo;
    private final DepartmentRepository deptRepo;
    private final TeamRepository teamRepo;
    private final EmployeeRepository employeeRepo;
    private final ProjectRepository projectRepo;
    private final TechnologyRepository techRepo;
    private final Neo4jClient neo4jClient;

    /**
     * Seeds the sample TechCorp knowledge graph on application startup, unless data already exists.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (companyRepo.count() > 0) {
            log.info("Graph data already present — skipping seeding");
            return;
        }
        log.info("Seeding Neo4j knowledge graph …");

        createFullTextIndex();

        // ── Technologies (leaf nodes) ──────────────────────────────────────
        Technology java = save(new Technology("Java", "language", "Enterprise JVM language", "21"));
        Technology spring = save(new Technology("Spring Boot", "framework", "Java microservices framework", "4.x"));
        Technology kubernetes = save(new Technology("Kubernetes", "platform", "Container orchestration", "1.29"));
        Technology react = save(new Technology("React", "framework", "UI component library", "18"));
        Technology typescript = save(new Technology("TypeScript", "language", "Typed superset of JavaScript", "5"));
        Technology graphql = save(new Technology("GraphQL", "framework", "API query language", "16"));
        Technology python = save(new Technology("Python", "language", "Data science & scripting", "3.12"));
        Technology tensorflow = save(new Technology("TensorFlow", "framework", "ML training framework", "2.x"));
        Technology spark = save(new Technology("Apache Spark", "platform", "Distributed data processing", "3.5"));
        Technology kafka = save(new Technology("Apache Kafka", "platform", "Event streaming platform", "3.x"));
        Technology postgres = save(new Technology("PostgreSQL", "platform", "Relational database", "16"));
        Technology neo4j = save(new Technology("Neo4j", "platform", "Native graph database", "5.x"));

        // ── Projects (cross-cutting) ───────────────────────────────────────
        Project alphaApi = new Project("Project Alpha",
                "Core API platform providing microservices for internal and external consumers",
                "active", "2023-01-15", "Deliver a scalable REST + GraphQL API platform");
        alphaApi.setTechnologies(List.of(java, spring, kubernetes, postgres));
        alphaApi = projectRepo.save(alphaApi);

        Project betaPortal = new Project("Project Beta",
                "Customer-facing web portal with real-time data updates",
                "active", "2023-06-01", "Ship a production-ready customer portal");
        betaPortal.setTechnologies(List.of(react, typescript, graphql));
        betaPortal = projectRepo.save(betaPortal);

        Project gammaML = new Project("Project Gamma",
                "Machine learning pipeline for product recommendation and fraud detection",
                "active", "2024-01-10", "Build an end-to-end ML inference platform");
        gammaML.setTechnologies(List.of(python, tensorflow, spark, kafka));
        gammaML = projectRepo.save(gammaML);

        Project deltaStream = new Project("Project Delta",
                "Real-time event streaming infrastructure connecting all internal services",
                "active", "2023-09-01", "Migrate all service events to Kafka-based streaming");
        deltaStream.setTechnologies(List.of(kafka, java, spring, kubernetes));
        deltaStream = projectRepo.save(deltaStream);

        Project epsilonGraph = new Project("Project Epsilon",
                "Knowledge graph platform for internal data discovery and lineage tracking",
                "active", "2024-03-01", "Build a company-wide knowledge graph on Neo4j");
        epsilonGraph.setTechnologies(List.of(neo4j, python, java, spring));
        epsilonGraph = projectRepo.save(epsilonGraph);

        // ── Company ────────────────────────────────────────────────────────
        Company techCorp = new Company(
                "TechCorp",
                "Software & AI",
                "A leading technology company building cloud-native platforms and AI solutions",
                "2018",
                "San Francisco, CA");

        // ── Departments (L1) ──────────────────────────────────────────────
        Department engineering = new Department(
                "Engineering",
                "Platform & API development",
                "Builds and maintains all backend services, APIs, and infrastructure",
                120);

        Department product = new Department(
                "Product",
                "Product management & design",
                "Defines product strategy, manages roadmaps, and coordinates across teams",
                35);

        Department dataSci = new Department(
                "Data Science",
                "Machine learning & analytics",
                "Develops ML models, analytical pipelines, and data infrastructure",
                45);

        // Collaboration edges between departments
        engineering.setCollaborators(List.of(dataSci, product));
        dataSci.setCollaborators(List.of(engineering));
        product.setCollaborators(List.of(engineering, dataSci));

        // Project ownership
        engineering.setProjects(List.of(alphaApi, deltaStream));
        product.setProjects(List.of(betaPortal));
        dataSci.setProjects(List.of(gammaML, epsilonGraph));

        // ── Teams (L2) ────────────────────────────────────────────────────
        Team backendTeam = new Team("Backend Team",
                "Owns Java/Spring microservices and API gateway", "Scrum");
        Team platformTeam = new Team("Platform Team",
                "Owns Kubernetes clusters, CI/CD, and streaming infra", "Kanban");
        Team frontendTeam = new Team("Frontend Team",
                "Owns React portal and design system", "Scrum");
        Team productMgmt = new Team("Product Management",
                "Roadmap planning, feature specs, and stakeholder alignment", "OKR");
        Team mlTeam = new Team("ML Engineering",
                "Trains and serves ML models in production", "Scrum");
        Team dataTeam = new Team("Data Platform",
                "Builds data pipelines, warehousing, and graph infrastructure", "Kanban");

        // ── Employees (L3) ────────────────────────────────────────────────
        Employee alice = new Employee("Alice Chen", "Principal Engineer",
                "alice@techcorp.com",
                "Alice leads the backend platform; deep expertise in distributed systems and API design.",
                List.of("Java", "Spring Boot", "Kubernetes", "GraphQL", "System Design"), 9);

        Employee bob = new Employee("Bob Martinez", "Senior Engineer",
                "bob@techcorp.com",
                "Bob specialises in high-throughput services and event-driven architectures.",
                List.of("Java", "Apache Kafka", "Spring Boot", "PostgreSQL"), 6);

        Employee charlie = new Employee("Charlie Wang", "Tech Lead",
                "charlie@techcorp.com",
                "Charlie leads the frontend team and is passionate about design systems.",
                List.of("React", "TypeScript", "GraphQL", "CSS", "Testing"), 7);

        Employee diana = new Employee("Diana Patel", "Senior Frontend Engineer",
                "diana@techcorp.com",
                "Diana owns the React component library and accessibility initiatives.",
                List.of("React", "TypeScript", "WCAG", "Figma"), 5);

        Employee eve = new Employee("Eve Johnson", "VP of Product",
                "eve@techcorp.com",
                "Eve defines the product vision and manages cross-functional roadmaps.",
                List.of("Product Strategy", "OKRs", "Stakeholder Management", "Data Analysis"), 12);

        Employee frank = new Employee("Frank Kim", "Platform Engineer",
                "frank@techcorp.com",
                "Frank owns Kubernetes, Helm charts, and the streaming platform migration.",
                List.of("Kubernetes", "Helm", "Terraform", "Apache Kafka", "Java"), 4);

        Employee grace = new Employee("Grace Liu", "ML Engineer",
                "grace@techcorp.com",
                "Grace designs and trains production ML models for recommendation and fraud detection.",
                List.of("Python", "TensorFlow", "PyTorch", "Feature Engineering", "MLOps"), 6);

        Employee henry = new Employee("Henry Brown", "Data Scientist",
                "henry@techcorp.com",
                "Henry builds analytical pipelines and performs statistical analysis for product teams.",
                List.of("Python", "Apache Spark", "SQL", "Statistics", "Neo4j"), 5);

        Employee isabel = new Employee("Isabel Torres", "Senior Product Manager",
                "isabel@techcorp.com",
                "Isabel owns the customer portal roadmap and coordinates Engineering + Data Science.",
                List.of("Roadmapping", "User Research", "SQL", "Figma", "A/B Testing"), 7);

        Employee james = new Employee("James Wright", "Staff Engineer",
                "james@techcorp.com",
                "James architects cross-system integrations and drives tech decisions in Engineering.",
                List.of("Java", "Neo4j", "System Architecture", "Apache Kafka", "Kubernetes"), 10);

        // ── Management chain (REPORTS_TO) ─────────────────────────────────
        alice.setManager(james);
        bob.setManager(alice);
        charlie.setManager(alice);
        diana.setManager(charlie);
        frank.setManager(james);
        grace.setManager(eve);
        henry.setManager(grace);
        isabel.setManager(eve);

        // ── Project assignments (WORKS_ON with role + allocation) ─────────
        alice.setProjectAssignments(List.of(
                new WorksOnRelationship("lead", "2023-01-15", 60, alphaApi),
                new WorksOnRelationship("architect", "2024-03-01", 20, epsilonGraph)));

        bob.setProjectAssignments(List.of(
                new WorksOnRelationship("contributor", "2023-01-15", 70, alphaApi),
                new WorksOnRelationship("contributor", "2023-09-01", 30, deltaStream)));

        charlie.setProjectAssignments(List.of(
                new WorksOnRelationship("lead", "2023-06-01", 80, betaPortal)));

        diana.setProjectAssignments(List.of(
                new WorksOnRelationship("contributor", "2023-06-01", 100, betaPortal)));

        frank.setProjectAssignments(List.of(
                new WorksOnRelationship("lead", "2023-09-01", 80, deltaStream),
                new WorksOnRelationship("contributor", "2023-01-15", 20, alphaApi)));

        grace.setProjectAssignments(List.of(
                new WorksOnRelationship("lead", "2024-01-10", 70, gammaML),
                new WorksOnRelationship("contributor", "2024-03-01", 30, epsilonGraph)));

        henry.setProjectAssignments(List.of(
                new WorksOnRelationship("contributor", "2024-01-10", 60, gammaML),
                new WorksOnRelationship("lead", "2024-03-01", 40, epsilonGraph)));

        james.setProjectAssignments(List.of(
                new WorksOnRelationship("architect", "2023-09-01", 40, deltaStream),
                new WorksOnRelationship("architect", "2024-03-01", 40, epsilonGraph),
                new WorksOnRelationship("reviewer", "2023-01-15", 20, alphaApi)));

        isabel.setProjectAssignments(List.of(
                new WorksOnRelationship("pm", "2023-06-01", 50, betaPortal),
                new WorksOnRelationship("pm", "2024-01-10", 50, gammaML)));

        // ── Assemble teams ─────────────────────────────────────────────────
        backendTeam.setMembers(List.of(alice, bob, charlie));
        platformTeam.setMembers(List.of(frank, james));
        frontendTeam.setMembers(List.of(charlie, diana));
        productMgmt.setMembers(List.of(eve, isabel));
        mlTeam.setMembers(List.of(grace));
        dataTeam.setMembers(List.of(henry));

        // ── Assemble departments ───────────────────────────────────────────
        engineering.setTeams(List.of(backendTeam, platformTeam));
        product.setTeams(List.of(frontendTeam, productMgmt));
        dataSci.setTeams(List.of(mlTeam, dataTeam));

        // ── Assemble company ───────────────────────────────────────────────
        techCorp.setDepartments(List.of(engineering, product, dataSci));
        companyRepo.save(techCorp);

        log.info("Graph seeded: {} companies, {} departments, {} teams, {} employees, {} projects, {} technologies",
                companyRepo.count(), deptRepo.count(), teamRepo.count(),
                employeeRepo.count(), projectRepo.count(), techRepo.count());
    }

    private void createFullTextIndex() {
        try {
            neo4jClient.query(Neo4jConfig.FULLTEXT_INDEX_QUERY).run();
            log.info("Full-text index 'entitySearch' ensured");
        } catch (Exception e) {
            log.warn("Could not create full-text index (may already exist): {}", e.getMessage());
        }
    }

    private Technology save(Technology t) {
        return techRepo.save(t);
    }
}
