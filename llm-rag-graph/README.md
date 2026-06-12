# LLM RAG Graph

A **Graph RAG** (Retrieval-Augmented Generation) pipeline built on **Neo4j** and **Anthropic Claude**. It models a 4-level corporate knowledge graph and answers natural-language questions by traversing graph relationships before calling the LLM — going far beyond what flat vector search can do.

---

## What is RAG?

Retrieval-Augmented Generation combines a retrieval step with an LLM. Instead of relying on the model's training knowledge, you fetch relevant context at query time, inject it into the prompt, and let the LLM reason over fresh, private, or structured data.

---

## Traditional RAG vs Graph RAG

| Dimension | Traditional RAG | Graph RAG |
|---|---|---|
| **Storage** | Vector database (embeddings) | Graph database (nodes + relationships) |
| **Retrieval unit** | Chunk of text (flat) | Paths and subgraphs (structured) |
| **Relationship awareness** | None — chunks are independent | First-class — traverses edges at query time |
| **Multi-hop reasoning** | Weak — requires the answer to be in one chunk | Strong — follows chains: Employee → Manager → Department → Project |
| **Context quality** | Semantically similar text | Factually connected entities with typed relationships |
| **Data freshness** | Requires re-embedding on updates | Graph writes are immediately queryable |
| **Query type** | "What does doc X say about topic Y?" | "Who in Engineering works on ML projects and reports to the CTO?" |
| **Hallucination risk** | Higher — context gaps are silent | Lower — missing relationships return no path, not a wrong answer |

### Why relationships matter

Traditional RAG retrieves the most similar text chunks. If you ask *"Which engineers on the fraud detection project report to Eve?"* it needs the answer co-located in a single chunk. In a knowledge graph that query is a 3-hop Cypher traversal:

```
Employee -[:WORKS_ON]-> Project <-[:WORKS_ON]- Employee -[:REPORTS_TO*]-> Eve
```

The graph returns precise, structured facts. The LLM's job is then to narrate those facts, not to invent them.

---

## Architecture

```
HTTP Request
     │
     ▼
RagController  POST /api/rag/query
     │
     ▼
GraphRAGService   ──── orchestrates ────┐
     │                                  │
     ▼                                  ▼
GraphContextExtractor          AnthropicLLMService
  1. Keyword extraction           claude-opus-4-8
  2. Full-text index search       Injects graph context
  3. Entity traversal             into the prompt
     • Employees + managers
     • Departments + teams
     • Projects + technologies
  4. Multi-level Cypher paths
     • Org hierarchy (4 hops)
     • Management chains
     • Project ↔ technology paths
     • Cross-dept collaboration
  5. Deduplicate & format
     │
     ▼
  GraphContext (structured text + entity list)
     │
     ▼
LLM generates grounded answer
     │
     ▼
RagResponse { question, answer, graphContext, entities, latencyMs }
```

---

## Knowledge Graph — TechCorp

The seeder (`GraphDataSeeder`) builds a realistic 4-level corporate hierarchy on startup.

```
TechCorp (Company)
├── Engineering (Department)  ──COLLABORATES_WITH──► Data Science, Product
│   ├── Backend Team
│   │   ├── Alice Chen  (Principal Engineer)  ──REPORTS_TO──► James Wright
│   │   └── Bob Martinez (Senior Engineer)   ──REPORTS_TO──► Alice
│   └── Platform Team
│       ├── Frank Kim   (Platform Engineer)  ──REPORTS_TO──► James
│       └── James Wright (Staff Engineer)
│
├── Product (Department)      ──COLLABORATES_WITH──► Engineering, Data Science
│   ├── Frontend Team
│   │   ├── Charlie Wang (Tech Lead)         ──REPORTS_TO──► Alice
│   │   └── Diana Patel  (Senior Frontend)   ──REPORTS_TO──► Charlie
│   └── Product Management
│       ├── Eve Johnson  (VP of Product)
│       └── Isabel Torres (Senior PM)        ──REPORTS_TO──► Eve
│
└── Data Science (Department) ──COLLABORATES_WITH──► Engineering
    ├── ML Engineering
    │   └── Grace Liu    (ML Engineer)       ──REPORTS_TO──► Eve
    └── Data Platform
        └── Henry Brown  (Data Scientist)    ──REPORTS_TO──► Grace
```

### Projects (cross-cutting)

| Project | Status | Technologies | Owner dept |
|---|---|---|---|
| Project Alpha | active | Java, Spring Boot, Kubernetes, PostgreSQL | Engineering |
| Project Beta | active | React, TypeScript, GraphQL | Product |
| Project Gamma | active | Python, TensorFlow, Apache Spark, Kafka | Data Science |
| Project Delta | active | Apache Kafka, Java, Spring Boot, Kubernetes | Engineering |
| Project Epsilon | active | Neo4j, Python, Java, Spring Boot | Data Science |

### Relationship types

| Relationship | From → To | Properties |
|---|---|---|
| `HAS_DEPARTMENT` | Company → Department | — |
| `HAS_TEAM` | Department → Team | — |
| `HAS_MEMBER` | Team → Employee | — |
| `REPORTS_TO` | Employee → Employee | — |
| `WORKS_ON` | Employee → Project | `role`, `allocationPct`, `startDate` |
| `USES_TECHNOLOGY` | Project → Technology | — |
| `COLLABORATES_WITH` | Department → Department | — |
| `OWNS_PROJECT` | Department → Project | — |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.1 |
| Graph DB | Neo4j 5.x (Spring Data Neo4j) |
| LLM | Anthropic Claude (claude-opus-4-8) via `anthropic-java` SDK 2.34 |
| Build | Maven |
| Boilerplate | Lombok |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- Neo4j 5.x running locally (default: `bolt://localhost:7687`)
- An [Anthropic API key](https://console.anthropic.com/)

---

## Setup

### 1. Start Neo4j

Using Docker:

```bash
docker run -d \
  --name neo4j \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password \
  neo4j:5
```

Or use [Neo4j Desktop](https://neo4j.com/download/).

### 2. Set environment variables

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export NEO4J_PASSWORD=password       # default: password
```

### 3. Build and run

```bash
./mvnw spring-boot:run
```

On first startup the application seeds the full TechCorp knowledge graph automatically (`app.graph.seed-data: true`). Subsequent restarts skip seeding if data is already present.

---

## API Reference

### Query the RAG pipeline

```
POST /api/rag/query
Content-Type: application/json

{
  "question": "Who are the engineers working on the ML projects and who do they report to?"
}
```

**Response**

```json
{
  "question": "...",
  "answer": "Grace Liu (ML Engineer) leads Project Gamma...",
  "graphContext": "=== Graph Knowledge Context ===\n• Employee Grace Liu...",
  "entities": ["Grace Liu", "Project Gamma", "Data Science"],
  "latencyMs": 1240
}
```

### Graph inspection endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/graph/stats` | Node and relationship counts |
| `GET` | `/api/graph/companies/{name}/hierarchy` | Full company hierarchy |
| `GET` | `/api/graph/companies/{name}/employees` | All employees in a company |
| `GET` | `/api/graph/employees/{name}/context` | Employee with projects and manager |
| `GET` | `/api/graph/employees/{name}/reports` | Direct reports of an employee |
| `GET` | `/api/graph/projects/{name}/team` | Everyone working on a project |

### Health check

```
GET /actuator/health
```

---

## Example questions to try

```
Who does Alice Chen report to?
Which engineers have Kubernetes skills?
What technologies does Project Gamma use?
Who is working on Project Epsilon and in what role?
Which departments collaborate with Engineering?
List all employees in the Data Science department.
Who are Grace Liu's direct reports?
What projects is James Wright involved in?
```

---

## Configuration

Key settings in `application.yaml`:

```yaml
app:
  anthropic:
    model: claude-opus-4-8   # LLM model
    max-tokens: 4096
  graph:
    seed-data: true           # auto-seed on first boot
    context-depth: 4          # max relationship hops
    max-context-nodes: 20     # cap on retrieved entities
```

---

## How the context extraction works

1. **Keyword extraction** — stop words are stripped; up to 8 meaningful terms are pulled from the question.
2. **Full-text index search** — Neo4j's `entitySearch` index is queried for matching node names across all labels.
3. **Typed traversals** — for each keyword, four specialised Cypher queries run in parallel:
   - Org hierarchy paths: `Company → Department → Team → Employee`
   - Project paths: `Employee → Project → Technology`
   - Management chains: `Employee -[:REPORTS_TO*1..3]-> Manager`
   - Cross-department collaboration: `Department -[:COLLABORATES_WITH]-> Department`
4. **Deduplication and capping** — results are deduplicated and capped at `maxContextNodes × 3` lines.
5. **Prompt injection** — the formatted context block is prepended to the LLM prompt so Claude reasons over graph facts, not its training weights.

## 🧩 Design patterns

This module stays deliberately small: `GraphRAGService` is a **Facade** over the multi-step
keyword-extraction → graph-traversal → prompt-assembly → LLM flow, and the Spring Data Neo4j
repositories follow the Repository pattern. There is a single LLM provider
(`AnthropicLLMService`), so no Strategy interface is introduced — see the
[Design patterns section](../llm-rag-pipeline/README.md#-design-patterns-gof) in
`llm-rag-pipeline` for the GoF patterns used across the llm-rag modules and the reasoning about
where patterns are deliberately not applied.

## 🏗️ Build & test

```bash
mvn test
```

Tests run **offline** — the `test` profile disables graph seeding and supplies a dummy Anthropic
key, so no running Neo4j instance or real API key is needed. `GraphRAGServiceTest` unit-tests the
RAG orchestration (context extraction → LLM answer → response assembly) and the graph-stats
aggregation with mocked collaborators; `LlmRagGraphApplicationTests` verifies the Spring context
assembles.
