# Phase 1 Implemented Product Flow

## Purpose

This note documents the currently implemented product-side flow in Repo B. It is intentionally narrow and reflects the current Spring Boot code rather than the broader target architecture.

## Current Implemented Product Flow

Repo A remains a separate FastAPI processing service. Repo B is the Spring Boot product core.

The currently implemented product-facing endpoints are:

- `POST /api/workspaces`
- `GET /api/workspaces`
- `GET /api/workspaces/{workspaceId}`
- `GET /api/assets`
- `GET /api/assets/{assetId}`
- `DELETE /api/assets/{assetId}`
- `POST /api/assets/upload`
- `GET /api/assets/{assetId}/status`
- `GET /api/assets/{assetId}/transcript`
- `GET /api/assets/{assetId}/transcript/context`
- `POST /api/assets/{assetId}/index`
- `GET /api/search`

The implemented flow is:

1. Spring exposes minimal workspace create, list, and read endpoints.
2. Spring creates the configured default workspace lazily when the default scope is first needed.
3. Spring receives a multipart upload from the client.
4. Spring resolves the requested `workspaceId`, or falls back to the configured default workspace.
5. Spring forwards `file` and `title` to FastAPI.
6. Spring validates the live FastAPI upload response.
7. Spring persists a local `Workspace` reference on `Asset` plus the related `ProcessingJob`.
8. Spring exposes workspace-aware asset listing plus simple per-asset reads and deletion.
9. Spring exposes asset-centric status reads and performs on-demand polling when the local job is not terminal.
10. Spring exposes transcript reads through the product API using the stored `fastapiVideoId`.
11. Spring exposes a narrow transcript-context follow-up endpoint that returns a row window around one transcript hit.
12. Spring exposes an explicit product-side indexing trigger that writes one logical Elasticsearch document per transcript row through a bulk indexing request.
13. Successful indexing refreshes the transcript index before returning.
14. Spring exposes a product-owned search endpoint backed by Elasticsearch.

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
- Spring also exposes a separate transcript-context endpoint for search-hit follow-up.
- Transcript context is selected by transcript row ordering on `segmentIndex`.
- If a transcript row has a real upstream `id`, context lookup matches only that `id`.
- The fallback identifier `segment-{segmentIndex}` only applies when the upstream transcript row `id` is missing.

Workspace management and asset listing are also product-facing.

- Spring exposes a minimal workspace API through `POST /api/workspaces`, `GET /api/workspaces`, and `GET /api/workspaces/{workspaceId}`.
- Workspace reads and listing stay intentionally narrow: `id`, `name`, and `createdAt`.
- Asset listing runs through `GET /api/assets`.
- Asset listing resolves `workspaceId` and falls back to the configured default workspace when it is omitted.
- Asset listing supports small v1 pagination through `page` and `size`, plus one optional `assetStatus` filter.
- Pagination and filtering are applied within the resolved workspace scope.
- A provided unknown `workspaceId` returns a product-side `404`, and a malformed `workspaceId` returns `400`.
- Default-workspace asset listing includes older local assets whose workspace association is still null.
- Default-workspace asset reads and listing backfill returned legacy assets to the configured default workspace.
- Non-default workspace listing only returns assets already associated with that workspace.
- Asset listing uses deterministic default ordering:
  - `createdAt desc`
  - tie-break by `assetId desc`
- Asset deletion runs through `DELETE /api/assets/{assetId}`.
- Local deletion removes the linked `ProcessingJob` plus `Asset`, but never deletes a `Workspace`.
- If the asset is `SEARCHABLE`, Spring deletes that asset's Elasticsearch documents before removing local DB records.
- This deletion slice does not call upstream FastAPI delete or cancel behavior.

Indexing and search are also product-facing.

- Indexing is explicit through `POST /api/assets/{assetId}/index`.
- Indexing only uses usable non-empty transcript rows.
- Indexed transcript-row documents include `workspaceId`.
- One asset indexing request now sends transcript-row documents through one Elasticsearch bulk write path.
- Repeated indexing reuses stable transcript-row document IDs for the same asset and transcript row.
- Successful indexing refreshes the transcript index before returning.
- If Elasticsearch indexing fails after transcript data is usable, Spring does not collapse the asset back to `FAILED`.
- Search runs through Spring, not FastAPI.
- Search resolves `workspaceId` and falls back to the configured default workspace when it is omitted.
- A provided unknown `workspaceId` returns a product-side `404`, and a malformed `workspaceId` returns `400`.
- Search filters on workspace scope before returning results.
- Search only returns transcript-row documents for assets currently marked `SEARCHABLE`.
- The current search baseline is a simple Elasticsearch text query over transcript text and asset title.
- Search ordering is deterministic on score ties.

## What Is Intentionally Not Implemented Yet

- Local transcript-table persistence
- Auth-based workspace ownership enforcement
- Workspace management beyond the current create/list/read surface plus default-workspace bootstrap
- Background scheduling or workflow orchestration for polling/indexing
- Search tuning beyond the current baseline text query

## Guardrails For The Next Step

- Keep Spring as the product-facing boundary for indexing and search.
- Do not treat FastAPI `/videos/search` as the product search contract.
- Keep search results product-owned and avoid leaking raw FastAPI IDs.
- Keep transcript indexing grounded in the currently verified transcript fields.
- Avoid broad domain redesign while hardening the current flow.
