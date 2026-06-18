# llm-rag

A multi-module Maven project exploring three complementary approaches to **Retrieval-Augmented
Generation (RAG)**: a production-grade vector pipeline, a vectorless keyword/tree-reasoning
service, and a knowledge-graph pipeline. Each module is an independent Spring Boot application;
the root `pom.xml` is a plain aggregator.

| Module                                               | Approach                           | Storage                           | LLM usage                                                   |
|------------------------------------------------------|------------------------------------|-----------------------------------|-------------------------------------------------------------|
| [`llm-rag-pipeline`](llm-rag-pipeline/README.md)     | Vector RAG (ingestion + retrieval) | OpenSearch (kNN) + PostgreSQL     | Embeddings always; chunking / enrichment / reranking opt-in |
| [`llm-rag-vectorless`](llm-rag-vectorless/README.md) | Vectorless RAG (BM25 + PageIndex)  | In-memory index / PageIndex cloud | Claude for answer generation                                |
| [`llm-rag-graph`](llm-rag-graph/README.md)           | Graph RAG                          | Neo4j knowledge graph             | Claude reasons over traversed graph context                 |

## Modules

### [llm-rag-pipeline](llm-rag-pipeline/README.md) — Spring AI vector RAG backend

- **Ingestion:** reads PDF/OCR, Markdown, Excel, and generic formats via Tika; normalises, deduplicates by content hash,
  and chunks via a pluggable `ChunkingStrategy`
- **Storage:** vectors written to OpenSearch kNN index; structured source data and operational metadata in PostgreSQL
- **Retrieval:** `SearchStrategy` interface with vector kNN, BM25 keyword, and hybrid RRF search; post-processor chain
  of filters, dedup, six reranking strategies, and MMR diversity
- **Security & reliability:** API-key auth, SHA-256 hashed, rate limiting, Resilience4j circuit breaker
- **Observability:** Prometheus metrics, Grafana dashboards, Tempo distributed tracing, Loki structured logging
- **Quality:** retrieval evaluation (MRR, P@k, R@k, RAGAS context precision); answer generation is out of scope —
  downstream consumers handle that

### [llm-rag-vectorless](llm-rag-vectorless/README.md) — RAG without embeddings

- **No vectors required:** works without an embedding model, vector database, or GPU
- **BM25 retriever:** in-process inverted index built at startup (k1=1.2, b=0.75); always on
- **PageIndex retriever:** cloud tree-reasoning service that navigates a hierarchical document index with LLM-guided
  reasoning; opt-in via `PAGEINDEX_API_KEY`
- **Generation:** Claude (via Spring AI `ChatClient`) produces grounded answers from retrieved chunks
- **Comparison:** both retrievers feed the same prompt template, enabling side-by-side evaluation on identical questions

### [llm-rag-graph](llm-rag-graph/README.md) — Graph RAG on Neo4j

- **Graph traversal:** answers questions by walking a knowledge graph rather than comparing vectors
- **Schema:** 4-level corporate graph — Company → Department → Team → Employee — plus Projects, Technologies, and
  management/collaboration edges
- **Retrieval:** full-text APOC index for entity lookup, then multi-hop Cypher traversals for relationship paths
- **Generation:** extracted subgraph context injected into a Claude prompt with extended thinking enabled; LLM narrates
  structured facts rather than inventing them
- **Strength:** multi-hop questions flat RAG cannot answer, e.g. *"Who in Engineering works on ML projects and reports
  to the CTO?"*

## Design patterns

The variation points across the modules are modelled with classic Gang-of-Four patterns —
Strategy, Chain of Responsibility, Factory, Template Method, Composite, Proxy, Facade, Adapter —
plus several additional patterns added to improve correctness, extensibility, and observability.
Patterns are deliberately *omitted* where no real variation point exists.

| Pattern                      | Where                                                                                                               | What it does                                                         |
|------------------------------|---------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| **Strategy**                 | `SearchStrategy`, `ChunkingStrategy`, `RetrievalPostProcessor`, `GraphExtractionStrategy`, `DocumentReaderStrategy` | Swap algorithms at runtime without touching callers                  |
| **Template Method**          | `DatabaseIngestionService.ingestTable()`                                                                            | Shared SQL+map loop; table-specific row mapping supplied by subclass |
| **Command**                  | `IngestionCommand`, `DeleteCommand`, `IngestAllCommand`, `CommandExecutor`                                          | Lifecycle operations as first-class objects with audit logging       |
| **Observer** (Spring Events) | `IngestionCompletedEvent`, `VectorsStoredEvent`                                                                     | Pipeline stages decouple via `ApplicationEventPublisher`             |
| **Decorator**                | `MeteredReranker`, `CachedReranker`                                                                                 | Wrap any `Reranker` with metrics and cache without subclassing       |
| **Chain of Responsibility**  | `RetrievalPostProcessor` chain                                                                                      | Filter → dedup → rerank pipeline composable at startup               |
| **Factory / Builder**        | `ChatClient.Builder`, `QuestionAnswerAdvisor.Builder`                                                               | Spring AI advisor configuration                                      |
| **Facade**                   | `KnowledgeLifecycleService`, `GraphRAGService`                                                                      | Hide multi-step orchestration behind a single interface              |

## Recent improvements

### llm-rag-pipeline

| Area                                         | Change                                                                                                                                                                                                                                                                                                                                                             |
|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Multi-turn conversation**                  | `GenerationService` now holds a `MessageWindowChatMemory` (Spring AI 2.0.0). Pass `conversationId` in `GenerateRequest` to continue a session; omit it for single-turn (UUID generated per request). Conversation ID is injected via `ChatMemory.CONVERSATION_ID` advisor param.                                                                                   |
| **Streaming generation (SSE)**               | `POST /api/v1/generate/stream` returns `text/event-stream` via `Flux<String>`. Same RAG pipeline as the blocking endpoint — injection guard, context rebuild, ChatMemory advisor.                                                                                                                                                                                  |
| **Async ingestion**                          | `POST /api/v1/upload/async` accepts a file and immediately returns HTTP 202 with a `jobId`. The ingestion runs in a dedicated `ThreadPoolTaskExecutor`. `GET /api/v1/upload/{jobId}/status` polls the `IngestionJob` (PENDING → RUNNING → DONE / FAILED).                                                                                                          |
| **Command pattern for lifecycle**            | `IngestCommand`, `DeleteCommand`, `IngestAllCommand` wrap each lifecycle operation. `CommandExecutor` provides audit logging at a single point.                                                                                                                                                                                                                    |
| **Spring Application Events**                | `KnowledgeLifecycleService` publishes `IngestionCompletedEvent` and `VectorsStoredEvent` after each stage. Listeners can react to pipeline progress without coupling to the service.                                                                                                                                                                               |
| **Decorator on Reranker**                    | Every `Reranker` is wrapped at startup by `MeteredReranker` (Micrometer `Timer` + failure counter) then `CachedReranker` (score cache to avoid re-ranking identical query+chunk pairs).                                                                                                                                                                            |
| **Resilience4j circuit breaker**             | `RerankingPostProcessor` uses Resilience4j `CircuitBreaker` (replacing a hand-rolled state machine). Metrics integrate automatically with Micrometer.                                                                                                                                                                                                              |
| **VectorMath utility**                       | `com.org.common.VectorMath.cosine()` consolidates duplicate cosine-similarity implementations from `SemanticCacheService`, `SemanticChunkingStrategy`, and `TextSimilarity`.                                                                                                                                                                                       |
| **EmbeddingCacheService**                    | Fixed race condition (ConcurrentHashMap replaces synchronized LinkedHashMap). SHA-256 reused per-thread via `ThreadLocal<MessageDigest>`.                                                                                                                                                                                                                          |
| **RateLimitFilter**                          | API key is hashed with SHA-256 before bucketing (was `hashCode()`, causing collisions across different keys).                                                                                                                                                                                                                                                      |
| **Actuator security**                        | Health detail and component info only exposed to callers with `ACTUATOR` role (`when-authorized`). Previously always-visible.                                                                                                                                                                                                                                      |
| **CORS**                                     | `PUT` and `PATCH` added to allowed methods in `SecurityConfig`.                                                                                                                                                                                                                                                                                                    |
| **@Transactional guard**                     | `KnowledgeLifecycleService.reingest()` now deletes existing vectors only after chunking succeeds and produces a non-empty list, preventing data loss on chunking failures.                                                                                                                                                                                         |
| **DocumentReaderFactory — Strategy pattern** | File-type dispatch extracted from a monolithic switch into per-type `DocumentReaderStrategy` `@Component` beans (`PdfReaderStrategy`, `MarkdownReaderStrategy`, `ExcelReaderStrategy`, `TikaReaderStrategy`, …). `DocumentType` enum owns the extension→source-label mapping. Adding a new file type only requires a new `@Component` — no changes to the factory. |
| **UnsupportedDocumentTypeException**         | Custom exception replaces `IllegalArgumentException` for unknown file types. `GlobalExceptionHandler` maps it to HTTP 415 Unsupported Media Type.                                                                                                                                                                                                                  |
| **Lombok boilerplate removed**               | 9 `@ConfigurationProperties` classes converted from manual getters/setters to `@Data`. Command classes (`IngestCommand`, `DeleteCommand`, `IngestAllCommand`) use `@RequiredArgsConstructor`. Event classes (`IngestionCompletedEvent`, `VectorsStoredEvent`) use `@Getter` in place of manual accessors.                                                          |

### llm-rag-graph

| Area                                      | Change                                                                                                                                                                                     |
|-------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Security layer**                        | API-key authentication and rate limiting added (mirrors pipeline module). Configured via `app.security.*` properties.                                                                      |
| **GlobalExceptionHandler**                | `@RestControllerAdvice` returns RFC 9457 `ProblemDetail` JSON for validation errors, bad requests, and `LlmCallException`.                                                                 |
| **LLM error handling**                    | `AnthropicLLMService` wraps all SDK exceptions in `LlmCallException`. Callers see a typed exception instead of a raw `RuntimeException` with a raw stack trace.                            |
| **Strategy pattern for graph extraction** | `GraphExtractionStrategy` interface + `PathTraversalStrategy` implementation. New traversal approaches (semantic, embedding-based) can be added without modifying `GraphContextExtractor`. |
| **Test coverage**                         | `AnthropicLLMServiceTest` — network failure and auth failure scenarios. `GraphRAGServiceTest` — LLM exception propagation test.                                                            |

## Building and testing

```bash
# build everything
mvn clean install

# run all tests (no external services or API keys required)
mvn test

# work on a single module
mvn -pl llm-rag-graph test
```

Each module's README documents its own runtime prerequisites (Docker infra, API keys) — those are
needed to **run** the services, not to build or test them.

### Java 25 / Spring AI 2.0 migration verification

All three modules build against Java 25 and Spring AI 2.0.0 by inheritance from the shared
`super-pom` → `llm-bom` parent chain (`java.version=25`, `spring-ai.version=2.0.0`); no module
overrides either property, and the Dockerfiles for `llm-rag-pipeline`, `llm-rag-vectorless`, and
`llm-rag-graph` now build and run on `eclipse-temurin:25-jdk`/`25-jre`/`25-jre-alpine`.

What was verified in this pass (compiling and running with an Azul Zulu 25 JDK, offline `mvn -o`):

- **Compile** — `mvn -o clean compile` succeeds for the aggregator and all three submodules.
- **Test** — `mvn -o clean test` succeeds for all three submodules: `llm-rag-pipeline` (150 passed,
  21 skipped — Testcontainers-backed Postgres/OpenSearch tests, no Docker available here),
  `llm-rag-vectorless` (8 passed), `llm-rag-graph` (20 passed, 8 skipped — Testcontainers-backed
  Neo4j tests). One genuine regression was found and fixed during this verification:
  `GraphContextExtractorTest` mocked a `Neo4jClient.RunnableSpecThenProject` type that doesn't
  exist on the Spring Data Neo4j version pulled in by Spring Boot 4.1; it now mocks
  `Neo4jClient.UnboundRunnableSpec`, the type `Neo4jClient.query(...)` actually returns.
- **Boot attempt** (`mvn spring-boot:run`, no Docker) — `llm-rag-vectorless` and `llm-rag-graph`
  both start cleanly (Tomcat up on :8080, full context loaded); `llm-rag-graph` logs and continues
  past failed Neo4j index creation (`localhost:7687` unreachable) rather than failing hard.
  `llm-rag-pipeline` fails context startup only at the Postgres/Flyway step (`localhost:5432`
  refused) — every bean up to that point wires correctly, confirming the Java 25 / Spring AI 2.0
  bump didn't break dependency injection.
- **Not verified** — full integration against live Postgres, OpenSearch, or Neo4j, and anything
  requiring Docker/Testcontainers or `docker compose up`, since Docker is unavailable in this
  environment.

---

## Technology Deep Dive

This section explains every significant technology used across the three modules — what each one is
and exactly how this project uses it.

---

### Java 25

**What it is:** The latest release of the Java platform (GA June 2025, Azul Zulu build used here).

**How it's used here:** All three modules compile and run on Java 25. The project makes deliberate
use of modern Java features: sealed records (`RetrievalResult`, `RagResponse`, `GraphContext`) replace
verbose POJOs; text blocks clean up multi-line SQL and prompt strings throughout the codebase; and
`llm-rag-pipeline`'s `LlmPointwiseReranker` uses `Executors.newVirtualThreadPerTaskExecutor()` to
fire N parallel LLM grading calls with the cost of one round-trip rather than N sequential ones —
the virtual-threads feature introduced in Java 21 and available here on Java 25.

---

### Apache Maven (multi-module aggregator)

**What it is:** The standard Java build tool, here used in its multi-module mode where a root POM
aggregates child modules without sharing dependency management with them.

**How it's used here:** The root `pom.xml` is a pure aggregator (`<packaging>pom</packaging>`)
containing only `<modules>` entries. Each child module (`llm-rag-pipeline`, `llm-rag-vectorless`,
`llm-rag-graph`) has its own independent `pom.xml` that inherits from
`spring-boot-starter-parent`. This lets the three modules declare different Spring AI versions,
test dependencies, and build plugins without interfering with each other, while still being built
with a single `mvn clean install` from the root.

---

### Spring Boot 4.1

**What it is:** The industry-standard opinionated application framework for Java, providing
auto-configuration, an embedded Tomcat server, actuator endpoints, and a unified starter
dependency model.

**How it's used here:** All three modules are independent Spring Boot 4.1 applications. Spring
Boot handles: embedded HTTP server startup, `@ConfigurationProperties` binding (e.g.
`RetrievalProperties`, `RagProperties`), graceful shutdown with a 30-second drain period
(`llm-rag-pipeline`), JDBC/Neo4j auto-configuration, and the Actuator `/health`, `/info`,
`/metrics`, and `/prometheus` endpoints. The `spring-boot-docker-compose` dependency (optional,
dev-only) auto-manages the Docker Compose stack when running from an IDE.

---

### Spring AI 2.0.0

**What it is:** Anthropic's (and VMware's) abstraction layer that gives Spring applications a
uniform interface over multiple LLM providers, embedding models, vector stores, and document
loaders.

**How it's used here across modules:**

- **llm-rag-pipeline** uses `spring-ai-starter-model-openai` to call OpenAI's
  `text-embedding-3-small` model and convert document chunks into 1536-dimension float vectors
  before writing them to OpenSearch. It also uses `spring-ai-starter-vector-store-opensearch` as
  the Spring AI-managed vector store abstraction. The `ChatClient` bean (backed by OpenAI) drives
  the `LlmPointwiseReranker` and the optional `ChunkEnricher` (which calls Spring AI's
  `KeywordMetadataEnricher` and `SummaryMetadataEnricher` to add LLM-derived keywords and
  per-chunk summaries to chunk metadata). Document loading uses the three Spring AI readers:
  `spring-ai-pdf-document-reader` (text PDFs), `spring-ai-tika-document-reader` (generic MIME
  types via Apache Tika), and `spring-ai-markdown-document-reader` (wiki/markdown files).

- **llm-rag-vectorless** uses `spring-ai-starter-model-anthropic` to wire a `ChatClient` backed
  by Claude. The `RagController` calls `chatClient.prompt().user(message).call().content()` to
  generate grounded answers from BM25- or PageIndex-retrieved context. No vector store is
  involved.

---

### OpenAI Embeddings (`text-embedding-3-small`)

**What it is:** OpenAI's general-purpose text embedding model that converts arbitrary text into a
1536-dimension dense vector capturing semantic meaning.

**How it's used here:** Only `llm-rag-pipeline` uses embeddings. During ingestion, every text
chunk is sent to the OpenAI API and returned as a float array. That vector is stored alongside the
chunk text in the OpenSearch kNN index. At query time, the user's question is embedded with the
same model and the resulting vector is used to perform an approximate nearest-neighbor search over
all stored chunk vectors. The dimension `1536` is pinned in `application.yml` and must match what
`text-embedding-3-small` returns.

---

### OpenSearch 2.17.1

**What it is:** An open-source search and analytics engine (AWS fork of Elasticsearch) that
supports both traditional BM25 keyword search and approximate k-nearest-neighbor (kNN) vector
search via the HNSW algorithm.

**How it's used here:** `llm-rag-pipeline` stores every ingested chunk in an OpenSearch index
(`nexacorp_index` by default). The index is created with the `knn` field type and `hnsw` engine
so Spring AI's `OpenSearchVectorStore` can do cosine-similarity vector search. Three interchangeable
`SearchStrategy` implementations exist:

- `VectorSearchStrategy` — pure kNN vector search using the embedded query.
- `KeywordSearchStrategy` — BM25 full-text search using the `match` query DSL.
- `HybridSearchStrategy` — runs both sides and fuses their ranked lists with **Reciprocal Rank
  Fusion** (damping constant k=60 from the original Cormack et al. paper), so neither score scale
  needs to be normalized before merging.

OpenSearch Dashboards (port 5601) is also included in `docker-compose.yml` for visual inspection
of the index during development.

---

### PostgreSQL 18

**What it is:** The leading open-source relational database.

**How it's used here:** Used exclusively in `llm-rag-pipeline`. It stores two categories of data:

1. **Structured source content** — `faqs`, `release_notes`, and `announcements` tables (seeded
   in `V1__init_schema.sql` and `V2__seed_data.sql`) that the `DatabaseIngestionService` reads,
   formats as Markdown rows, and feeds into the chunking + embedding pipeline.
2. **Operational metadata** — an `api_keys` table (`V3__api_keys.sql`) where API keys are stored
   as SHA-256 hex digests (never the raw key), with `enabled`, `expires_at`, and `last_used`
   columns; and an `ingestion_log` table (`V4__ingestion_log.sql`) that records every ingestion
   run for deduplication and audit.

Spring Boot's `JdbcTemplate` (not JPA/Hibernate) is used throughout — keeping the persistence
layer thin and explicit.

---

### Flyway

**What it is:** A database migration library that applies versioned SQL scripts to a schema in
order, tracking which have already run in a `flyway_schema_history` table.

**How it's used here:** `llm-rag-pipeline` uses Flyway to manage the PostgreSQL schema. Four
migration scripts live under `src/main/resources/db/migration/`: `V1` creates the source-content
tables, `V2` seeds sample data, `V3` adds the `api_keys` table, and `V4` adds the `ingestion_log`
table. Migrations run automatically on startup. Spring Boot 4 requires both `flyway-core` and the
separate `spring-boot-flyway` auto-configuration module to trigger migration — a Spring Boot 4
split that is explicitly documented in the `pom.xml` comments.

---

### Neo4j 5 with APOC

**What it is:** A native graph database where data is stored as nodes and directed relationships
rather than rows and columns. APOC (Awesome Procedures On Cypher) is a plugin that adds hundreds
of utility procedures, including full-text index management.

**How it's used here:** `llm-rag-graph` stores an entire corporate knowledge graph in Neo4j: six
node types (`Company`, `Department`, `Team`, `Employee`, `Project`, `Technology`) connected by
six relationship types (`HAS_DEPARTMENT`, `HAS_TEAM`, `HAS_MEMBER`, `REPORTS_TO`, `WORKS_ON`,
`USES_TECHNOLOGY`, `COLLABORATES_WITH`). The graph is seeded automatically at startup by
`GraphDataSeeder` if the database is empty. APOC is used to create a full-text index
(`entitySearch`) across all node `name` properties so the `GraphContextExtractor` can run fuzzy
keyword lookups before following relationship traversals. The Bolt protocol (port 7687) is used
for all application-to-database communication.

---

### Spring Data Neo4j

**What it is:** The Spring Data module for Neo4j, providing `@Node`-annotated domain objects,
`Neo4jRepository` interfaces, and a `Neo4jClient` for raw Cypher queries.

**How it's used here:** `llm-rag-graph` models its domain as `@Node` classes (`Employee`,
`Department`, `Team`, `Company`, `Project`, `Technology`) with `@Relationship` annotations that
tell Spring Data Neo4j how to load connected nodes eagerly. Six repository interfaces
(`EmployeeRepository`, `DepartmentRepository`, etc.) extend `Neo4jRepository` and declare
`@Query`-annotated methods for keyword search. The `GraphContextExtractor` mixes Spring Data
repository calls (for typed, eagerly-fetched entities) with direct `Neo4jClient` Cypher queries
(for multi-hop path traversals like management chains and collaboration paths that are awkward to
express as derived query methods).

---

### Anthropic Java SDK (`anthropic-java` 2.34.0)

**What it is:** Anthropic's official first-party Java SDK for calling the Claude API directly,
without going through a Spring AI abstraction layer.

**How it's used here:** `llm-rag-graph` uses the SDK directly in `AnthropicLLMService` to call
Claude with an `AnthropicClient` bean. This module deliberately bypasses Spring AI to show how
the raw SDK works: it builds a `MessageCreateParams` object with a structured system prompt (that
explains the graph schema to Claude), passes the formatted graph-context string and the user's
question as the user message, and enables `ThinkingConfigAdaptive` (Claude's extended thinking
mode) for deeper reasoning over the graph context. The response is parsed by filtering content
blocks for `isText()`.

---

### Claude (Anthropic LLM)

**What it is:** Anthropic's family of large language models, accessed via API. This project uses
`claude-opus-4-8` by default in `llm-rag-graph` and whichever model is configured in
`llm-rag-vectorless`.

**How it's used here:**

- In **llm-rag-graph**, Claude receives the formatted graph-context string extracted from Neo4j
  (structured as bullet points listing entity facts, management chains, project assignments, and
  collaboration edges) plus the user's natural-language question. It is instructed to answer
  *only* from the provided graph context, cite node names and relationship paths explicitly, and
  flag when context is insufficient — preventing hallucination.

- In **llm-rag-vectorless**, Claude is called via Spring AI's `ChatClient` to generate answers
  grounded in the BM25- or PageIndex-retrieved text chunks. The prompt instructs it to answer
  only from the provided context or say it doesn't know.

- In **llm-rag-pipeline**, Claude (or another OpenAI-compatible model via the `ChatClient`) is
  optionally used for two purposes: the `LlmPointwiseReranker` asks it to score each candidate
  chunk's relevance on a 0–100 scale; and the `ChunkEnricher` (when enabled) calls Spring AI's
  `SummaryMetadataEnricher` and `KeywordMetadataEnricher` to generate per-chunk summaries and
  keyword tags.

---

### BM25 (Okapi BM25)

**What it is:** A classic probabilistic information-retrieval ranking function — the algorithm
behind most traditional search engines. It scores documents by term-frequency saturation and
document-length normalization without any machine learning.

**How it's used here in two distinct modules:**

- **llm-rag-vectorless** implements BM25 from scratch in `BM25Retriever`. At startup it builds
  an in-memory inverted index over all loaded document chunks (TF arrays keyed by term, plus IDF
  map), with parameters k1=1.2 and b=0.75. Every query is tokenized (lowercased, punctuation
  stripped, stop words removed) and scored against the index at O(|query terms| × |corpus|) cost.
  This is the always-on retrieval path — no GPU, no API key, no vector database required.

- **llm-rag-pipeline** has a `KeywordSearchStrategy` that delegates to OpenSearch's built-in BM25
  `match` query (which OpenSearch computes server-side over its inverted index), and a
  `Bm25Reranker` that re-scores an already-retrieved candidate set in-process for a second-pass
  reranking step.

---

### PageIndex (cloud tree-reasoning retrieval)

**What it is:** A third-party cloud service (`api.pageindex.ai`) that accepts PDF uploads, builds
a hierarchical document index, and performs retrieval using LLM-guided tree reasoning rather than
vector similarity — no embedding model required.

**How it's used here:** `llm-rag-vectorless`'s `PageIndexClient` exposes three operations: upload
a PDF (returns a `doc_id`), poll until processing is complete, and query for relevant excerpts.
The retrieval call is asynchronous — the client posts a retrieval job and polls until status is
`completed`, then extracts text from `retrieved_nodes[].relevant_contents[].relevant_content`.
PageIndex is opt-in (activated by `rag.pageindex.enabled=true` and a `PAGEINDEX_API_KEY`), so the
module runs fully offline using only BM25 when the key is absent.

---

### Apache PDFBox 3.x

**What it is:** An open-source Java library for reading, rendering, and extracting text from PDF
files.

**How it's used here:** Used in both `llm-rag-pipeline` and `llm-rag-vectorless`. In
`llm-rag-pipeline` it serves two roles: Spring AI's `PdfDocumentReader` uses it under the hood to
extract text from text-based PDFs, and the `PdfIngestionService` uses PDFBox directly to render
individual scanned pages to `BufferedImage` objects before passing them to Tesseract OCR. PDFBox
is pinned to version 3.0.7 to match the version Spring AI's PDF/Tika readers bring transitively —
downgrading to 2.x would cause class conflicts.

---

### Apache Tika

**What it is:** A content-detection and extraction toolkit that can parse hundreds of file formats
(Word, PowerPoint, HTML, XML, etc.) and return plain text.

**How it's used here:** `llm-rag-pipeline` includes `spring-ai-tika-document-reader`, which wraps
Tika as a catch-all document reader. When the `ChunkingOrchestrator` encounters a source type
that is neither PDF, WIKI, nor DATABASE, it falls back to the token-based chunking strategy, and
the ingestion layer can use Tika to extract the raw text first. This means the pipeline can ingest
Office documents and other binary formats without writing a dedicated reader.

---

### Apache POI

**What it is:** The Java library for reading and writing Microsoft Office file formats (`.xlsx`,
`.xls`, `.docx`, etc.).

**How it's used here:** `llm-rag-pipeline` uses `poi-ooxml` in its Excel ingestion path to open
workbooks, iterate over sheets and rows, and convert tabular data into Markdown-formatted tables.
Those Markdown tables are then fed into the standard chunking pipeline so spreadsheet content
becomes searchable vector chunks.

---

### Tesseract OCR (tess4j)

**What it is:** Google's open-source OCR engine, wrapped for Java by tess4j.

**How it's used here:** `llm-rag-pipeline` includes an opt-in OCR path for scanned PDFs (image
PDFs where text cannot be extracted digitally). When enabled, `PdfIngestionService` renders each
PDF page to an image with PDFBox 3.x, passes the image to Tesseract, and feeds the resulting text
into the chunking pipeline. Tesseract degrades gracefully — if the native `libtesseract` shared
library is absent at runtime, OCR is skipped and the pipeline continues with whatever text PDFBox
extracted directly.

---

### Spring Security

**What it is:** The standard security framework for Spring applications, handling authentication,
authorization, CORS, session management, and security response headers.

**How it's used here:** `llm-rag-pipeline` uses Spring Security for a stateless REST API with
API-key authentication. The `SecurityConfig` disables form login, HTTP Basic, CSRF, and sessions,
applies security headers (HSTS, frame deny, referrer policy), and installs two custom servlet
filters: `ApiKeyAuthFilter` validates the `X-API-Key` request header by SHA-256-hashing the
provided key and looking it up in PostgreSQL's `api_keys` table; `RateLimitFilter` implements an
in-memory token-bucket rate limiter (keyed by API key or client IP) that returns HTTP 429 when
exhausted. Authentication can be disabled entirely with `app.security.auth-enabled=false` for
development while keeping the CORS and rate-limiting behavior active.

---

### Micrometer + Prometheus + Grafana

**What it is:** Micrometer is a metrics facade for JVM applications; Prometheus is a time-series
database that scrapes metrics endpoints; Grafana is the visualization layer that turns Prometheus
data into dashboards.

**How it's used here:** `llm-rag-pipeline` wires full observability. `ObservabilityConfig`
registers `TimedAspect` (so `@Timed`-annotated methods become Prometheus histograms) and
`ObservedAspect` (so `@Observed` methods open distributed-tracing spans). The Actuator's
`/actuator/prometheus` endpoint is scraped by Prometheus. Custom metrics include SLO histograms
for HTTP latency (buckets at 50ms, 100ms, 200ms, 300ms, 500ms, 1s, 2s, 5s) and four
`rag.eval.*` gauges (`mrr`, `context_precision`, `precision_at_k`, `recall_at_k`) updated every
time the `RetrievalEvaluator` runs against the gold query set. A pre-built Grafana dashboard JSON
(`grafana-dashboard-rag.json`) is provisioned automatically.

---

### OpenTelemetry + Grafana Tempo

**What it is:** OpenTelemetry is the vendor-neutral standard for distributed tracing. Grafana Tempo
is a high-scale distributed-tracing backend that stores and queries OTLP traces.

**How it's used here:** `llm-rag-pipeline` uses `micrometer-tracing-bridge-otel` and
`opentelemetry-exporter-otlp` to export traces over OTLP HTTP to Tempo (configured at
`http://localhost:4318/v1/traces`). Sampling is set to 100% (`probability: 1.0`) in development.
Every `@Observed` method in the retrieval pipeline opens a span, so a single retrieval request
produces a trace that shows ingestion, vector search, post-processing, and reranking as child
spans.

---

### Loki + Logstash Logback Encoder

**What it is:** Grafana Loki is a horizontally-scalable log aggregation system; Logstash Logback
Encoder is a Logback appender that emits structured JSON logs instead of plain text.

**How it's used here:** `llm-rag-pipeline` uses `logstash-logback-encoder` to write every log
line as a JSON object (with timestamp, level, logger, thread, trace ID, and message fields) to
stdout. Loki scrapes those JSON logs, and Grafana's Loki datasource makes them queryable and
correlatable with Prometheus metrics and Tempo traces — all three signals in one dashboard.

---

### Retrieval Evaluation (MRR, P@k, R@k, RAGAS Context Precision)

**What it is:** A set of information-retrieval quality metrics: Mean Reciprocal Rank (MRR)
measures how early the first relevant result appears; Precision@k measures the fraction of top-k
results that are relevant; Recall@k measures the fraction of relevant sources found in the top k;
and RAGAS Context Precision is a variant that weights relevant chunks by their rank.

**How it's used here:** `llm-rag-pipeline`'s `RetrievalEvaluator` loads a gold query set from
`src/main/resources/eval/qrels.json` (a list of queries each paired with their known-relevant
source names), runs each query through the live `RetrievalService`, and computes all four
metrics. Results are exposed as Micrometer gauges (`rag.eval.mrr`, `rag.eval.context_precision`,
etc.) so they appear in Grafana alongside latency and throughput metrics. The evaluation endpoint
(`POST /api/evaluate`) triggers a run on demand.

---

### Lombok

**What it is:** A compile-time annotation processor that generates boilerplate Java code
(`getters`, `setters`, `constructors`, `toString`, `equals`, `hashCode`, `@Slf4j` loggers) from
annotations.

**How it's used here:** All three modules use Lombok extensively. `@RequiredArgsConstructor`
generates constructors that inject all `final` fields, eliminating Spring constructor-injection
boilerplate. `@Slf4j` adds a `log` field without a `LoggerFactory.getLogger(...)` call.
`@Data` replaces all manual getters and setters on the nine `@ConfigurationProperties` classes
(`EmbeddingCacheProperties`, `SemanticCacheProperties`, `OcrProperties`, `IngestionProperties`, etc.),
cutting roughly 120 lines of boilerplate across the pipeline module. `@Getter` covers Spring event
classes (`IngestionCompletedEvent`, `VectorsStoredEvent`) that extend `ApplicationEvent` and therefore
cannot be Java records. Command classes (`IngestCommand`, `DeleteCommand`, `IngestAllCommand`) use
`@RequiredArgsConstructor` to remove their explicit constructors. `@Getter`/`@Setter`/`@NoArgsConstructor`
remain on Neo4j `@Node` domain classes in `llm-rag-graph`. Lombok is excluded from the final JAR
(`<optional>true</optional>`) and stripped by the Spring Boot Maven plugin.

Where a class is a pure immutable data holder with no superclass, **Java records** are preferred
over Lombok — `KnowledgeRequest`, `IngestedDocument`, `IngestionJob`, `GraphContext`, and all REST
DTOs (`GenerateRequest`, `RagRequest`, `RagResponse`, `GraphStats`, …) are records.

---

### Testcontainers

**What it is:** A Java testing library that spins up real Docker containers (databases, message
brokers, etc.) for the duration of a test suite, eliminating the need for mocks or external
services in integration tests.

**How it's used here:** `llm-rag-pipeline`'s integration tests use Testcontainers to start a real
PostgreSQL 18 container and a real OpenSearch 2.17.1 container via Spring Boot's
`@ServiceConnection` support. This means the Flyway migrations run against a real database, API-key
lookups hit a real Postgres, and retrieval tests can actually store and search vectors in
OpenSearch — all without any pre-installed external services, and with automatic cleanup after
the test run.

---

### JaCoCo

**What it is:** The standard Java code-coverage measurement tool, integrated as a Maven plugin.

**How it's used here:** `llm-rag-pipeline` runs JaCoCo as part of the `verify` Maven phase. It
measures instruction coverage across the module and enforces a minimum threshold of 70%
(`COVEREDRATIO ≥ 0.70`). The build fails if coverage drops below that gate, and a human-readable
HTML report is generated in `target/site/jacoco/`. The threshold is documented in the `pom.xml`
as intentionally conservative, with a note to raise it as the test suite grows.

---

### OpenAPI / Swagger Parser

**What it is:** A static contract-validation library (Swagger Parser v3) that can parse and
validate an OpenAPI 3.x YAML/JSON file against the specification.

**How it's used here:** `llm-rag-pipeline` ships a hand-authored `src/main/resources/static/openapi.yaml`
that documents every REST endpoint. An `OpenApiContractTest` reads that YAML with the Swagger
Parser and asserts that it parses without errors, keeping the shipped contract syntactically valid
as the code evolves.

---

### Docker Compose

**What it is:** A tool for defining and running multi-container Docker applications from a single
YAML file.

**How it's used here:** Each module that needs infrastructure provides its own `docker-compose.yml`.
`llm-rag-pipeline`'s file starts PostgreSQL 18 and OpenSearch 2.17.1 (plus OpenSearch Dashboards
at port 5601). `llm-rag-graph`'s file starts Neo4j 5 with the APOC plugin enabled and also builds
and starts the application container itself (via `build: .`), so the entire graph service can be
launched with `docker compose up`. The `spring-boot-docker-compose` dev dependency lets Spring Boot
auto-start and auto-stop the Compose services when running from an IDE.

---

### git-commit-id Maven Plugin

**What it is:** A Maven plugin that reads the current git commit hash, branch, and timestamp at
build time and writes them to a `git.properties` file baked into the JAR.

**How it's used here:** All three modules include this plugin. Combined with Spring Boot Actuator's
`/actuator/info` endpoint (configured with `info.git.mode: full`), every running instance exposes
its exact git commit SHA, branch, build timestamp, and dirty flag. This makes it straightforward
to correlate a production incident with the exact code version that was deployed.
