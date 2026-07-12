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

The normal integrated Spring launch is `make run`, which activates the coherent `project3` profile. The functional `direct_upload` rollback path, `compatibility` profile, `make run-compatibility`, and legacy `make run-standalone` alias are deprecated for normal Project3 operation but have no scheduled removal date. Neither this deprecation nor the default path changes legacy session authentication, explicit indexing recovery, or manual relay/recovery operations. See the [deprecation registry](docs/architecture/deprecations.md).
