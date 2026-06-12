# llm-rag

A multi-module Maven project exploring three complementary approaches to **Retrieval-Augmented
Generation (RAG)**: a production-grade vector pipeline, a vectorless keyword/tree-reasoning
service, and a knowledge-graph pipeline. Each module is an independent Spring Boot application;
the root `pom.xml` is a plain aggregator.

| Module | Approach | Storage | LLM usage |
|--------|----------|---------|-----------|
| [`llm-rag-pipeline`](llm-rag-pipeline/README.md) | Vector RAG (ingestion + retrieval) | OpenSearch (kNN) + PostgreSQL | Embeddings always; chunking / enrichment / reranking opt-in |
| [`llm-rag-vectorless`](llm-rag-vectorless/README.md) | Vectorless RAG (BM25 + PageIndex) | In-memory index / PageIndex cloud | Claude for answer generation |
| [`llm-rag-graph`](llm-rag-graph/README.md) | Graph RAG | Neo4j knowledge graph | Claude reasons over traversed graph context |

## Modules

### [llm-rag-pipeline](llm-rag-pipeline/README.md) — Spring AI vector RAG backend

A production-grade **ingestion + retrieval** service built with Spring AI. Documents are read
(PDF/OCR, Markdown, Excel, Tika …), normalized, deduplicated by content hash, chunked by a
pluggable `ChunkingStrategy`, optionally LLM-enriched, and stored as vectors in OpenSearch.
Retrieval offers vector kNN, BM25 keyword, and hybrid RRF search behind a `SearchStrategy`
interface, followed by a post-processor chain (filters, dedup, six reranking strategies, MMR
diversity). Includes API-key security, rate limiting, retrieval-quality evaluation (MRR, P@k,
R@k), and full observability (Prometheus / Grafana / Tempo / Loki). Answer generation is
deliberately out of scope — a downstream consumer turns the ranked chunks into answers.

### [llm-rag-vectorless](llm-rag-vectorless/README.md) — RAG without embeddings

Two retrieval approaches that need **no embedding model, no vector database, and no GPU**, with
Claude for generation. A local **BM25** index ranks chunks in-process with classic keyword IR
(always on), and **PageIndex** — a cloud tree-reasoning service — navigates a hierarchical
document index with LLM reasoning (opt-in via API key). Both feed the same grounded-answer
prompt, so the two retrievers can be compared side by side on the same questions.

### [llm-rag-graph](llm-rag-graph/README.md) — Graph RAG on Neo4j

Answers natural-language questions by **traversing a knowledge graph** instead of comparing
vectors. A seeded 4-level corporate graph (Company → Department → Team → Employee, plus projects,
technologies, and management chains) is queried with full-text search and multi-hop Cypher
traversals; the extracted subgraph context is injected into a Claude prompt, so the LLM narrates
structured facts rather than inventing them. Strong at multi-hop questions flat RAG cannot
answer, e.g. *"Who in Engineering works on ML projects and reports to the CTO?"*.

## Design patterns

The variation points across the modules are modelled with classic Gang-of-Four patterns —
Strategy, Chain of Responsibility, Factory, Template Method, Composite, Proxy, Facade, Adapter —
and patterns are deliberately *omitted* where no real variation point exists. See the
[Design patterns section](llm-rag-pipeline/README.md#-design-patterns-gof) in `llm-rag-pipeline`
for the full table and the reasoning, plus the shorter notes in
[`llm-rag-vectorless`](llm-rag-vectorless/README.md#-design-patterns) and
[`llm-rag-graph`](llm-rag-graph/README.md#-design-patterns).

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
