# Sprint 1 Thin Slice

## Sprint Goal

Deliver a narrow but real backend slice in which Spring Boot owns the product-facing flow from upload through indexing and search for lecture-video transcript retrieval.

The goal is to prove the product boundary and end-to-end data flow, not to build a polished end-user product.

## Already Implemented

### Product-Facing Endpoints

- `POST /api/assets/upload`
- `GET /api/assets/{assetId}/status`
- `GET /api/assets/{assetId}/transcript`
- `POST /api/assets/{assetId}/index`
- `GET /api/search`

### Product Core Behavior

- Spring accepts lecture-video uploads through the product API.
- Spring forwards upload requests to FastAPI `POST /videos/upload`.
- Spring validates the live FastAPI upload response.
- Spring persists `Asset` and `ProcessingJob`.
- Spring stores:
  - `fastapiTaskId`
  - `fastapiVideoId`
  - `processingJobStatus`
  - `rawUpstreamTaskState`
- Spring performs on-demand task polling through the asset status read path.
- Spring fetches transcript rows from FastAPI `GET /videos/{video_id}/transcript`.
- Spring keeps transcript retrieval product-facing instead of exposing FastAPI directly.
- Spring indexes one Elasticsearch document per transcript row through an explicit product endpoint.
- Spring exposes a product-owned search endpoint backed by Elasticsearch.
- Spring returns search results through Spring-owned DTOs instead of legacy upstream shapes.

### Transcript Handling

- Spring only depends on the confirmed transcript fields:
  - `id`
  - `video_id`
  - `segment_index`
  - `text`
  - `created_at`
- Spring does not assume timestamps or richer transcript metadata exist.
- Empty transcript is handled explicitly as not usable and is not treated as transcript-ready or searchable.

### Search And Indexing

- Successful indexing moves an asset to `SEARCHABLE`.
- Search only returns documents for assets already marked `SEARCHABLE`.
- Search currently uses a simple Elasticsearch text-query baseline over transcript text and asset title.
- Lightweight tests exist for indexing and search, and the focused Maven test run passes.

## Remaining Work

### Verification And Hardening

- Run the full thin slice manually against live Repo A, PostgreSQL, and Elasticsearch.
- Verify that empty-transcript assets never become searchable in the real end-to-end path.
- Verify indexing and search failure paths against a live Elasticsearch node, not only the lightweight tests.

### Product Gaps Still Deferred

- Real workspace persistence and workspace-scoped filtering
- Local transcript-table persistence or caching
- Search tuning beyond the current baseline text query

## Still-Open Decisions

- Should indexing remain explicit or move behind a later background workflow?
- When should transcript rows also be persisted locally instead of fetched on demand?
- How should the search response evolve when workspace filters or richer search UX are added?

## Acceptance Criteria

### Completed

- Done: Spring Boot exposes a product-facing upload endpoint for lecture video.
- Done: The upload flow forwards media to FastAPI `POST /videos/upload`.
- Done: Spring stores both upstream identifiers returned by FastAPI: `task_id` and `video_id`.
- Done: Spring persists both `Asset` and `ProcessingJob`.
- Done: Spring can retrieve task status from FastAPI `GET /videos/tasks/{task_id}` through the asset-centric status path.
- Done: Spring can fetch transcript rows from FastAPI `GET /videos/{video_id}/transcript`.
- Done: The transcript row model used by Spring only depends on the confirmed upstream fields.
- Done: Spring does not use `/videos/search` as its product search contract.
- Done: Transcript rows are indexed into Elasticsearch through Spring.
- Done: Spring exposes a product-facing search endpoint that returns transcript-row results from Elasticsearch.
- Done: The end-to-end thin slice exists through upload, status tracking, transcript retrieval, indexing, and search.
- Done: If FastAPI reports a ready or successful state but transcript rows are empty, Spring handles that outcome explicitly instead of treating the asset as usable.

### Remaining

- Remaining: Complete a live manual smoke run that covers the full slice with Repo A and Elasticsearch running together.
- Remaining: Decide the next hardening step after the thin slice, rather than broadening scope by default.

## Follow-Up Tasks

### Verification

- Run the thin slice end to end with Repo A and Repo B separately.
- Verify that empty-transcript assets are excluded from indexing and search in the live path.
- Keep the smoke checklist aligned with the actual Spring endpoints.

### Small Technical Follow-Ups

- Decide whether `GET /api/assets/{assetId}` should remain public or stay as a simple convenience endpoint.
- Decide when to replace per-document indexing writes with a bulk path.
- Decide when workspace persistence is mature enough to enforce product-side search scoping.

## Explicit Non-Goals

- Authentication and authorization
- Collaboration or multi-user workspace behavior
- Chatbot or assistant behavior
- RAG answer generation
- Timestamp-based seek or media jumping
- Use of FastAPI `/videos/search` as the product search API
- Temporal, Kafka, or Kubernetes
- Search quality optimization beyond a basic working product slice
- Polished frontend work

## Risks

### Empty Transcript Edge Case

FastAPI success still does not guarantee usable transcript rows. This remains a product risk until indexing and search explicitly exclude empty-transcript assets.

### Search Scope Creep

Indexing and search can expand quickly into ranking, filtering, and API-shape work. Sprint 1 should stay focused on a narrow first retrieval path.

### Upstream Contract Drift

The current Spring implementation relies on the verified upload, task, and transcript contracts. If upstream response shapes change, the thin slice may need adjustment.

### Separate Repo Coordination

Repo A and Repo B still run separately. This keeps boundaries clear, but local verification depends on both stacks being available and correctly configured.

## End-Of-Sprint Demo Script

1. Start Repo A and confirm FastAPI is reachable.
2. Start Repo B infrastructure and Spring Boot.
3. Upload one lecture video through `POST /api/assets/upload`.
4. Show that Spring returns product-owned identifiers and persists processing state internally.
5. Call `GET /api/assets/{assetId}/status` until processing reaches terminal success or failure.
6. Call `GET /api/assets/{assetId}/transcript`.
7. Show that Spring returns transcript rows only when they are non-empty.
8. Show that empty transcript is handled as not usable.
9. Call `POST /api/assets/{assetId}/index` and show that the asset becomes `SEARCHABLE`.
10. Call `GET /api/search?q=...` and show product-facing transcript-row results from Spring.
11. Call out that search does not depend on FastAPI `/videos/search`.

## Sprint Outcome Checklist

- [x] Product-facing upload flow exists in Spring
- [x] Upstream FastAPI upload integration works
- [x] `task_id` and `video_id` are persisted in Spring
- [x] On-demand polling or status refresh works
- [x] Transcript rows can be fetched from FastAPI
- [x] Empty transcript handling is explicit
- [x] Transcript rows can be indexed into Elasticsearch
- [x] Product-facing search returns results from Spring
- [ ] Full live smoke/demo run has been completed against Repo A and Elasticsearch together
