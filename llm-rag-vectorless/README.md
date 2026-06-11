# LLM Vectorless RAG

Two vectorless retrieval approaches backed by Claude for generation — no embeddings, no vector databases, no GPU.

| Retriever | Endpoint                       | How it works                                   |
|-----------|--------------------------------|------------------------------------------------|
| BM25      | `POST /api/rag/chat`           | In-process keyword ranking — always available  |
| PageIndex | `POST /api/rag/chat-pageindex` | Cloud tree-reasoning — enabled with an API key |

---

## What is Vectorless RAG?

Standard RAG converts every document chunk into a high-dimensional vector using an embedding model, stores those vectors
in a vector database (Pinecone, Weaviate, pgvector …), and retrieves chunks at query time by cosine similarity. This
captures semantic meaning but requires an embedding model, a running vector DB, and extra latency/cost on every request.

**Vectorless RAG** achieves the same goal — grounding LLM answers in your documents — without any of that
infrastructure. Two approaches are implemented here:

---

## Retriever 1 — BM25 (local, always-on)

BM25 (Best Match 25) is a classic information-retrieval ranking function. The index is built in-memory at startup and
queries run in-process with no external calls.

```
Query ──► tokenise ──► BM25 score each chunk ──► top-k chunks ──► Claude prompt ──► answer
```

**Scoring formula:**

```
score(d, q) = Σ  IDF(t) × tf(t,d)×(k1+1) / (tf(t,d) + k1×(1 − b + b×|d|/avgdl))

IDF(t) = log( (N − df(t) + 0.5) / (df(t) + 0.5) + 1 )
```

- **TF** rewards chunks where the query word appears often, with diminishing returns
- **IDF** rewards rare, informative words over common ones
- **k1=1.2** — TF saturation; **b=0.75** — length normalisation

---

## Retriever 2 — PageIndex (cloud, reasoning-based)

[PageIndex](https://pageindex.ai) by VectifyAI is a vectorless RAG cloud service. Instead of splitting documents into
fixed chunks and comparing embedding vectors, PageIndex builds a **hierarchical tree index** (like an intelligent table
of contents) and uses LLM reasoning to navigate that tree when a question arrives.

```
            ┌─ upload once ─────────────────────────┐
.txt files ─┤ convert to PDF → POST /doc/ → doc_id  │
            └───────────────────────────────────────┘

            ┌─ per query ────────────────────────────────────────────────────┐
Query ──►   │ POST /retrieval/ {doc_id, query}                               │
            │ GET  /retrieval/{id}/ → retrieved_nodes → relevant_content     │
            └────────────────────────────────────────────────────────────────┘
                                          │
                                          ▼
                               Claude prompt ──► answer
```

PageIndex's retrieval is entirely vectorless — no embeddings are involved on either the indexing or query side. The tree
search is driven by LLM reasoning over section titles and summaries.

---

## Project Architecture

```
src/main/
├── resources/
│   ├── application.yaml
│   └── documents/               ← .txt files — the knowledge base
│       ├── vectorless-rag.txt
│       ├── spring-ai-guide.txt
│       └── java-21-features.txt
└── java/com/rag/vectorless/
    ├── config/
    │   └── AppConfig.java              ← ChatClient bean with system prompt
    ├── controller/
    │   └── RagController.java          ← REST endpoints
    ├── dto/
    │   ├── Chunk.java                  ← record: id, text, source, chunkIndex
    │   ├── ChatRequest.java            ← record: question
    │   └── ChatResponse.java           ← record: answer, sources
    └── rag/
        ├── DocumentLoader.java         ← loads .txt files, splits into chunks
        ├── BM25Retriever.java          ← builds BM25 index at startup
        ├── PageIndexClient.java        ← HTTP client for api.pageindex.ai (optional)
        └── PageIndexDocumentManager.java ← uploads docs to PageIndex at startup (optional)
```

### Startup sequence (BM25 always runs; PageIndex is conditional)

1. `DocumentLoader` reads all `*.txt` files, splits into 500-char overlapping chunks.
2. `BM25Retriever` builds the in-memory TF/IDF index from those chunks.
3. *(If `RAG_PAGEINDEX_ENABLED=true`)* `PageIndexDocumentManager` converts each `.txt` to a PDF (via PDFBox), uploads to
   `api.pageindex.ai/doc/`, and waits for PageIndex to finish building the tree index.

### Request flow — BM25 (`POST /api/rag/chat`)

1. Tokenise the query (lowercase, strip punctuation, remove stop words).
2. Score every chunk with BM25; pick top-k (default 5).
3. Concatenate chunk text into a context block.
4. Call Claude with the augmented prompt.

### Request flow — PageIndex (`POST /api/rag/chat-pageindex`)

1. For each indexed document, call `POST /retrieval/` with the doc_id + query.
2. Poll `GET /retrieval/{id}/` until status = `completed`.
3. Extract `retrieved_nodes[*].relevant_contents[*].relevant_content`.
4. Merge content from all documents and call Claude with the augmented prompt.

---

## API Endpoints

### `POST /api/rag/chat`

BM25 retrieval + Claude. No external dependencies.

```json
// request
{ "question": "How does BM25 handle document length?" }

// response
{ "answer": "BM25 normalises by dividing by average document length ...", "sources": ["vectorless-rag.txt"] }
```

### `POST /api/rag/chat-pageindex`

PageIndex tree-reasoning retrieval + Claude. Requires PageIndex to be enabled.

```json
// request
{ "question": "What are Java 21 virtual threads?" }

// response
{ "answer": "Virtual threads are lightweight threads managed by the JVM ...", "sources": ["java-21-features.txt"] }
```

### `GET /api/rag/documents`

```json
{ "documents": ["java-21-features.txt", "spring-ai-guide.txt", "vectorless-rag.txt"], "totalChunks": 42 }
```

### `GET /api/rag/health`

```json
{ "status": "ok", "chunks": 42, "pageindex": "enabled", "pageindexDocs": 3 }
```

---

## Configuration

```yaml
# application.yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-opus-4-8
          max-tokens: 1024

rag:
  chunk-size: 500      # characters per BM25 chunk
  chunk-overlap: 100   # overlap between adjacent chunks
  top-k: 5             # chunks returned by BM25 per query
  pageindex:
    enabled: ${RAG_PAGEINDEX_ENABLED:false}
    api-key: ${PAGEINDEX_API_KEY:}
```

---

## Running Locally

**Prerequisites:** Java 21, Maven 3.9+, Anthropic API key.

### BM25 only

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run
```

### BM25 + PageIndex

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export PAGEINDEX_API_KEY=<your-pageindex-api-key>   # from app.pageindex.ai
export RAG_PAGEINDEX_ENABLED=true
./mvnw spring-boot:run
```

On startup, all `.txt` files are converted to PDFs (via PDFBox) and uploaded to PageIndex. PageIndex builds its tree
index asynchronously; the app waits for all documents to be ready before accepting requests (~30–60 s per document for
the cloud service).

**Adding documents:** drop any `.txt` into `src/main/resources/documents/`. BM25 rebuilds automatically; PageIndex
re-uploads on next startup.

**Example:**

```bash
# compare both retrievers on the same question
curl -s -X POST http://localhost:8080/api/rag/chat \
  -H 'Content-Type: application/json' \
  -d '{"question": "What is BM25?"}' | jq .

curl -s -X POST http://localhost:8080/api/rag/chat-pageindex \
  -H 'Content-Type: application/json' \
  -d '{"question": "What is BM25?"}' | jq .
```

---

## BM25 vs PageIndex

|                            | BM25                            | PageIndex                             |
|----------------------------|---------------------------------|---------------------------------------|
| **Infrastructure**         | None — pure in-memory           | PageIndex cloud API                   |
| **Startup**                | Milliseconds                    | ~30–60 s per document (tree building) |
| **Query latency**          | ~1 ms in-process                | ~5–30 s (async retrieval API)         |
| **Retrieval method**       | Keyword term frequency          | LLM reasoning over tree index         |
| **Semantic understanding** | No — exact keyword overlap      | Yes — LLM understands meaning         |
| **Best for**               | Keyword-heavy technical queries | Complex questions, long documents     |
| **Accuracy benchmark**     | —                               | 98.7% on FinanceBench                 |
| **Cost**                   | Free                            | PageIndex API credits                 |

Sources:

- [PageIndex documentation](https://docs.pageindex.ai/)
- [PageIndex GitHub (VectifyAI/PageIndex)](https://github.com/VectifyAI/PageIndex)
- [Vectorless RAG cookbook](https://docs.pageindex.ai/cookbook/vectorless-rag-pageindex)
