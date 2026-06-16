# Observability Setup — Prometheus, Grafana, Tempo, Loki

This service ships a full observability stack mirroring `llm-gateway`:
**metrics** (Prometheus), **traces** (Tempo), **logs** (Loki), visualised in **Grafana**.

## 1. Start the stack

```bash
docker compose up -d
```

This starts Postgres, Redis, RedisInsight, Prometheus, Grafana, Tempo and Loki.
The RAG application itself runs on the **host** (port `8081`), so Prometheus scrapes it at
`host.docker.internal:8081` (see `observability/prometheus.yml`).

## 2. Run the app

```bash
export OPENAI_API_KEY=sk-...          # used only for embeddings (required at startup)
./mvnw spring-boot:run
```

## 3. Endpoints

| What              | URL                                       |
|-------------------|-------------------------------------------|
| App health        | http://localhost:8081/actuator/health     |
| Prometheus scrape | http://localhost:8081/actuator/prometheus |
| Prometheus UI     | http://localhost:9090                     |
| Grafana           | http://localhost:3000  (admin / admin)    |
| Tempo (traces)    | queried via Grafana                       |
| Loki (logs)       | queried via Grafana                       |

## 4. Grafana

Datasources (Prometheus, Tempo, Loki) and the **LLM RAG Pipeline** dashboard are
auto-provisioned from `observability/grafana/provisioning/`. Open Grafana →
Dashboards → *LLM RAG Pipeline* folder. The starter dashboard includes:

- HTTP request rate & p95 latency (`http_server_requests_*`)
- Retrieval latency p95 (`rag_retrieval_seconds_*`)
- JVM heap usage
- Retrieval quality from the last eval run (`rag_eval_mrr`, `rag_eval_context_precision`,
  `rag_eval_precision_at_k`, `rag_eval_recall_at_k`) — refreshed by `POST /api/admin/eval/run`

## 5. Tracing & log correlation

`management.tracing.sampling.probability=1.0` samples every request and exports spans to
Tempo over OTLP (`http://localhost:4318`). JSON logs carry `traceId`/`spanId`, and the
Loki datasource is configured with a derived field so you can jump **log → trace** in Grafana.

## Tuning

- Reduce trace volume in production by lowering `management.tracing.sampling.probability`.
- Point `OTEL_EXPORTER_OTLP_ENDPOINT` at a remote collector if not using the local Tempo.
