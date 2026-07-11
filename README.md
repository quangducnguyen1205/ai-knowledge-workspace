# AI Knowledge Workspace

A serious personal software engineering project for building a knowledge-centered workspace with a Spring Boot product core, an internal AI processing service, and supporting platform infrastructure.

## Repository Structure

- `docs/` for product, architecture, ADRs, API notes, and planning
- `services/` for deployable backend services
- `infra/` for local infrastructure and operational scripts
- `.github/workflows/` for CI/CD automation

## Current Status

- `workspace-core` exposes the product-facing backend for workspace ownership, asset upload, durable asynchronous processing coordination, transcript snapshots, automatic indexing, explicit indexing fallback, search, and grounded assistant policy.
- Repo A remains a separate FastAPI processing dependency.
- Repo B now persists `Workspace`, `Asset`, and `ProcessingJob` in PostgreSQL.
- Product search remains Spring-owned, Elasticsearch-backed, and scoped by `workspaceId` with default-workspace fallback.
- Docs are maintained in `docs/`, with current API and persistence summaries in `docs/api/API.md` and `docs/data/Database.md`.
- Local dev setup, Makefile shortcuts, host-port defaults, and the smoke helper are documented in `docs/runbooks/local-dev.md`.

The normal integrated Spring launch is `make run`, which activates the coherent `project3` profile. Use `make run-compatibility` to retain the previous `direct_upload` path with asynchronous request/result/indexing controls disabled. Neither path changes legacy session authentication or removes explicit indexing.
