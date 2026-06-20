# LLM RAG Pipeline — Spring AI Production-Grade Backend

A production-grade **Retrieval-Augmented Generation** backend built with Spring AI covering all three RAG phases:

- **Ingestion** — turning documents into searchable vectors, triggered either via the REST API or
  automatically by the **inbox scheduler**, which polls a drop-folder (`app.ingestion.inbox.path`)
  on a fixed delay and ingests any new file it finds
- **Retrieval** — ranking the most relevant chunks
- **Generation** — assembling a grounded LLM answer with citations, semantic caching, and prompt-injection defence

> **Stack**: Spring Boot 4.1 · Spring AI 2.0.0 · Java 25 · OpenAI · OpenSearch · MongoDB · Redis · PostgreSQL 17

Chunks are dual-written at ingestion: **OpenSearch** holds the vector + filter fields + `chunkId`
(search index only), **MongoDB** holds the full chunk text + descriptive metadata keyed by
`chunkId` (the system of record for content). Retrieval searches OpenSearch for `chunkId`s, then
**hydrates** the real text from Mongo before reranking. **Redis** caches a content hash per chunk
so re-ingesting unchanged content is skipped without a database round-trip.

---

## High-level Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          API Layer (Spring MVC)                          │
│  /api/v1/admin/lifecycle/{ingest,upload,delete}  ·  POST /api/v1/retrieve│
│  POST /api/v1/generate  ·  POST /api/v1/admin/eval/run                  │
│  Spring Security (ApiKeyAuthFilter · RateLimitFilter · CorsConfig)       │
│  RequestLoggingInterceptor (every request, no per-controller boilerplate)│
└────────┬───────────────────────┬──────────────────────────┬─────────────┘
         │                       │                          │
┌────────┴─────────┐             │                          │
│ InboxScheduler    │             │                          │
│ @Scheduled poll   │             │                          │
│ of drop-folder    │             │                          │
│ (app.ingestion.   │             │                          │
│  inbox.*) — feeds │             │                          │
│  INGESTION too    │             │                          │
└────────┬──────────┘             │                          │
         ▼                       ▼                          ▼
┌────────────────┐  ┌────────────────────────┐  ┌────────────────────────┐
│  INGESTION     │  │  RETRIEVAL             │  │  GENERATION            │
│                │  │                        │  │                        │
│ FileIngestion  │  │ QueryTransformation    │  │ SemanticCache (in-mem) │
│ PdfIngestion   │  │  NONE / REWRITE /      │  │ PromptOrchestrator     │
│ WikiIngestion  │  │  MULTI_QUERY / HYDE /  │  │   → ContextBuilder     │
│ DbIngestion    │  │  STEP_BACK             │  │   → GroundingPolicy    │
│ ExcelReader    │  │         │              │  │ PromptInjectionGuard   │
│ OcrAugmentor   │  │         ▼              │  │ ContextSufficiencyJudge│
│ TextNormalizer │  │ SearchStrategy         │  │  (LLM-as-judge, pre-gen│
│ PiiRedactor    │  │  vector / keyword /    │  │   — skips generation if│
│ ChunkingStrat  │  │  hybrid (RRF)          │  │   context insufficient)│
│  + LLM-based   │  │  → OpenSearch returns  │  │ ChatClient (OpenAI)    │
│  classifier    │  │    chunkId candidates  │  │ GenerationEvaluator    │
│    (auto mode) │  │         │              │  │  (faithfulness +       │
│ ChunkDedupSvc  │  │         ▼              │  │   relevance — RAG Triad│
│  (Redis hash,  │  │ ChunkHydrationService  │  │   via Spring AI eval)  │
│   per-chunk)   │  │  → fetch text+metadata │  └──────────┬─────────────┘
│ ChunkEnricher  │  │    from Mongo by       │             │
│         │      │  │    chunkId             │             │
│         ▼      │  │         │              │             │
│ EmbeddingCache │  │         ▼              │    ┌────────▼─────────────┐
│ ChunkVectorStoreService    │  │ PostProcessor chain    │    │  EVALUATION          │
│  ├─ MongoDB    │  │  BusinessRuleFilter    │    │  RetrievalEvaluator  │
│  │  (chunk text│  │  LengthFilter          │    │   MRR / P@k / R@k /  │
│  │  + metadata,│  │  NearDuplicateFilter   │    │   nDCG / HitRate /   │
│  │  keyed by   │  │  RerankingPostProc     │    │   ContextPrecision   │
│  │  chunkId)   │  │   (6 strategies)       │    │  GenerationEvaluator │
│  └─ OpenSearch │  │  ScoreAwareRanker      │    │   Faithfulness /     │
│     (vector +  │  │  MmrDiversityProcessor │    │   Relevance (LLM)    │
│     filters +  │  └────────────────────────┘    └──────────────────────┘
│     chunkId)   │
│ PostgreSQL     │
│ (lifecycle log)│
└────────────────┘
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
  │                          │                           │── ChunkingOrchestrator.chunk()
  │                          │                           │      DB → whole-row (structural)
  │                          │                           │      auto + classifier.enabled →
  │                          │                           │        ChunkingStrategyClassifier
  │                          │                           │        (LLM picks fixed|recursive|
  │                          │                           │         token|semantic|markdown,
  │                          │                           │         falls back to heuristic)
  │                          │                           │── per-chunk dedup:
  │                          │                           │      ChunkDedupService.isNewContent()
  │                          │                           │      (SHA-256 → Redis SETNX+TTL;
  │                          │                           │       duplicate chunks are skipped)
  │                          │                           │── ChunkEnricher (opt-in)
  │                          │                           │── EmbeddingCacheService.embed()
  │                          │                           │── ChunkVectorStoreService.store():
  │                          │                           │      1. MongoDB upsert (chunkId →
  │                          │                           │         full text + metadata)
  │                          │                           │      2. OpenSearch add() [batched]
  │                          │                           │         (vector + filters + chunkId)
  │◄─────────────────────────│◄──────────────────────────│
  │       204 No Content     │                           │
```

### Scheduled ingestion (inbox watcher — no client request)

`InboxScheduler` runs independently of the REST API, on a fixed delay
(`app.ingestion.inbox.poll-interval`, default 30s). Drop a file into the watched folder
(`app.ingestion.inbox.path`) and it's ingested automatically — no `POST /lifecycle/ingest` needed.

The scan is guarded by **ShedLock** (`@SchedulerLock`, backed by Redis via `SchedulerLockConfig`),
so when the app is scaled to multiple instances only one of them runs `scan()` at a time — without
it, every instance would poll and ingest from the same shared folder concurrently.

```
InboxScheduler                  DocumentReaderFactory      FileIngestionService
      │                                  │                          │
      │ @Scheduled scan() every 30s      │                          │
      │── list inbox/ (skip files newer than min-age — still being written)
      │── readerFactory.supports(name)? ►│                          │
      │   no  → move to inbox/failed/    │                          │
      │   yes │                          │                          │
      │───────┼── ingestFile(file, name) ───────────────────────────►│
      │       │                          │   (same clean→chunk→dedup→store
      │       │                          │    pipeline as the REST path)
      │◄──────┼──────────────────────────┼──────────────────────────│
      │── success → move to inbox/processed/
      │── failure → move to inbox/failed/
```

### Retrieval (`POST /api/v1/retrieve`)

```
Client           RetrievalController      RetrievalService      OpenSearch      MongoDB
  │                      │                       │                  │              │
  │  POST /retrieve      │                       │                  │              │
  │  {"query":"…","topK"}│                       │                  │              │
  │─────────────────────►│                       │                  │              │
  │                      │──── retrieve() ──────►│                  │              │
  │                      │                       │── QueryTransformation             │
  │                      │                       │   (REWRITE|MULTI_QUERY            │
  │                      │                       │    |HYDE|STEP_BACK)               │
  │                      │                       │── SearchStrategy ───────────────►│              │
  │                      │                       │   (vector|keyword|    │ kNN/BM25/RRF over        │
  │                      │                       │    hybrid)  ◄────────│ vector+filters+chunkId    │
  │                      │                       │── toChunk() (chunkId in metadata) │
  │                      │                       │── ChunkHydrationService.hydrate() │
  │                      │                       │   findByIds(chunkIds) ──────────────────────────►│
  │                      │                       │   ◄──────────────────────────────────────────────│
  │                      │                       │   (replaces text with Mongo's copy; falls back   │
  │                      │                       │    to OpenSearch-carried text if Mongo misses)   │
  │                      │                       │── BusinessRuleFilter              │              │
  │                      │                       │── LengthFilter                    │              │
  │                      │                       │── NearDuplicateFilter (on hydrated text)          │
  │                      │                       │── RerankingPostProc (on hydrated text)            │
  │                      │                       │   (cross-encoder|bi-encoder|llm-pw|llm-lw|bm25|rrf)│
  │                      │                       │── ScoreAwareRanker                │              │
  │                      │                       │── MmrDiversityProc                │              │
  │                      │                       │── toCitations()                   │              │
  │◄─────────────────────│◄──────────────────────│                                   │              │
  │  {chunks[], citations[]}                      │                                   │              │
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
  │                    │── ContextSufficiencyJudge.isSufficient(query, context)     │
  │                    │   (LLM-as-judge — SUFFICIENT/INSUFFICIENT)                 │
  │                    │   ├─ INSUFFICIENT → return canned response, skip LLM call  │
  │                    │   └─ SUFFICIENT   → continue                               │
  │                    │── prompt().system().user().call() ────────────────────────►│
  │                    │                    │                   │  LLM generates    │
  │                    │◄────────────────────────────────────────────────────────── │
  │                    │── GenerationEvaluator.isFaithful() (opt-in)               │
  │                    │── SemanticCache.put(query, answer)                         │
  │◄───────────────────│                    │                   │                   │
  │ {answer, citations[], faithful, fromCache, insufficientContext}
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
| `ingestion/inbox/`       | `InboxScheduler`, `InboxProperties`                                                                                                                                     | `@Scheduled` (+ ShedLock `@SchedulerLock`) drop-folder watcher — auto-ingests new files, no API call needed, safe to scale to multiple instances |
| `chunking/strategy/`     | `FixedSizeChunkingStrategy`, `RecursiveChunkingStrategy`, `TokenChunkingStrategy`, `SemanticChunkingStrategy`, `MarkdownSectionChunkingStrategy`, `LlmChunkingStrategy`, `ChunkingStrategyClassifier` | 6 pluggable chunking algorithms + opt-in LLM strategy selector (`auto` mode) |
| `chunking/`              | `ChunkingOrchestrator`, `ChunkingStrategyFactory`, `ChunkIdGenerator`                                                                                                   | Factory + orchestration + deterministic chunkId derivation         |
| `enrichment/`            | `ChunkEnricher`                                                                                                                                                         | LLM keyword / summary enrichment (opt-in)                         |
| `mongo/`                 | `ChunkDocument`, `ChunkDocumentRepository`                                                                                                                              | Per-chunk text + metadata store, keyed by chunkId (system of record for content) |
| `vectorstore/`           | `ChunkVectorStoreService`, `VectorStoreWriteProperties`                                                                                                                  | Dual-write: MongoDB (text+metadata) then OpenSearch (vector+filters+chunkId), batched by chunk count + token count |
| `retrieval/`             | `RetrievalService`, `ChunkHydrationService`                                                                                                                             | Top-level retrieve + Mongo hydration by chunkId + citation assembly |
| `retrieval/transform/`   | `HydeQueryTransformer`, `MultiQueryExpanderImpl`, `RewriteQueryTransformerImpl`, `StepBackQueryTransformer`                                                             | Pre-retrieval query transformation                                |
| `retrieval/search/`      | `VectorSearchStrategy`, `KeywordSearchStrategy`, `HybridSearchStrategy`                                                                                                 | First-stage candidate fetch                                       |
| `retrieval/postprocess/` | `BusinessRuleFilter`, `LengthFilter`, `NearDuplicateFilter`, `RetrievalPostProcessor`, `ScoreAwareRanker`, `MmrDiversityProcessor`                                      | Ordered post-processing chain                                     |
| `retrieval/rerank/`      | `CrossEncoderReranker`, `BiEncoderReranker`, `LlmPointwiseReranker`, `LlmListwiseReranker`, `Bm25Reranker`, `RrfFusionReranker`                                         | 6 second-stage rerankers                                          |
| `generation/`            | `GenerationService`, `PromptOrchestrator`, `ContextBuilder`, `GroundingPolicy`                                                                                          | Full RAG generation pipeline                                      |
| `cache/`                 | `EmbeddingCacheService`, `SemanticCacheService`, `ChunkDedupService`                                                                                                    | LRU+TTL embedding cache · semantic answer cache · Redis chunk-hash dedup |
| `security/`              | `ApiKeyAuthFilter`, `ApiKeyService`, `RateLimitFilter`, `SecurityConfig`                                                                                                | API-key auth + rate limiting                                      |
| `security/pii/`          | `PiiRedactor`                                                                                                                                                           | Regex PII redaction before embedding                              |
| `security/`              | `PromptInjectionGuard`                                                                                                                                                  | Strips injection-payload chunks from context                      |
| `eval/`                  | `RetrievalEvaluator`, `GenerationEvaluator`, `RetrievalMetrics`, `ContextSufficiencyJudge`                                                                              | MRR / P@k / R@k / nDCG / faithfulness / relevance / pre-gen context-sufficiency judge |
| `lifecycle/`             | `KnowledgeLifecycleService`, `IngestionLogRepository`                                                                                                                   | Orchestration (chunk → per-chunk Redis dedup → store) + delete cleanup |
| `common/`                | `CircuitBreaker`, `Resilience`                                                                                                                                          | Retry + circuit breaker (used by reranking)                       |
| `config/`                | `ChatClientConfig`, `ObservabilityConfig`, `StartupValidator`, `SchedulerLockConfig`, `VectorStoreWriteConfig`                                                          | Bean wiring + validation + ShedLock Redis lock provider + embedding batching strategy |
| `web/`                   | `GlobalExceptionHandler`, `ApiError`, `RequestIdFilter`, `RequestLoggingInterceptor`, `WebConfig`                                                                       | Structured error responses + request-ID correlation + cross-cutting request/response logging (no per-controller boilerplate) |

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

### Data stores

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/llm-rag
    redis:
      host: localhost
      port: 6379
```

### Ingestion (inbox watcher)

```yaml
app:
  ingestion:
    inbox:
      enabled: true              # set false to disable the scheduler — REST ingest still works
      path: /path/to/drop-folder # watched for new files
      poll-interval: 30s
      min-age: 5s                # skip files modified more recently than this (still being written)
```

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
    classifier:
      enabled: false       # LLM picks the strategy under strategy=auto (opt-in; DB sources always
                            # use whole-row chunking regardless, never classifier-selected)
      sample-chars: 2000   # chars of document content sent to the LLM
```

### Vector store writes & embedding batching

```yaml
app:
  vectorstore:
    write:
      batch-size: 50               # chunks per vectorStore.add() call
      concurrency: 4                # parallel write threads
      queue-capacity: 64
      embedding-model-id: text-embedding-3-small
      embedding-dimension: 1536
      max-tokens-per-batch: 8191    # per-request token ceiling for the embedding vendor (OpenAI default)
      token-reserve-percentage: 0.1 # safety margin subtracted from the ceiling above
```

`ChunkVectorStoreService` batches by **chunk count** (`batch-size`); within each of those calls,
Spring AI's `TokenCountBatchingStrategy` bean (`VectorStoreWriteConfig.batchingStrategy`) splits
further by **token count** so a single embedding API call never exceeds `max-tokens-per-batch` —
lower it if you switch to an embedding vendor with a smaller per-request limit than OpenAI's 8191.

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
    judge:
      enabled: true                # pre-generation LLM-as-judge; skips the final LLM call and
                                    # returns insufficient-answer when context is judged insufficient
      insufficient-answer: "I don't have enough information in the available context to answer that question."
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
    chunk-dedup:
      enabled: true           # skip writing a chunk whose content hash is already in Redis
      key-prefix: "chunkhash:"
      ttl: 720h               # 30 days; re-ingested chunk after this is re-embedded once
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

## Query Transformation Modes

Before retrieval, the raw query can be rewritten to improve recall. Controlled by `app.retrieval.query-transform.mode`:

| Mode          | Class                         | Mechanism                                                                                                                   | Best for                                               |
|---------------|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------|
| `NONE`        | —                             | Query passed through unchanged                                                                                              | Fast lookups, exact keyword queries                    |
| `REWRITE`     | `RewriteQueryTransformerImpl` | LLM rephrases the query (grammar fix, abbreviation expansion)                                                               | Conversational or ambiguous queries                    |
| `MULTI_QUERY` | `MultiQueryExpanderImpl`      | LLM generates N alternative phrasings (default 3); all N result sets are merged and deduplicated                            | Broad topics where one phrasing misses relevant chunks |
| `HYDE`        | `HydeQueryTransformer`        | LLM generates a *hypothetical* answer passage; that passage's embedding is used as the retrieval key (not the query itself) | Sparse corpora, highly technical questions             |
| `STEP_BACK`   | `StepBackQueryTransformer`    | LLM reformulates the query at a higher abstraction level before retrieving                                                  | Multi-hop or overly specific queries                   |

**HyDE mechanism in detail:**

```
User query: "How many days notice for resignation?"
         │
         ▼  ChatClient call
Hypothetical passage: "Employees must give at least two weeks' notice per section 4.3..."
         │
         ▼  EmbeddingModel
Hypothetical vector ──► similarity search ──► actual policy chunks
```

The passage is never returned to the user — only its embedding is used as the retrieval key.

---

## Chunking Strategies

Six `ChunkingStrategy` implementations, selected via `app.chunking.strategy`:

| Strategy                          | Config value | Split logic                                                                                                     | Ideal content                                            |
|-----------------------------------|--------------|-----------------------------------------------------------------------------------------------------------------|----------------------------------------------------------|
| `FixedSizeChunkingStrategy`       | `fixed`      | Splits at character boundary every `max-chars` with `overlap` carry-over                                        | Simple text with uniform density                         |
| `RecursiveChunkingStrategy`       | `recursive`  | Tries `\n\n` → `\n` → `.` → ` ` until chunk fits; preserves natural boundaries                                  | General prose documents                                  |
| `TokenChunkingStrategy`           | `token`      | Splits on tiktoken token count (`chunk-size` tokens)                                                            | Any LLM-bound content; correct for context-window limits |
| `SemanticChunkingStrategy`        | `semantic`   | Embeds each sentence; splits where cosine similarity drops below `threshold`; merges similar adjacent sentences | Long-form content where topic boundaries matter          |
| `MarkdownSectionChunkingStrategy` | `markdown`   | Splits on `#`/`##`/`###` headings; each section is one chunk                                                    | Structured documentation, wikis                          |
| `LlmChunkingStrategy`             | `llm`        | LLM identifies natural segment boundaries; most accurate, highest cost                                          | Complex specs, mixed-format documents                    |

`auto` picks `markdown` for `.md` sources, `token` for database rows, and `recursive` for everything else.

---

## Reranking Strategies

A second-stage reranker re-scores candidates after first-stage retrieval. Controlled by
`app.retrieval.rerank.strategy` (requires `rerank.enabled: true`):

| Strategy        | `isCostly()` | Mechanism                                                                            | Latency  | Best for                                                  |
|-----------------|--------------|--------------------------------------------------------------------------------------|----------|-----------------------------------------------------------|
| `cross-encoder` | `true`       | Jointly encodes (query, chunk) via a cross-encoder model; most accurate              | Medium   | When precision matters more than throughput               |
| `bi-encoder`    | `false`      | Re-embeds query + chunks separately, scores by cosine; faster but lower accuracy     | Low      | High-throughput retrieval                                 |
| `llm-pointwise` | `true`       | Each chunk scored individually by LLM (0–100 scale); N parallel virtual-thread calls | High     | Authoritative answers where one misranked chunk is costly |
| `llm-listwise`  | `true`       | All candidates sent to LLM in one prompt; fewer API calls than pointwise             | High     | Final precision pass for generation endpoints             |
| `bm25`          | `false`      | Re-scores vector-retrieved candidates by BM25 term frequency in-process              | Very low | Keyword-heavy technical queries as cheap second pass      |
| `rrf`           | `false`      | Reciprocal Rank Fusion (k=60) merges vector + keyword rank lists                     | Very low | Default safe choice with hybrid search                    |

`RerankingPostProcessor` wraps any reranker behind a Resilience4j circuit breaker, a score cache (`rerank.cache.ttl`),
and a cost-cap guard (`isCostly()` strategies bypass automatically when the circuit is open).

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

- Java 25, Maven
- Docker & Docker Compose
- An OpenAI API key (embeddings + generation)

### 1 — Start infrastructure

```bash
docker compose up -d
# Postgres :5432 · OpenSearch :9200 · OpenSearch Dashboards :5601
# MongoDB :27017 · Mongo Express :8082 · Redis :6379
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

Alternatively, drop a file into the inbox folder (`app.ingestion.inbox.path`, enabled by default)
— the `InboxScheduler` picks it up on its next poll (every 30s) with no API call needed.

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

Response fields:

- `answer` — the LLM-generated response (or the canned insufficient-context message)
- `citations[]` — source documents ranked by relevance
- `faithful` — faithfulness score (null unless `evaluate-faithfulness=true`)
- `fromSemanticCache` — true if the answer was served from cache
- `insufficientContext` — true if the pre-generation judge skipped the final LLM call

---

## API-key Authentication

- All `/api/**` routes are protected when `app.security.auth-enabled=true`
- API keys are stored as SHA-256 digests in the `api_keys` PostgreSQL table — plaintext keys are never persisted
- To provision a new key:

```bash
raw=$(openssl rand -hex 32)
hash=$(printf "%s" "$raw" | shasum -a 256 | cut -d' ' -f1)
psql ... -c "INSERT INTO api_keys (key_hash, label) VALUES ('$hash', 'my-client');"
echo "X-API-Key: $raw"
```

- Send the raw value in the `X-API-Key` request header; the filter hashes it and compares against the stored digest
- Rate limiting is enforced separately via `RateLimitFilter` (token-bucket, configurable capacity and refill rate)

---

## Build & Test

```bash
# Unit tests only (no Docker required)
mvn test -Dtest="*Test" -Djacoco.skip=true

# Full suite including Testcontainers integration tests (needs Docker)
mvn verify
```

Test infrastructure:

- **Unit tests** — Mockito-based, fully offline
- **Integration tests** (`@SpringBootTest`) — spin up real Postgres + OpenSearch + MongoDB + Redis via Testcontainers
- **Coverage** — JaCoCo enforces ≥70% instruction coverage on `mvn verify`

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
    ├── chunking/strategy/           # 6 chunking strategies + factory + LLM strategy classifier
    ├── enrichment/                  # LLM keyword / summary enrichment
    ├── mongo/                       # Per-chunk text + metadata store, keyed by chunkId
    ├── vectorstore/                 # Dual-write: MongoDB then batched OpenSearch writes
    ├── retrieval/
    │   ├── transform/               # Query transformation (HyDE, multi-query, rewrite, step-back)
    │   ├── search/                  # Vector / keyword / hybrid search
    │   ├── postprocess/             # Ordered filter + ranking chain
    │   └── rerank/                  # 6 reranker implementations
    ├── generation/                  # Prompt orchestration + LLM generation
    ├── cache/                       # Embedding cache + semantic answer cache + chunk-hash dedup (Redis)
    ├── security/                    # API-key auth, rate limit, PII redactor, injection guard
    ├── eval/                        # Retrieval metrics + generation RAG Triad eval + context-sufficiency judge
    ├── lifecycle/                   # Ingestion orchestration (chunk → Redis dedup → store) + delete
    ├── common/                      # Circuit breaker, retry
    ├── config/                      # Spring beans, observability, startup validation
    └── web/                         # Exception handler, request-ID filter, request logging interceptor
```

## Service Ports

| Service               | Port               |
|-----------------------|--------------------|
| RAG application       | 8081               |
| PostgreSQL            | 5432               |
| OpenSearch            | 9200 / 9600        |
| OpenSearch Dashboards | 5601               |
| MongoDB               | 27017              |
| Mongo Express (GUI)   | 8082               |
| Redis                 | 6379               |
| Prometheus            | 9090               |
| Grafana               | 3000               |
| Tempo                 | 3200 / 4317 / 4318 |
| Loki                  | 3100               |
