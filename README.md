# AI Knowledge Workspace

A serious personal software engineering project for building a knowledge-centered workspace with a Spring Boot product core, an internal AI processing service, and supporting platform infrastructure.

## Repository Structure

- `docs/` for product, architecture, ADRs, API notes, and planning
- `services/` for deployable backend services
- `infra/` for local infrastructure and operational scripts
- `.github/workflows/` for CI/CD automation

## Current Status

- `workspace-core` now exposes the current product-facing backend slice for upload, status, transcript retrieval, explicit indexing, and search.
- Repo A remains a separate FastAPI processing dependency.
- Repo B persists `Asset` and `ProcessingJob` in PostgreSQL and uses Elasticsearch for the current search slice.
- Docs are maintained in `docs/`, with current API and persistence summaries in `docs/api/API.md` and `docs/data/Database.md`.
