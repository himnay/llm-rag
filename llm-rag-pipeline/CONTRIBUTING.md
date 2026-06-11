# Contributing

Thanks for contributing to the LLM RAG Pipeline.

## Prerequisites

- Java 21+, Maven, Docker (for Testcontainers integration tests).

## Workflow

1. Branch off `main` (`feat/...`, `fix/...`, `chore/...`).
2. Make focused changes with tests.
3. Run the full build locally before opening a PR:
   ```bash
   mvn verify
   ```
   This runs unit + Testcontainers integration tests (Postgres 18 + OpenSearch) and enforces the
   JaCoCo coverage gate.
4. Open a PR against `main` and fill in the PR template.

## Coding conventions

- Match the style of surrounding code (naming, comment density, package layout under `com.org.*`).
- Keep controllers thin; validation is declarative (Bean Validation + `GlobalExceptionHandler`),
  not imperative in controllers/services.
- Configuration is bound via `@ConfigurationProperties` (prefix `app.*`) and validated with
  `@Validated`; don't scatter `@Value` lookups.
- New retrieval techniques should be added as composable `RetrievalPostProcessor` beans, not inlined
  into `RetrievalService`.
- External calls (embeddings, OpenSearch, rerank) must fail/degrade gracefully (timeouts, retries,
  guarded native calls).

## Tests

- Unit tests should not require Docker; integration tests extend `IntegrationTest`
  (`@Testcontainers(disabledWithoutDocker = true)` — skipped, not failed, without Docker).
- Keep instruction coverage above the configured JaCoCo threshold.

## Commits

- Write clear, imperative commit messages. Reference issues where relevant.
