# Phase 1 Implemented Product Flow

## Purpose

This note documents the currently implemented product-side flow in Repo B. It is intentionally narrow and reflects the current Spring Boot code rather than the broader target architecture.

## Current Implemented Product Flow

Repo A remains a separate FastAPI processing service. Repo B is the Spring Boot product core.

The currently implemented product-facing endpoints are:

- `POST /api/assets/upload`
- `GET /api/assets/{assetId}/status`
- `GET /api/assets/{assetId}/transcript`
- `POST /api/assets/{assetId}/index`
- `GET /api/search`

The implemented flow is:

1. Spring receives a multipart upload from the client.
2. Spring resolves the requested `workspaceId`, or falls back to the configured default workspace.
3. Spring forwards `file` and `title` to FastAPI.
4. Spring validates the live FastAPI upload response.
5. Spring persists a local `Workspace` reference on `Asset` plus the related `ProcessingJob`.
6. Spring exposes asset-centric status reads and performs on-demand polling when the local job is not terminal.
7. Spring exposes transcript reads through the product API using the stored `fastapiVideoId`.
8. Spring exposes an explicit product-side indexing trigger that writes one Elasticsearch document per transcript row.
9. Successful indexing refreshes the transcript index before returning.
10. Spring exposes a product-owned search endpoint backed by Elasticsearch.

## Current Local Persistence Model

Spring currently persists:

- `Workspace`
- `Asset`
- `ProcessingJob`

`Workspace` currently stores:

- `id`
- `name`
- `createdAt`

`ProcessingJob` currently stores:

- `fastapiTaskId`
- `fastapiVideoId`
- `processingJobStatus`
- `rawUpstreamTaskState`

The current transaction boundary is simple:

- Network calls to FastAPI happen outside the DB write transaction.
- DB writes are isolated in the persistence service.
- The configured default workspace can be created lazily on first use.

## Current Status And Transcript Policy

Status is product-facing and asset-centric.

- Spring loads `Asset` and `ProcessingJob` by local asset ID.
- If the processing job is already terminal, Spring returns the stored local state without further upstream polling.
- If the processing job is non-terminal, Spring calls FastAPI task status and updates local state explicitly.
- Raw upstream task state is retained for debugging.

Transcript reads are also product-facing.

- Spring fetches transcript rows from FastAPI using `fastapiVideoId`.
- Spring only uses the currently verified transcript fields:
  - `id`
  - `video_id`
  - `segment_index`
  - `text`
  - `created_at`
- Spring does not treat task success alone as proof of usable transcript data.
- If transcript rows are empty, Spring explicitly does not treat the asset as usable.
- A non-empty transcript can move the asset to `TRANSCRIPT_READY`.
- Successful indexing moves the asset to `SEARCHABLE`.

Indexing and search are also product-facing.

- Indexing is explicit through `POST /api/assets/{assetId}/index`.
- Indexing only uses usable non-empty transcript rows.
- Indexed transcript-row documents include `workspaceId`.
- Successful indexing refreshes the transcript index before returning.
- If Elasticsearch indexing fails after transcript data is usable, Spring does not collapse the asset back to `FAILED`.
- Search runs through Spring, not FastAPI.
- Search resolves `workspaceId` and falls back to the configured default workspace when it is omitted.
- Search filters on workspace scope before returning results.
- Search only returns transcript-row documents for assets currently marked `SEARCHABLE`.
- The current search baseline is a simple Elasticsearch text query over transcript text and asset title.

## What Is Intentionally Not Implemented Yet

- Local transcript-table persistence
- Auth-based workspace ownership enforcement
- Workspace CRUD beyond the current default-workspace bootstrap
- Background scheduling or workflow orchestration for polling/indexing
- Search tuning beyond the current baseline text query

## Guardrails For The Next Step

- Keep Spring as the product-facing boundary for indexing and search.
- Do not treat FastAPI `/videos/search` as the product search contract.
- Keep search results product-owned and avoid leaking raw FastAPI IDs.
- Keep transcript indexing grounded in the currently verified transcript fields.
- Avoid broad domain redesign while hardening the current flow.
