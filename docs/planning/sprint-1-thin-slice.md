# Sprint 1 Thin Slice

Historical note: this document records the original thin-slice scope and outcomes at that time. Later work has since added ownership/auth, local transcript snapshot persistence, workspace rename/delete, search refinement, and release-readiness/runbook improvements.

For the current backend source of truth, use:

- [API.md](/Users/nqd2005/Projects/ai-knowledge-workspace/docs/api/API.md)
- [phase1-implemented-product-flow.md](/Users/nqd2005/Projects/ai-knowledge-workspace/docs/architecture/phase1-implemented-product-flow.md)
- [Database.md](/Users/nqd2005/Projects/ai-knowledge-workspace/docs/data/Database.md)
- [local-dev.md](/Users/nqd2005/Projects/ai-knowledge-workspace/docs/runbooks/local-dev.md)
- [integration-smoke-checklist.md](/Users/nqd2005/Projects/ai-knowledge-workspace/docs/testing/integration-smoke-checklist.md)

## Sprint Goal

Deliver a narrow but real backend slice in which Spring Boot owns the product-facing flow from upload through indexing and search for lecture-video transcript retrieval.

The goal is to prove the product boundary and end-to-end data flow, not to build a polished end-user product.

## Already Implemented

### Product-Facing Endpoints

- `POST /api/workspaces`
- `GET /api/workspaces`
- `GET /api/workspaces/{workspaceId}`
- `GET /api/assets`
- `GET /api/assets/{assetId}`
- `POST /api/assets/upload`
- `GET /api/assets/{assetId}/status`
- `GET /api/assets/{assetId}/transcript`
- `GET /api/assets/{assetId}/transcript/context`
- `POST /api/assets/{assetId}/index`
- `GET /api/search`

### Product Core Behavior

- Spring accepts lecture-video uploads through the product API.
- Spring forwards upload requests to FastAPI `POST /videos/upload`.
- Spring validates the live FastAPI upload response.
- Spring persists `Workspace`, `Asset`, and `ProcessingJob` in Repo B.
- Spring exposes a minimal workspace API through create, list, and read endpoints.
- Spring stores:
  - `fastapiTaskId`
  - `fastapiVideoId`
  - `processingJobStatus`
  - `rawUpstreamTaskState`
- Spring associates each asset with one workspace.
- Spring exposes workspace-aware asset listing through `GET /api/assets`.
- Spring falls back to the configured default workspace when upload or search omit `workspaceId`.
- Spring returns a product-side `404` for an unknown `workspaceId` and `400` for a malformed `workspaceId`.
- Default-workspace asset listing includes legacy assets with null workspace and backfills them.
- Spring performs on-demand task polling through the asset status read path.
- Spring fetches transcript rows from FastAPI `GET /videos/{video_id}/transcript`.
- Spring keeps transcript retrieval product-facing instead of exposing FastAPI directly.
- Spring exposes a narrow transcript-context follow-up endpoint for search hits.
- Spring indexes one Elasticsearch document per transcript row through an explicit product endpoint.
- Spring now sends transcript-row indexing writes through one Elasticsearch bulk request per asset.
- Indexed transcript-row documents include `workspaceId`.
- Repeated indexing reuses stable transcript-row document IDs for the same asset and transcript row.
- Successful indexing refreshes the transcript index before returning.
- Spring exposes a product-owned search endpoint backed by Elasticsearch.
- Spring scopes product search by `workspaceId`.
- Spring keeps search result ordering deterministic when Elasticsearch scores tie.
- Spring returns search results through Spring-owned DTOs instead of legacy upstream shapes.
- A smoke helper script exists at `infra/scripts/smoke-thin-slice.sh` for the current happy path, with optional non-default workspace verification through `SMOKE_WORKSPACE_NAME`.

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
- Repeated indexing is safe to rerun because the same asset/transcript-row combination maps to the same Elasticsearch document ID.
- Search ordering is deterministic on score ties.
- Transcript context matches real upstream row IDs when they exist and only falls back to `segment-{segmentIndex}` for rows whose upstream ID is missing.
- Lightweight tests now cover workspace-aware upload, default-workspace fallback, invalid workspace handling, workspace-aware search filters, repeated indexing, legacy-asset default-workspace backfill, and workspace-aware asset listing.
- Lightweight tests also cover transcript-context retrieval success, invalid window handling, row-not-found handling, and transcript-not-usable paths.
- The focused Maven test run passes.

## Remaining Work

### Verification And Hardening

- Keep rerunning the full thin slice manually against live Repo A, PostgreSQL, and Elasticsearch after local-dev or API changes.
- Verify that empty-transcript assets never become searchable in the real end-to-end path.
- Verify indexing and search failure paths against a live Elasticsearch node, not only the lightweight tests.
- Keep verifying the search-to-context follow-up path against a live stack, not only the lightweight tests and helper output.

### Product Gaps Still Deferred

- Auth-based workspace ownership enforcement
- Workspace management beyond the current create/list/read surface plus default-workspace bootstrap
- Local transcript-table persistence or caching
- Search tuning beyond the current baseline text query

## Still-Open Decisions

- Should indexing remain explicit or move behind a later background workflow?
- When should transcript rows also be persisted locally instead of fetched on demand?
- When should the default-workspace bootstrap be replaced with real workspace management and ownership rules?

## Acceptance Criteria

### Completed

- Done: Spring Boot exposes a product-facing upload endpoint for lecture video.
- Done: Spring exposes minimal workspace create, list, and read endpoints.
- Done: The upload flow forwards media to FastAPI `POST /videos/upload`.
- Done: Spring stores both upstream identifiers returned by FastAPI: `task_id` and `video_id`.
- Done: Spring persists `Workspace`, `Asset`, and `ProcessingJob`.
- Done: Spring associates uploaded assets with a workspace and supports default-workspace fallback.
- Done: Spring exposes workspace-aware asset listing with default-workspace fallback.
- Done: Spring can retrieve task status from FastAPI `GET /videos/tasks/{task_id}` through the asset-centric status path.
- Done: Spring can fetch transcript rows from FastAPI `GET /videos/{video_id}/transcript`.
- Done: Spring exposes transcript-context retrieval for search-hit follow-up through `GET /api/assets/{assetId}/transcript/context`.
- Done: The transcript row model used by Spring only depends on the confirmed upstream fields.
- Done: Spring does not use `/videos/search` as its product search contract.
- Done: Transcript rows are indexed into Elasticsearch through Spring.
- Done: Indexed transcript-row documents now include `workspaceId`.
- Done: Successful indexing refreshes Elasticsearch before returning success.
- Done: Spring exposes a product-facing search endpoint that returns transcript-row results from Elasticsearch.
- Done: Spring scopes search by `workspaceId` with default-workspace fallback.
- Done: Spring returns clear product-side errors for malformed and unknown `workspaceId` values where workspace scoping is accepted.
- Done: Repeated indexing reuses stable transcript-row document IDs instead of widening the indexing lifecycle.
- Done: Search ordering is deterministic when Elasticsearch scores tie.
- Done: Lightweight coverage exists for the current workspace-aware upload, indexing, and search slice.
- Done: Lightweight coverage exists for transcript-context retrieval plus invalid-window and row-not-found behavior.
- Done: The smoke helper can also exercise a non-default workspace path through `SMOKE_WORKSPACE_NAME`.
- Done: The smoke helper can optionally verify the search-to-context step through `SMOKE_VERIFY_CONTEXT`.
- Done: The end-to-end thin slice exists through upload, status tracking, transcript retrieval, indexing, and search.
- Done: If FastAPI reports a ready or successful state but transcript rows are empty, Spring handles that outcome explicitly instead of treating the asset as usable.

### Remaining

- Remaining: Decide the next hardening step after the thin slice, rather than broadening scope by default.

## Follow-Up Tasks

### Verification

- Rerun the thin slice end to end with Repo A and Repo B separately after meaningful flow changes.
- Verify that empty-transcript assets are excluded from indexing and search in the live path.
- Keep the smoke checklist aligned with the actual Spring endpoints.
- Keep the smoke helper aligned with both the default-workspace path and the optional non-default workspace path.
- Rerun the helper with `SMOKE_WORKSPACE_NAME` when workspace-scoping changes land.

### Small Technical Follow-Ups

- Decide whether `GET /api/assets/{assetId}` should remain public or stay as a simple convenience endpoint.
- Decide whether `GET /api/assets` needs pagination or additional filtering once asset counts grow.
- Decide when to move beyond default-workspace fallback into real workspace management and ownership.

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

FastAPI success still does not guarantee usable transcript rows. The code handles this conservatively now, but the live end-to-end path still needs periodic manual verification.

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
11. Call out that the default workspace path works without exposing FastAPI directly.
12. Call out that search does not depend on FastAPI `/videos/search`.

## Sprint Outcome Checklist

- [x] Product-facing upload flow exists in Spring
- [x] Upstream FastAPI upload integration works
- [x] `task_id` and `video_id` are persisted in Spring
- [x] On-demand polling or status refresh works
- [x] Transcript rows can be fetched from FastAPI
- [x] Empty transcript handling is explicit
- [x] Transcript rows can be indexed into Elasticsearch
- [x] Product-facing search returns results from Spring
- [x] Full live smoke/demo run has been completed against Repo A and Elasticsearch together
