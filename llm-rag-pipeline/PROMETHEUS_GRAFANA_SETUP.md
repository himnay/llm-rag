# <span style="color:hsl(30,68%,44%)">Observability Setup — Prometheus, Grafana, Tempo, Loki</span>

This service ships a full observability stack mirroring `llm-gateway`:
**metrics** (Prometheus), **traces** (Tempo), **logs** (Loki), visualised in **Grafana**.

## <span style="color:hsl(81,68%,32%)">1. Start the stack</span>

```bash
docker compose up -d
```

This starts Postgres, Redis, RedisInsight, Prometheus, Grafana, Tempo and Loki.
The RAG application itself runs on the **host** (port `8081`), so Prometheus scrapes it at
`host.docker.internal:8081` (see `observability/prometheus.yml`).

## <span style="color:hsl(133,68%,32%)">2. Run the app</span>

```bash
export OPENAI_API_KEY=sk-...          # used only for embeddings (required at startup)
./mvnw spring-boot:run
```

## <span style="color:hsl(184,68%,36%)">3. Endpoints</span>

| What              | URL                                       |
|-------------------|-------------------------------------------|
| App health        | http://localhost:8081/actuator/health     |
| Prometheus scrape | http://localhost:8081/actuator/prometheus |
| Prometheus UI     | http://localhost:9090                     |
| Grafana           | http://localhost:3000  (admin / admin)    |
| Tempo (traces)    | queried via Grafana                       |
| Loki (logs)       | queried via Grafana                       |

## <span style="color:hsl(236,68%,44%)">4. Grafana</span>

Datasources (Prometheus, Tempo, Loki) and the **LLM RAG Pipeline** dashboard are
auto-provisioned from `observability/grafana/provisioning/`. Open Grafana →
Dashboards → *LLM RAG Pipeline* folder. The starter dashboard includes:

- HTTP request rate & p95 latency (`http_server_requests_*`)
- Retrieval latency p95 (`rag_retrieval_seconds_*`)
- JVM heap usage
- Retrieval quality from the last eval run (`rag_eval_mrr`, `rag_eval_context_precision`,
  `rag_eval_precision_at_k`, `rag_eval_recall_at_k`) — refreshed by `POST /api/admin/eval/run`

## <span style="color:hsl(287,68%,44%)">5. Tracing & log correlation</span>

`management.tracing.sampling.probability=1.0` samples every request and exports spans to
Tempo over OTLP (`http://localhost:4318`). JSON logs carry `traceId`/`spanId`, and the
Loki datasource is configured with a derived field so you can jump **log → trace** in Grafana.

## <span style="color:hsl(339,68%,44%)">Tuning</span>

- Reduce trace volume in production by lowering `management.tracing.sampling.probability`.
- Point `OTEL_EXPORTER_OTLP_ENDPOINT` at a remote collector if not using the local Tempo.
