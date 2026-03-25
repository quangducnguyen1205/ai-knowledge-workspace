# Sprint 1 Thin Slice

## Sprint Goal

Complete a narrow, working backend slice in which Spring Boot owns the product-facing flow for lecture-video upload, asset status, and transcript retrieval, while keeping Elasticsearch indexing and product-facing search as the main remaining implementation focus.

The goal is still to prove the product boundary and data flow, not to build a polished end-user product.

## Already Implemented

### Product-Facing Endpoints

- `POST /api/assets/upload`
- `GET /api/assets/{assetId}/status`
- `GET /api/assets/{assetId}/transcript`

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

### Transcript Handling

- Spring only depends on the confirmed transcript fields:
  - `id`
  - `video_id`
  - `segment_index`
  - `text`
  - `created_at`
- Spring does not assume timestamps or richer transcript metadata exist.
- Empty transcript is handled explicitly as not usable and is not treated as transcript-ready or searchable.

## Remaining Work

### Elasticsearch Indexing

- Define the Elasticsearch document shape for transcript-row indexing.
- Decide what asset and processing metadata must be included in indexed documents.
- Implement transcript indexing from Spring after transcript retrieval succeeds.
- Define how indexing success or failure changes local asset state.

### Product-Facing Search

- Define the product search response contract in Spring.
- Implement Spring-owned search against Elasticsearch.
- Keep `/videos/search` out of the product contract.
- Return transcript-based search results through Spring rather than exposing legacy upstream behavior.

### Local Verification And Demo Completion

- Verify the full thin slice with indexing included.
- Verify that search works only on transcript content Spring considers usable.
- Update the demo flow to include indexing and product-facing search once those pieces exist.

## Still-Open Decisions

- What is the minimal Elasticsearch document structure for transcript rows in phase 1?
- Should indexing happen immediately after transcript retrieval or through a separate explicit step?
- Which asset state transition should represent “indexed and searchable” in the first version?
- What is the simplest product-facing search response shape that stays stable while search evolves?

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
- Done: The end-to-end path works through upload, status tracking, and transcript retrieval in local development.
- Done: If FastAPI reports a ready or successful state but transcript rows are empty, Spring handles that outcome explicitly instead of treating the asset as usable.

### Pending

- Pending: Transcript rows are indexed into Elasticsearch in a form that supports simple product-facing retrieval.
- Pending: Spring exposes a product-facing search endpoint that returns transcript-based results from Elasticsearch.
- Pending: The thin slice is demoable through upload, status, transcript retrieval, indexing, and product-facing search as one complete flow.

## Engineering Tasks

### Search And Indexing

- Define the first Elasticsearch index shape for transcript rows.
- Implement indexing from Spring using transcript data already retrieved through the product flow.
- Add explicit asset-state handling around indexing completion and indexing failure.
- Implement a narrow Spring search endpoint backed by Elasticsearch.

### Product Contract Cleanup

- Define a stable search response shape owned by Spring.
- Decide which internal asset states are enough for phase 1 once indexing is added.
- Keep the product API asset-centric and avoid leaking legacy FastAPI concepts into search.

### Verification

- Run the thin slice end to end with Repo A and Repo B separately.
- Verify that empty-transcript assets are excluded from any indexing/search path.
- Add a minimal manual verification checklist for indexing and search.

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
9. If indexing is completed in this sprint, show transcript indexing into Elasticsearch.
10. If search is completed in this sprint, show product-facing search through Spring and explicitly note that it does not depend on FastAPI `/videos/search`.

## Sprint Outcome Checklist

- [x] Product-facing upload flow exists in Spring
- [x] Upstream FastAPI upload integration works
- [x] `task_id` and `video_id` are persisted in Spring
- [x] On-demand polling or status refresh works
- [x] Transcript rows can be fetched from FastAPI
- [x] Empty transcript handling is explicit
- [ ] Transcript rows can be indexed into Elasticsearch
- [ ] Product-facing search returns results from Spring
- [ ] Thin slice is demoable locally with indexing and search included
