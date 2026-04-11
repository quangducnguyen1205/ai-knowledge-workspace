# AI Knowledge Workspace

A serious personal software engineering project for building a knowledge-centered workspace with a Spring Boot product core, an internal AI processing service, and supporting platform infrastructure.

## Repository Structure

- `docs/` for product, architecture, ADRs, API notes, and planning
- `services/` for deployable backend services
- `infra/` for local infrastructure and operational scripts
- `.github/workflows/` for CI/CD automation

## Current Status

- `workspace-core` now exposes the current product-facing backend slice for workspace create/list/read, workspace-aware asset listing, upload, status, transcript retrieval, transcript-context follow-up, explicit indexing, and search.
- Repo A remains a separate FastAPI processing dependency.
- Repo B now persists `Workspace`, `Asset`, and `ProcessingJob` in PostgreSQL.
- Product search remains Spring-owned, Elasticsearch-backed, and scoped by `workspaceId` with default-workspace fallback.
- Docs are maintained in `docs/`, with current API and persistence summaries in `docs/api/API.md` and `docs/data/Database.md`.
- Local dev setup, Makefile shortcuts, host-port defaults, and the smoke helper are documented in `docs/runbooks/local-dev.md`.
