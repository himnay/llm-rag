# LLM RAG Pipeline — Spring AI Production-Grade Backend

A production-grade Retrieval-Augmented Generation **ingestion + retrieval** service built with
Spring AI. It owns the two halves of "Retrieval-Augmented": **ingestion** (turning documents into
searchable vectors) and **retrieval** (returning the most relevant, ranked chunks for a query).

> **Answer generation is out of scope here.** This service does **no chat/LLM generation** — a
> downstream consumer (e.g. [`llm-gateway`](../llm-gateway)) takes the retrieved chunks and produces
> the answer. The only always-on LLM interaction here is **embeddings** (local `EmbeddingModel`);
> **semantic/LLM chunking**, **metadata enrichment**, and **reranking** (cross-encoder / LLM-based
> / local strategies — see [Reranking](#-reranking-second-stage-retrieval)) are opt-in.

## 🛠️ Technology Stack

- **Spring Boot** 4.0.6 · **Spring AI** 2.0.0-M8 · **Java** 21 · **Maven**
- **OpenAI API** — used **locally for embeddings** (vector modelling) only
- **OpenSearch** for vector storage (kNN) · **PostgreSQL 18** for knowledge metadata + API keys
- **Spring Security** — API-key authentication on `/api/**`
- **Observability**: Micrometer + Prometheus + Grafana + Tempo (traces) + Loki (logs)

## 🏗️ Architecture

```
                 ┌─────────────────────────── INGESTION ───────────────────────────┐
  REST by-name / │  triggers: /lifecycle/ingest[-all] · /lifecycle/upload · inbox   │
  upload / inbox │  readers by extension: Page/ParagraphPdf (+OCR for scanned PDF), │
  folder    ───► │      Markdown, Text, Json, Excel→Markdown tables (POI), Tika     │
                 │  → TextNormalizer (clean) → content-hash dedup (skip unchanged)  │
                 │  → ChunkingStrategy (token|recursive|semantic|markdown|fixed|llm)│
                 │  → ChunkEnricher (opt-in: keywords/summary via LLM)              │
                 │  → ChunkVectorStoreService (parallel batched add)                │
                 │  → OpenSearchVectorStore (nexacorp_index, embeddings LOCAL)      │
                 └──────────────────────────────────────────────────────────────────┘

                 ┌─────────────────────────── RETRIEVAL ───────────────────────────┐
 POST /api/v1/retrieve │  RetrievalController → RetrievalService                    │
        ───►           │   → SearchStrategy (vector kNN | keyword BM25 | hybrid RRF)│
                       │      over-fetch topK*factor, threshold pushed down         │
                       │   → post-processor chain: business rules → length → dedup  │
                       │     → rerank (opt-in: cross-encoder | bi-encoder |          │
                       │        llm-pointwise | llm-listwise | bm25 | rrf)           │
                       │     → score-aware rank → MMR (opt-in)                       │
                       │  ◄── RetrievalResult { chunks[], citations[] }             │
                       └──────────────────────────────────────────────────────────────┘
```

### Key components (`com.org.*`)

- `controller/` — `RetrievalController` (`POST /api/v1/retrieve`), `LifecycleController`
  (ingest/delete/upload), `EvaluationController`, `HealthController`.
- `security/` — API-key auth: `ApiKeyService` (SHA-256 hashes in the `api_keys` table),
  `ApiKeyAuthFilter`, `SecurityConfig` (headers + CORS), `RestAuthenticationEntryPoint` (401 JSON).
- `retrieval/` — `RetrievalService` + the composable `postprocess/` chain (`BusinessRuleFilter`,
  `LengthFilter`, `NearDuplicateFilter`, `RerankingPostProcessor`, `ScoreAwareRanker`,
  `MmrDiversityProcessor`) and `model/Citation`.
- `retrieval/search/` — pluggable first-stage search strategies behind the `SearchStrategy`
  interface: `VectorSearchStrategy` (cosine kNN), `KeywordSearchStrategy` (BM25 full-text),
  `HybridSearchStrategy` (RRF fusion of both).
- `retrieval/rerank/` — pluggable second-stage reranking strategies (`CrossEncoderReranker`,
  `BiEncoderReranker`, `LlmPointwiseReranker`, `LlmListwiseReranker`, `Bm25Reranker`,
  `RrfFusionReranker`) behind the `Reranker` interface, orchestrated by `RerankingPostProcessor`
  (score cache, circuit breaker, metrics, relevance floor).
- `common/` — dependency-free `Resilience` (retry + backoff) and `CircuitBreaker` helpers for
  outbound calls.
- `ingestion/` — readers (`reader/`, `excel/`, `ocr/`), `TextNormalizer`, orchestrators; PDF / Wiki
  / Database ingestion services.
- `lifecycle/` — knowledge ingest/delete with content-hash dedup (`IngestionLogRepository`).
- `vectorstore/ChunkVectorStoreService` — parallel batched writes to the OpenSearch store.
- `eval/` — retrieval-quality metrics (MRR / context precision / P@k / R@k).
- `config/ObservabilityConfig` — Micrometer `@Timed`/`@Observed` aspects + JVM-extras binders.

## 🚀 Getting Started

### Prerequisites
- Java 21+
- Docker & Docker Compose (Postgres 18, OpenSearch, OpenSearch Dashboards)
- An OpenAI API key (for embeddings)
- *(Optional)* the native `tesseract` library if you enable OCR

### Setup

1. **Start infrastructure**
   ```bash
   docker compose up -d   # Postgres 18, OpenSearch (:9200), OpenSearch Dashboards (:5601)
   ```

2. **Set environment variables**
   ```bash
   export OPENAI_API_KEY=sk-...   # embeddings only
   ```

3. **Run the application** (defaults to port `8081`)
   ```bash
   mvn spring-boot:run
   ```

### Testing the API

```bash
# Ingest the bundled sample knowledge
curl -X POST http://localhost:8081/api/v1/admin/lifecycle/ingest-all

# Retrieve the most relevant chunks for a query
curl -X POST http://localhost:8081/api/v1/retrieve \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the leave policy?"}'
```

When API-key auth is enabled (`app.security.auth-enabled=true`), add `-H "X-API-Key: <key>"`.

### REST API

| Method | Path                                          | Description                                                   |
|--------|-----------------------------------------------|---------------------------------------------------------------|
| GET    | `/health`                                     | Liveness check                                                |
| POST   | `/api/v1/retrieve`                            | Retrieve relevant chunks (+ citations) for a query           |
| POST   | `/api/v1/admin/eval/run?k=10`                 | Run retrieval-quality eval (MRR, context precision, P@k, R@k) |
| POST   | `/api/v1/admin/lifecycle/upload`              | Upload a file (multipart) and ingest it                       |
| POST   | `/api/v1/admin/lifecycle/ingest`              | Ingest a single knowledge source                              |
| POST   | `/api/v1/admin/lifecycle/ingest-all`          | Ingest all bundled sources                                    |
| DELETE | `/api/v1/admin/lifecycle/delete`              | Delete a knowledge source                                     |
| DELETE | `/api/v1/admin/lifecycle/delete-all`          | Delete all knowledge                                          |
| GET    | `/actuator/health` · `/actuator/prometheus`   | Health & metrics                                              |

- Spec: <http://localhost:8081/openapi.yaml> · Swagger UI: <http://localhost:8081/swagger-ui.html>
- Observability runbook: [PROMETHEUS_GRAFANA_SETUP.md](PROMETHEUS_GRAFANA_SETUP.md)

## 🔐 API-key authentication

`/api/**` can be protected by an `X-API-Key` header (`app.security.auth-enabled=true`, on by default
in the `prod` profile). Keys are stored as SHA-256 digests in the `api_keys` table — raw keys are
never persisted. To mint one:

```bash
raw=$(openssl rand -hex 32)
hash=$(printf "%s" "$raw" | shasum -a 256 | cut -d' ' -f1)
psql ... -c "INSERT INTO api_keys (key_hash, label) VALUES ('$hash', 'my-client');"
echo "X-API-Key: $raw"
```

## 🔎 Retrieval pipeline & filtering

Candidates are over-fetched (`app.retrieval.over-fetch-factor`) by the configured
[search mode](#-search-modes-first-stage-retrieval), then run through an ordered, composable
post-processor chain — each technique is an independently testable Spring bean:

| Stage | Property | Default |
|-------|----------|---------|
| Business-rule visibility (DB ACL / announcement window) | — | on |
| Length filter | `app.retrieval.length.{min,max}-chars` | off (0) |
| Near-duplicate collapse | `app.retrieval.dedup.*` | on |
| Reranking — 6 pluggable strategies (see below) | `app.retrieval.rerank.*` | off |
| Score-aware ranking (relevance first, business priority as tie-break) | — | on |
| MMR diversity | `app.retrieval.mmr.*` | off |

Each retrieved chunk is returned with a `Citation` (source, fileName, identity, page, chunkIndex,
score) for traceability.

## 🔍 Search modes (first-stage retrieval)

How candidates are fetched is a pluggable `SearchStrategy` (`com.org.retrieval.search`), selected
by `app.retrieval.search.mode`:

| `mode` | Technique | Extra cost | Best for |
|--------|-----------|-----------|----------|
| `vector` *(default)* | Cosine-similarity kNN over embeddings (OpenSearch knn index); `similarity-threshold` pushed down | 1 embedding call | Semantic / paraphrased queries |
| `keyword` | Lexical BM25 full-text match on the chunk `content` field of the same index | none | Exact terms: error codes, IDs, names |
| `hybrid` | Runs both, fuses rankings with Reciprocal Rank Fusion (dedup by id); a failing side degrades to the other | 1 embedding call | Mixed queries ("how do I fix E1234") |

```yaml
app:
  retrieval:
    search:
      mode: hybrid   # vector | keyword | hybrid   (env: SEARCH_MODE)
```

Keyword and hybrid scores are max-normalized to 0..1, so the post-processing chain (thresholds,
rerankers, MMR) behaves identically whichever mode produced the candidates.

## 🔁 Reranking (second-stage retrieval)

First-stage vector search optimizes for recall; the opt-in **rerank stage**
(`com.org.retrieval.rerank`) re-scores the top candidates for precision before they are trimmed to
`topK`. All techniques implement the `Reranker` strategy interface and plug into the chain via
`RerankingPostProcessor`; pick one with `app.retrieval.rerank.strategy`:

| `strategy` | Type | How it works | Extra cost | Best for |
|------------|------|--------------|-----------|----------|
| `cross-encoder` | Pointwise, neural | External Cohere-compatible `/v1/rerank` API reads query + document together | API $$ + latency | Highest quality; needs `api-key` |
| `bi-encoder` | Pointwise, embedding | Re-embeds query + candidates with the local `EmbeddingModel`, exact cosine re-score | 1 embedding call | Fixing approximate ANN scores; merged candidate sets |
| `llm-pointwise` | Pointwise, LLM-as-judge | Chat LLM grades each candidate 0–100 independently | 1 LLM call **per candidate** | Zero-shot quality without a rerank vendor; cap with `top-n` |
| `llm-listwise` | Listwise, LLM (RankGPT-style) | Chat LLM sees all candidates and returns a permutation | 1 LLM call total | Comparative ranking; cheaper than pointwise |
| `bm25` | Lexical | Okapi BM25 over the candidate set, in-process | none | Exact keywords, error codes, IDs, names |
| `rrf` | Hybrid, rank fusion | Reciprocal Rank Fusion of the dense (vector) and BM25 rankings | none | General hybrid boost without comparing score scales |

```yaml
app:
  retrieval:
    rerank:
      enabled: true
      strategy: rrf            # cross-encoder | bi-encoder | llm-pointwise | llm-listwise | bm25 | rrf
      top-n: 20                # re-score only the top 20 candidates (cost cap); 0 = all
      min-score: 0.0           # drop re-scored chunks below this relevance (0 = off)
      cache:                   # per-chunk score cache (costly strategies only)
        enabled: true
        max-size: 1000
        ttl: 10m
      breaker:                 # skip reranking while the vendor/model keeps failing
        failure-threshold: 3
        cooldown: 30s
      # cross-encoder only:
      base-url: https://api.cohere.ai
      model: rerank-english-v3.0
      api-key: ${RERANK_API_KEY}
      timeout: 10s             # HTTP read timeout — fail-open on expiry
```

Safety rails (production best practices, applied centrally in `RerankingPostProcessor`):

- **Fail-open** — any reranker failure (vendor down, timeout, unparseable LLM reply) logs a warning
  and passes the vector-ranked candidates through unchanged; reranking never breaks retrieval.
- **Circuit breaker** — after `failure-threshold` consecutive failures the breaker opens for
  `cooldown`: a down vendor is skipped instead of paying its timeout on every request, then probed
  half-open (`com.org.common.CircuitBreaker`).
- **Cost cap** — `top-n` limits how many candidates are re-scored; the tail keeps its vector order.
- **Score cache** — per-chunk scores from the costly strategies (API / LLM / embedding) are cached
  (TTL + LRU) keyed by `strategy|query|sha256(content)`; a repeated query re-ranks for free.
- **Relevance floor** — `min-score` drops re-scored chunks the reranker judged irrelevant,
  improving context precision for the downstream LLM. Note the scale differs per strategy.
- **Bounded prompts/calls** — LLM rerankers truncate documents (1 500 chars) before prompting;
  `llm-pointwise` grades candidates **in parallel on virtual threads** (latency ≈ one LLM
  round-trip instead of N); the cross-encoder HTTP call has a read timeout.
- **Metrics** — Micrometer timer `rag_rerank_duration` plus counters `rag_rerank_failures`,
  `rag_rerank_cache_hits`, `rag_rerank_breaker_rejected`, all tagged by strategy (charted via the
  existing Prometheus/Grafana stack).
- **Score transparency** — every strategy writes its relevance into the chunk's `score` metadata,
  which flows into the returned `Citation`s, so reranked results stay explainable.

## 🧩 Design patterns (GoF)

The variation points of the pipeline are modelled with classic Gang-of-Four patterns — each
technique is a small, independently testable class, and adding a new one never means editing a
`switch`:

| Pattern | Where | Role |
|---------|-------|------|
| **Strategy** | `SearchStrategy` (`retrieval/search/`: vector / keyword / hybrid) · `Reranker` (`retrieval/rerank/`: 6 strategies) · `ChunkingStrategy` (`chunking/strategy/`: token / recursive / semantic / markdown / fixed / llm) | Interchangeable algorithms behind one interface, selected by configuration at runtime |
| **Chain of Responsibility** | `RetrievalPostProcessor` chain (`retrieval/postprocess/`), applied in `Ordered` order by `RetrievalService` | Each stage (filter / dedup / rerank / rank / diversify) handles the candidate list and passes it on |
| **Factory** | `ChunkingStrategyFactory` (name → `ChunkingStrategy`) · `DocumentReaderFactory` (file extension → reader) | Centralised creation/resolution so callers never bind to concrete classes |
| **Template Method** | `AbstractChunkingStrategy` | Shared chunk-building skeleton (metadata, indexing, blank-skipping); subclasses supply only the splitting step |
| **Composite** | `HybridSearchStrategy` | A `SearchStrategy` composed of the vector + keyword strategies, fused with RRF |
| **Proxy (protection)** | `RerankingPostProcessor` wrapping the selected `Reranker` | Adds the cross-cutting rails — circuit breaker, score cache, cost cap, relevance floor, metrics, fail-open — without touching any strategy |
| **Facade** | `IngestionOrchestrator` / `ChunkingOrchestrator` · `GraphRAGService` (llm-rag-graph) | One entry point over the multi-step read→clean→chunk→enrich→store (and graph-RAG) flows |
| **Adapter** | `ExcelDocumentReader` (Apache POI workbook → Markdown-table `Document`s) · OCR `OcrPdfAugmentor` (Tesseract → page text) | Bridge third-party APIs into the pipeline's document model |
| **Builder / Singleton** | Spring AI `SearchRequest.builder()` / `Document.builder()`; Spring beans are container-managed singletons | Used via the frameworks rather than hand-rolled |

> **Where patterns are deliberately *not* used:** `llm-rag-vectorless` exposes its two retrieval
> paths (local BM25, remote PageIndex) as separate endpoints with different shapes, and
> `llm-rag-graph` has a single LLM provider — introducing a Strategy interface for a single
> implementation would be speculative abstraction, not good GoF usage. The pattern earns its place
> only where a real variation point exists.

## 📥 Ingestion pipeline

`read → clean → (content-hash dedup) → chunk → enrich → store`, triggered three ways: the by-name
lifecycle API, a multipart `POST /api/v1/admin/lifecycle/upload`, or a **drop-folder watcher**
(`app.ingestion.inbox.enabled=true`).

- **Readers** (`com.org.ingestion`) pick a reader by extension: Page/Paragraph PDF, Markdown, Text,
  Json, **Excel → Markdown tables (POI)**, and Tika (docx/pptx/html).
- **OCR** (opt-in, `app.ingestion.ocr.enabled=true`): scanned/image PDF pages are rasterized with
  PDFBox and OCR'd with Tesseract (tess4j). Degrades gracefully when the native library is absent.
- **Dedup**: re-ingesting an unchanged source (same identity + content hash) is skipped; a changed
  source replaces its previous vectors — so updates never leave stale knowledge behind.
- **Clean / Chunk / Enrich / Store** as in the diagram above.

> **LLM use:** `semantic`/`llm` chunking and enrichment call the LLM via the local OpenAI models.
> They are opt-in; the default pipeline performs no chat-LLM calls.

## 🧭 Choosing an embedding model & chunk size by domain

Retrieval quality is dominated by two coupled choices: **which embedding model** encodes your text,
and **how big each chunk** is. The right pair depends on your domain's vocabulary, document
structure, and the model's maximum input length. Three rules of thumb:

1. **The chunk must fit the embedding model's max input.** Many domain BERT-style models cap at
   **512 tokens**; OpenAI `text-embedding-3-*` accepts **8191**. Never chunk larger than the model
   can encode — the tail is silently truncated.
2. **Smaller chunks = higher precision, less context; larger = more context, diluted relevance.**
   Tune to how "pinpoint" your answers are (a single clause vs. a whole procedure).
3. **Respect structure.** Keep atomic units intact — a Q&A pair, a contract clause, a table row, a
   function — instead of splitting mid-unit. (This repo keeps FAQ rows and Excel tables intact.)

### Recommended embedding models by domain

| Domain | Strong embedding models | Dimensions | Notes |
|--------|-------------------------|-----------:|-------|
| **General / enterprise docs** | `text-embedding-3-small` / `-large` (OpenAI), Cohere `embed-v3`, `bge-large-en-v1.5`, `voyage-3` | 1536 / 3072 | Repo default is `text-embedding-3-small` (1536) |
| **Legal / contracts** | `voyage-law-2`, `text-embedding-3-large`, `bge-large` + clause chunking | 1024 / 3072 | Long clauses → larger chunks, big context window |
| **Biomedical / clinical** | `MedCPT`, `S-PubMedBert-MS-MARCO`, `BioLORD`, `voyage-3-large` | 768 / 1024 | Dense terminology → smaller chunks (≤512 tok) |
| **Finance / filings** | `voyage-finance-2`, `text-embedding-3-large`, `bge-large` | 1024 / 3072 | Table-aware chunking matters (Excel→Markdown helps) |
| **Code / technical** | `voyage-code-3`, `jina-embeddings-v2-base-code`, `text-embedding-3-large` | 1024 / 3072 | Chunk by function/class, not fixed size |
| **Multilingual** | `multilingual-e5-large`, Cohere `embed-multilingual-v3`, `bge-m3`, `jina-embeddings-v3` | 1024 | Pick a model trained on your languages |

> ⚠️ Whatever you pick, set `spring.ai.vectorstore.opensearch.dimensions` to that model's dimension
> — `StartupValidator` fails fast on a mismatch.

### Recommended chunk size by domain

| Domain / content | Strategy (`app.chunking.strategy`) | Chunk size | Overlap |
|------------------|-----------------------------------|-----------:|--------:|
| General prose, wiki, manuals | `markdown` / `recursive` | 512–1024 tok (~1000 chars) | 10–20% |
| Legal contracts | `recursive` (split on clauses/sections) | 1000–1500 tok | 15–20% |
| Biomedical / scientific papers | `semantic` or section-based | 256–512 tok | 10–15% |
| Financial reports / filings | `recursive`, keep tables whole | 512–1024 tok | 10–15% |
| Code | AST/function-level (`llm` or custom) | 1 logical unit | minimal |
| FAQ / Q&A | 1 chunk per pair (no split) | per item | none |
| Chat / transcripts | by turn/speaker window | ~256 tok | 1 turn |
| Slides / presentations | per slide | per slide | none |

These map to the repo's knobs: `app.chunking.max-chars` / `overlap` (char-based strategies),
`app.chunking.token.chunk-size` (token splitter), and `app.chunking.semantic.*` (embedding-similarity
splitting). Example for a legal corpus:

```yaml
app:
  chunking:
    strategy: recursive
    max-chars: 4000      # ~1000–1200 tokens, keeps clauses intact
    overlap: 600         # ~15%
```

> Rough conversion: **1 token ≈ 4 characters** of English. The defaults (`max-chars: 1000`,
> `overlap: 150`, `token.chunk-size: 800`) target general prose; adjust per the table above.

## 🧪 Retrieval-quality evaluation

Offline metrics — **MRR**, **context precision**, **precision@k**, **recall@k** — are computed
against a labelled gold set in [`src/main/resources/eval/qrels.json`](src/main/resources/eval/qrels.json):

```bash
curl -X POST "http://localhost:8081/api/v1/admin/eval/run?k=10"
```

The run refreshes the `rag_eval_*` Prometheus gauges (charted in Grafana).

## 🏗️ Build & test

```bash
mvn verify   # unit + Testcontainers integration tests (Postgres + OpenSearch) + JaCoCo gate
```

Integration tests spin up real Postgres 18 + OpenSearch via Testcontainers and stub the embedding
model, so no external infrastructure or OpenAI key is needed. The JaCoCo coverage gate (`verify`)
enforces a minimum instruction coverage.

## 📁 Repository structure

```
├── pom.xml                     # Maven (Boot 4 / Spring AI 2.0.0-M8 / Java 21)
├── docker-compose.yml          # Postgres 18, OpenSearch, OpenSearch Dashboards
├── observability/              # Prometheus, Tempo, Loki + Grafana provisioning
├── PROMETHEUS_GRAFANA_SETUP.md # Observability runbook
├── src/main/java/com/org/
│   ├── controller/  security/  retrieval/{postprocess,rerank,search}  ingestion/{reader,excel,ocr}
│   ├── chunking/    lifecycle/  vectorstore/  eval/  enrichment/  config/  web/
└── src/main/resources/
    ├── db/migration/      # Flyway (schema, api_keys, ingestion_log)
    ├── prompts/           # Prompt templates
    ├── data/{pdfs,wiki}/  # Sample documents
    └── static/openapi.yaml
```

## Ports

| Service               | Port               |
|-----------------------|--------------------|
| RAG app               | 8081               |
| Postgres              | 5432               |
| OpenSearch            | 9200 / 9600        |
| OpenSearch Dashboards | 5601               |
| Prometheus            | 9090               |
| Grafana               | 3000               |
| Tempo                 | 3200 / 4317 / 4318 |
| Loki                  | 3100               |
