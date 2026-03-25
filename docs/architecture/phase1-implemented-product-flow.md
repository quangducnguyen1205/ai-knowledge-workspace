# Phase 1 Implemented Product Flow

## Purpose

This note documents the currently implemented product-side flow in Repo B. It is intentionally narrow and reflects the current Spring Boot code rather than the broader target architecture.

## Current Implemented Product Flow

Repo A remains a separate FastAPI processing service. Repo B is the Spring Boot product core.

The currently implemented product-facing endpoints are:

- `POST /api/assets/upload`
- `GET /api/assets/{assetId}/status`
- `GET /api/assets/{assetId}/transcript`

The implemented flow is:

1. Spring receives a multipart upload from the client.
2. Spring forwards `file` and `title` to FastAPI.
3. Spring validates the live FastAPI upload response.
4. Spring persists a local `Asset` and `ProcessingJob`.
5. Spring exposes asset-centric status reads and performs on-demand polling when the local job is not terminal.
6. Spring exposes transcript reads through the product API using the stored `fastapiVideoId`.

## Current Local Persistence Model

Spring currently persists:

- `Asset`
- `ProcessingJob`

`ProcessingJob` currently stores:

- `fastapiTaskId`
- `fastapiVideoId`
- `processingJobStatus`
- `rawUpstreamTaskState`

The current transaction boundary is simple:

- Network calls to FastAPI happen outside the DB write transaction.
- DB writes are isolated in the persistence service.

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
- `SEARCHABLE` is reserved for a later indexing/search step.

## What Is Intentionally Not Implemented Yet

- Elasticsearch indexing
- Product-facing search
- Local transcript-table persistence

## Guardrails For The Next Step

- Keep Spring as the product-facing boundary for indexing and search.
- Do not treat FastAPI `/videos/search` as the product search contract.
- Only move an asset to `SEARCHABLE` after indexing is actually implemented and succeeds.
- Keep transcript indexing grounded in the currently verified transcript fields.
- Avoid broad domain redesign while adding the first indexing and search slice.
