# LLM RAG Pipeline — Spring AI Production-Grade Backend

A production-grade **Retrieval-Augmented Generation** backend built with Spring AI. It owns all
three phases of RAG: **ingestion** (turning documents into searchable vectors), **retrieval**
(ranking the most relevant chunks), and **generation** (assembling a grounded LLM answer with
citations, semantic caching, and prompt-injection defence).

> **Stack**: Spring Boot 4.1 · Spring AI 2.0.0-M8 · Java 21 · OpenAI · OpenSearch · PostgreSQL 17

---

## High-level Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          API Layer (Spring MVC)                          │
│  /api/v1/admin/lifecycle/{ingest,upload,delete}  ·  POST /api/v1/retrieve│
│  POST /api/v1/generate  ·  POST /api/v1/admin/eval/run                  │
│  Spring Security (ApiKeyAuthFilter · RateLimitFilter · CorsConfig)       │
└────────┬───────────────────────┬──────────────────────────┬─────────────┘
         │                       │                          │
         ▼                       ▼                          ▼
┌────────────────┐  ┌────────────────────────┐  ┌────────────────────────┐
│  INGESTION     │  │  RETRIEVAL             │  │  GENERATION            │
│                │  │                        │  │                        │
│ FileIngestion  │  │ QueryTransformation    │  │ SemanticCache (in-mem) │
│ PdfIngestion   │  │  NONE / REWRITE /      │  │ PromptOrchestrator     │
│ WikiIngestion  │  │  MULTI_QUERY / HYDE /  │  │   → ContextBuilder     │
│ DbIngestion    │  │  STEP_BACK             │  │   → GroundingPolicy    │
│ ExcelReader    │  │         │              │  │ PromptInjectionGuard   │
│ OcrAugmentor   │  │         ▼              │  │ ChatClient (OpenAI)    │
│ TextNormalizer │  │ SearchStrategy         │  │ GenerationEvaluator    │
│ PiiRedactor    │  │  vector / keyword /    │  │  (faithfulness +       │
│ ChunkingStrat  │  │  hybrid (RRF)          │  │   relevance — RAG Triad│
│ ChunkEnricher  │  │         │              │  │   via Spring AI eval)  │
│         │      │  │         ▼              │  └──────────┬─────────────┘
│         ▼      │  │ PostProcessor chain    │             │
│ EmbeddingCache │  │  BusinessRuleFilter    │             │
│ VectorStore    │  │  LengthFilter          │    ┌────────▼─────────────┐
│ (OpenSearch)   │  │  NearDuplicateFilter   │    │  EVALUATION          │
│ PostgreSQL     │  │  RerankingPostProc     │    │  RetrievalEvaluator  │
│ (lifecycle log)│  │   (6 strategies)       │    │   MRR / P@k / R@k /  │
└────────────────┘  │  ScoreAwareRanker      │    │   nDCG / HitRate /   │
                    │  MmrDiversityProcessor │    │   ContextPrecision   │
                    └────────────────────────┘    │  GenerationEvaluator │
                                                  │   Faithfulness /     │
                                                  │   Relevance (LLM)    │
                                                  └──────────────────────┘
```

---

## Request-flow Sequence Diagrams

### Ingestion (once — build the knowledge base)

```
Client                LifecycleController       IngestionOrchestrator
  │                          │                           │
  │  POST /lifecycle/ingest  │                           │
  │─────────────────────────►│                           │
  │                          │──── orchestrate() ───────►│
  │                          │                           │── read (PDF/Wiki/DB/Excel/…)
  │                          │                           │── TextNormalizer.normalize()
  │                          │                           │      └── PiiRedactor.redact()
  │                          │                           │── content-hash dedup check
  │                          │                           │── ChunkingStrategy.chunk()
  │                          │                           │      (fixed|recursive|token|
  │                          │                           │       semantic|markdown|llm)
  │                          │                           │── ChunkEnricher (opt-in)
  │                          │                           │── EmbeddingCacheService.embed()
  │                          │                           │── VectorStore.add() [batched]
  │                          │                           │── IngestionLogRepository.save()
  │◄─────────────────────────│◄──────────────────────────│
  │       204 No Content     │                           │
```

### Retrieval (`POST /api/v1/retrieve`)

```
Client           RetrievalController      RetrievalService          OpenSearch
  │                      │                       │                       │
  │  POST /retrieve      │                       │                       │
  │  {"query":"…","topK"}│                       │                       │
  │─────────────────────►│                       │                       │
  │                      │──── retrieve() ──────►│                       │
  │                      │                       │── QueryTransformation  │
  │                      │                       │   (REWRITE|MULTI_QUERY │
  │                      │                       │    |HYDE|STEP_BACK)    │
  │                      │                       │── SearchStrategy ─────►│
  │                      │                       │   (vector|keyword|     │ kNN / BM25
  │                      │                       │    hybrid)  ◄──────────│ / RRF
  │                      │                       │── BusinessRuleFilter   │
  │                      │                       │── LengthFilter         │
  │                      │                       │── NearDuplicateFilter  │
  │                      │                       │── RerankingPostProc    │
  │                      │                       │   (cross-encoder|      │
  │                      │                       │    bi-encoder|llm-pw|  │
  │                      │                       │    llm-lw|bm25|rrf)    │
  │                      │                       │── ScoreAwareRanker     │
  │                      │                       │── MmrDiversityProc     │
  │                      │                       │── toCitations()        │
  │◄─────────────────────│◄──────────────────────│                       │
  │  {chunks[], citations[]}                      │                       │
```

### Generation (`POST /api/v1/generate`)

```
Client         GenerationController   SemanticCache   PromptOrchestrator   ChatClient (LLM)
  │                    │                    │                  │                    │
  │  POST /generate    │                    │                  │                    │
  │  {"query":"…"}     │                    │                  │                    │
  │───────────────────►│                    │                  │                    │
  │                    │── get(query) ─────►│                  │                    │
  │                    │◄── hit? ───────────│                  │                    │
  │◄───────────────────│   (return cached)  │                  │                    │
  │  [cache hit path]  │                    │                  │                    │
  │                    │                    │                  │                    │
  │                    │── build(query,k) ─────────────────────►│                   │
  │                    │                    │  retrieve chunks  │                   │
  │                    │                    │  build context    │                   │
  │                    │                    │  grounding rules  │                   │
  │                    │◄────────────────────────────────────── │                   │
  │                    │── PromptInjectionGuard.filter(chunks)  │                   │
  │                    │   (remove malicious context)           │                   │
  │                    │── prompt().system().user().call() ────────────────────────►│
  │                    │                    │                   │  LLM generates    │
  │                    │◄────────────────────────────────────────────────────────── │
  │                    │── GenerationEvaluator.isFaithful() (opt-in)               │
  │                    │── SemanticCache.put(query, answer)                         │
  │◄───────────────────│                    │                   │                   │
  │ {answer, citations[], faithful, fromCache}
```

---

## Package Map (`com.org.*`)

| Package                  | Key classes                                                                                                                                                             | Responsibility                                                    |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| `controller/`            | `RetrievalController`, `GenerationController`, `LifecycleController`, `EvaluationController`                                                                            | REST endpoints                                                    |
| `ingestion/`             | `IngestionOrchestrator`, `FileIngestionService`, `PdfIngestionService`, `WikiIngestionService`, `DatabaseIngestionService`                                              | Document loading & normalising                                    |
| `ingestion/reader/`      | `DocumentReaderFactory`                                                                                                                                                 | Extension → reader mapping (PDF / MD / TXT / JSON / Excel / Tika) |
| `ingestion/ocr/`         | `OcrService`, `OcrPdfAugmentor`                                                                                                                                         | Tesseract OCR for scanned PDFs (opt-in)                           |
| `ingestion/excel/`       | `ExcelDocumentReader`                                                                                                                                                   | Apache POI workbook → Markdown tables                             |
| `chunking/strategy/`     | `FixedSizeChunkingStrategy`, `RecursiveChunkingStrategy`, `TokenChunkingStrategy`, `SemanticChunkingStrategy`, `MarkdownSectionChunkingStrategy`, `LlmChunkingStrategy` | 6 pluggable chunking algorithms                                   |
| `chunking/`              | `ChunkingOrchestrator`, `ChunkingStrategyFactory`                                                                                                                       | Factory + orchestration                                           |
| `enrichment/`            | `ChunkEnricher`                                                                                                                                                         | LLM keyword / summary enrichment (opt-in)                         |
| `vectorstore/`           | `ChunkVectorStoreService`                                                                                                                                               | Parallel batched writes to OpenSearch                             |
| `retrieval/`             | `RetrievalService`                                                                                                                                                      | Top-level retrieve + citation assembly                            |
| `retrieval/transform/`   | `HydeQueryTransformer`, `MultiQueryExpanderImpl`, `RewriteQueryTransformerImpl`, `StepBackQueryTransformer`                                                             | Pre-retrieval query transformation                                |
| `retrieval/search/`      | `VectorSearchStrategy`, `KeywordSearchStrategy`, `HybridSearchStrategy`                                                                                                 | First-stage candidate fetch                                       |
| `retrieval/postprocess/` | `BusinessRuleFilter`, `LengthFilter`, `NearDuplicateFilter`, `RetrievalPostProcessor`, `ScoreAwareRanker`, `MmrDiversityProcessor`                                      | Ordered post-processing chain                                     |
| `retrieval/rerank/`      | `CrossEncoderReranker`, `BiEncoderReranker`, `LlmPointwiseReranker`, `LlmListwiseReranker`, `Bm25Reranker`, `RrfFusionReranker`                                         | 6 second-stage rerankers                                          |
| `generation/`            | `GenerationService`, `PromptOrchestrator`, `ContextBuilder`, `GroundingPolicy`                                                                                          | Full RAG generation pipeline                                      |
| `cache/`                 | `EmbeddingCacheService`, `SemanticCacheService`                                                                                                                         | LRU+TTL embedding cache · semantic answer cache                   |
| `security/`              | `ApiKeyAuthFilter`, `ApiKeyService`, `RateLimitFilter`, `SecurityConfig`                                                                                                | API-key auth + rate limiting                                      |
| `security/pii/`          | `PiiRedactor`                                                                                                                                                           | Regex PII redaction before embedding                              |
| `security/`              | `PromptInjectionGuard`                                                                                                                                                  | Strips injection-payload chunks from context                      |
| `eval/`                  | `RetrievalEvaluator`, `GenerationEvaluator`, `RetrievalMetrics`                                                                                                         | MRR / P@k / R@k / nDCG / faithfulness / relevance                 |
| `lifecycle/`             | `KnowledgeLifecycleService`, `IngestionLogRepository`                                                                                                                   | Content-hash dedup + delete                                       |
| `common/`                | `CircuitBreaker`, `Resilience`                                                                                                                                          | Retry + circuit breaker (used by reranking)                       |
| `config/`                | `ChatClientConfig`, `ObservabilityConfig`, `StartupValidator`                                                                                                           | Bean wiring + validation                                          |
| `web/`                   | `GlobalExceptionHandler`, `ApiError`                                                                                                                                    | Structured error responses                                        |

---

## REST API

| Method   | Path                                 | Auth | Description                                                 |
|----------|--------------------------------------|------|-------------------------------------------------------------|
| `POST`   | `/api/v1/retrieve`                   | key  | Retrieve ranked chunks + citations for a query              |
| `POST`   | `/api/v1/generate`                   | key  | Full RAG: retrieve → guard → augment → generate             |
| `POST`   | `/api/v1/admin/lifecycle/ingest`     | key  | Ingest a named knowledge source                             |
| `POST`   | `/api/v1/admin/lifecycle/ingest-all` | key  | Ingest all bundled sources                                  |
| `POST`   | `/api/v1/admin/lifecycle/upload`     | key  | Upload + ingest a file (multipart)                          |
| `DELETE` | `/api/v1/admin/lifecycle/delete`     | key  | Delete a knowledge source                                   |
| `DELETE` | `/api/v1/admin/lifecycle/delete-all` | key  | Delete all knowledge                                        |
| `POST`   | `/api/v1/admin/eval/run?k=10`        | key  | Retrieval-quality evaluation (MRR, P@k, R@k, nDCG, HitRate) |
| `POST`   | `/api/v1/admin/eval/run/generation`  | key  | Generation-quality evaluation (faithfulness + relevance)    |
| `GET`    | `/actuator/health`                   | open | Liveness / readiness probes                                 |
| `GET`    | `/actuator/prometheus`               | open | Micrometer metrics scrape                                   |

---

## Configuration Reference

All knobs are in `application.yml` under the `app:` prefix.

### Chunking

```yaml
app:
  chunking:
    strategy: auto        # auto | fixed | recursive | token | semantic | markdown | llm
    max-chars: 1000       # fixed / recursive / semantic
    overlap: 150
    token:
      chunk-size: 800     # tokens
    semantic:
      threshold: 0.8      # cosine similarity breakpoint
      max-chars: 1500
```

### Retrieval & Query Transformation

```yaml
app:
  retrieval:
    default-top-k: 10
    over-fetch-factor: 3
    search:
      mode: vector          # vector | keyword | hybrid
    query-transform:
      mode: NONE            # NONE | REWRITE | MULTI_QUERY | HYDE | STEP_BACK
      multi-query-count: 3
    dedup:
      enabled: true
      threshold: 0.95
    mmr:
      enabled: false
      lambda: 0.7
    rerank:
      enabled: false
      strategy: cross-encoder   # cross-encoder | bi-encoder | llm-pointwise | llm-listwise | bm25 | rrf
      top-n: 0
      min-score: 0.0
      cache:
        enabled: true
        ttl: 10m
      breaker:
        failure-threshold: 3
        cooldown: 30s
```

### Generation

```yaml
app:
  generation:
    enabled: false            # expose POST /api/v1/generate
    mode: manual              # manual (hand-built prompt) | advisor (Spring AI QuestionAnswerAdvisor)
    top-k: 5
    include-citations: true
    evaluate-faithfulness: false   # one extra LLM call per generation
```

### Caching

```yaml
app:
  cache:
    embedding:
      enabled: true           # LRU+TTL cache for text→vector (avoids re-embedding)
      max-size: 5000
      ttl: 24h
    semantic:
      enabled: false          # cache query→answer for FAQ-style traffic
      max-size: 500
      ttl: 30m
      similarity-threshold: 0.95
```

### Security

```yaml
app:
  security:
    auth-enabled: false       # set true in prod
    header: X-API-Key
    rate-limit:
      enabled: true
      capacity: 120
      refill-per-minute: 120
    pii:
      enabled: false          # redact email / phone / SSN / CC / IP before embedding
      replacement: "[REDACTED]"
```

---

## Design Patterns

| Pattern                     | Where                                                                                                                                           | Why                                               |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| **Strategy**                | `ChunkingStrategy` (6 chunking algorithms) · `SearchStrategy` (3 search modes) · `Reranker` (6 rerankers) · `QueryTransformer` (4 transformers) | Swap algorithm at runtime via config              |
| **Chain of Responsibility** | `RetrievalPostProcessor` chain (filter → dedup → rerank → rank → MMR)                                                                           | Each stage handles and forwards                   |
| **Factory**                 | `ChunkingStrategyFactory` · `DocumentReaderFactory`                                                                                             | Name/extension → implementation                   |
| **Template Method**         | `AbstractChunkingStrategy`                                                                                                                      | Shared skeleton; subclasses supply the split step |
| **Composite**               | `HybridSearchStrategy`                                                                                                                          | Composes vector + keyword + RRF                   |
| **Proxy (protection)**      | `RerankingPostProcessor` wrapping `Reranker`                                                                                                    | Adds circuit breaker, cache, cost cap, metrics    |
| **Facade**                  | `IngestionOrchestrator` · `PromptOrchestrator`                                                                                                  | One entry point over multi-step pipelines         |
| **Adapter**                 | `ExcelDocumentReader` · `OcrPdfAugmentor`                                                                                                       | Bridge third-party APIs into the document model   |

---

## Getting Started

### Prerequisites

- Java 21+, Maven
- Docker & Docker Compose
- An OpenAI API key (embeddings + generation)

### 1 — Start infrastructure

```bash
docker compose up -d
# Postgres :5432 · OpenSearch :9200 · OpenSearch Dashboards :5601
```

### 2 — Configure

```bash
export OPENAI_API_KEY=sk-...
# Optional: enable generation endpoint
export GENERATION_ENABLED=true
```

### 3 — Run

```bash
mvn spring-boot:run
# Listening on :8081
```

### 4 — Ingest sample knowledge

```bash
curl -X POST http://localhost:8081/api/v1/admin/lifecycle/ingest-all
```

### 5 — Retrieve

```bash
curl -X POST http://localhost:8081/api/v1/retrieve \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the annual leave policy?", "topK": 5}'
```

### 6 — Generate (requires `GENERATION_ENABLED=true`)

```bash
curl -X POST http://localhost:8081/api/v1/generate \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the annual leave policy?"}'
```

Response includes `answer`, `citations[]`, `faithful` (null unless `evaluate-faithfulness=true`),
and `fromSemanticCache`.

---

## API-key Authentication

`/api/**` is protected when `app.security.auth-enabled=true`. Keys are SHA-256 digests stored in
the `api_keys` PostgreSQL table:

```bash
raw=$(openssl rand -hex 32)
hash=$(printf "%s" "$raw" | shasum -a 256 | cut -d' ' -f1)
psql ... -c "INSERT INTO api_keys (key_hash, label) VALUES ('$hash', 'my-client');"
echo "X-API-Key: $raw"
```

---

## Build & Test

```bash
# Unit tests only (no Docker required)
mvn test -Dtest="*Test" -Djacoco.skip=true

# Full suite including Testcontainers integration tests (needs Docker)
mvn verify
```

Unit tests use Mockito and run fully offline. Integration tests (`@SpringBootTest`) spin up real
Postgres + OpenSearch via Testcontainers. JaCoCo enforces ≥70% instruction coverage on `mvn verify`.

---

## Project Structure

```
llm-rag-pipeline/
├── pom.xml
├── docker-compose.yml               # Postgres, OpenSearch, observability stack
├── observability/                   # Grafana dashboards, Prometheus config, Loki, Tempo
└── src/main/java/com/org/
    ├── controller/                  # REST controllers
    ├── ingestion/                   # Readers, normaliser, OCR, Excel, orchestration
    ├── chunking/strategy/           # 6 chunking strategies + factory
    ├── enrichment/                  # LLM keyword / summary enrichment
    ├── vectorstore/                 # Batched OpenSearch writes
    ├── retrieval/
    │   ├── transform/               # Query transformation (HyDE, multi-query, rewrite, step-back)
    │   ├── search/                  # Vector / keyword / hybrid search
    │   ├── postprocess/             # Ordered filter + ranking chain
    │   └── rerank/                  # 6 reranker implementations
    ├── generation/                  # Prompt orchestration + LLM generation
    ├── cache/                       # Embedding cache + semantic answer cache
    ├── security/                    # API-key auth, rate limit, PII redactor, injection guard
    ├── eval/                        # Retrieval metrics + generation RAG Triad evaluation
    ├── lifecycle/                   # Content-hash dedup + delete
    ├── common/                      # Circuit breaker, retry
    ├── config/                      # Spring beans, observability, startup validation
    └── web/                         # Global exception handler, validation
```

## Service Ports

| Service               | Port               |
|-----------------------|--------------------|
| RAG application       | 8081               |
| PostgreSQL            | 5432               |
| OpenSearch            | 9200 / 9600        |
| OpenSearch Dashboards | 5601               |
| Prometheus            | 9090               |
| Grafana               | 3000               |
| Tempo                 | 3200 / 4317 / 4318 |
| Loki                  | 3100               |
