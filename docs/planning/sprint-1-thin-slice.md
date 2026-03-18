# Sprint 1 Thin Slice

## Sprint Goal

Deliver the first end-to-end product slice in which Spring Boot accepts a lecture video upload, forwards it to the separate FastAPI processing service, stores the upstream `task_id` and `video_id`, tracks processing until completion, fetches transcript rows, indexes those rows into Elasticsearch, and exposes a simple product-facing search endpoint.

The goal is not a polished product. The goal is to prove the core product flow and the service boundary.

## User Stories

### Story 1: Upload And Start Processing

As a learner, I want to upload a lecture video through the product backend so that the system can begin processing it without requiring me to call FastAPI directly.

### Story 2: Track Processing Outcome

As a learner, I want the product backend to track the processing state of my uploaded asset so that I know whether transcript-based retrieval is available yet.

### Story 3: Search Recovered Transcript Content

As a learner, I want to search processed transcript content through the product backend so that I can recover the relevant explanation without interacting with legacy upstream endpoints directly.

## Acceptance Criteria

- Spring Boot exposes a product-facing upload endpoint for a lecture video.
- The upload flow forwards the media to FastAPI `POST /videos/upload`.
- Spring stores both upstream identifiers returned by FastAPI: `task_id` and `video_id`.
- Spring can retrieve task status from FastAPI `GET /videos/tasks/{task_id}`.
- Spring can fetch transcript rows from FastAPI `GET /videos/{video_id}/transcript`.
- The transcript row model used by Spring only depends on the confirmed upstream fields:
  - `id`
  - `video_id`
  - `segment_index`
  - `text`
  - `created_at`
- Spring does not use `/videos/search` as its product search contract.
- Transcript rows are indexed into Elasticsearch in a form that supports simple product-facing retrieval.
- Spring exposes a product-facing search endpoint that returns transcript-based results from Elasticsearch.
- The end-to-end path works for at least one lecture video in local development.
- If FastAPI reports a ready or successful state but transcript rows are empty, Spring handles that outcome explicitly instead of treating the asset as searchable by default.

## Engineering Tasks

### Product API And Domain

- Define a product-facing upload endpoint in Spring Boot.
- Define a product-facing status endpoint for assets or processing jobs.
- Define a simple product-facing search endpoint in Spring Boot.
- Add persistence models for `Asset` and `ProcessingJob`.
- Ensure `ProcessingJob` persists both `fastapiTaskId` and `fastapiVideoId`.
- Keep the workspace boundary simple and single-user for phase 1.

### FastAPI Integration

- Finalize the `FastApiProcessingClient` for upload, task lookup, video lookup, and transcript fetch.
- Confirm the minimum upstream response fields needed for `task status` and `video read`.
- Make upload handling work with the FastAPI task-oriented response contract.
- Add basic error handling for upstream timeout, upstream failure, and invalid upstream responses.

### Processing Flow

- Decide where Spring will trigger polling for task status in the first milestone.
- Implement a simple polling approach without Temporal or Kafka.
- Persist intermediate and terminal processing states in Spring.
- Add explicit handling for the edge case where FastAPI reports ready but transcript rows are empty.
- Prevent transcript fetch and indexing from being treated as successful if no rows are returned.

### Transcript Storage And Search

- Decide how transcript rows will be stored in Spring-owned persistence before or alongside indexing.
- Define the Elasticsearch document shape for transcript-row search.
- Index transcript rows with enough metadata to support asset-level and workspace-level filtering later.
- Implement a narrow search endpoint that returns transcript text results from Elasticsearch.
- Keep the first search implementation simple; no ranking tuning is required in this sprint.

### Local Development And Verification

- Verify the thin slice against the separate FastAPI repository running as Repo A.
- Use `FASTAPI_BASE_URL` as the only integration boundary to Repo A.
- Document the startup sequence and local verification steps.
- Add a minimal test plan covering upload, polling, transcript fetch, and search.

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

### Upstream Contract Gaps

The transcript row contract is partially verified, but the exact `task status` and `video read` shapes may still need confirmation.

### Empty Transcript Edge Case

FastAPI may report a ready or successful outcome even when the transcript is empty. This can create a false positive if Spring treats readiness as searchability.

### Polling Simplicity

Without Temporal, the first polling implementation should stay simple. A fragile or over-engineered polling design would be a poor use of sprint time.

### Search Scope Creep

It is easy to overreach into search ranking, embeddings strategy, or UI design. Sprint 1 should focus on proving the end-to-end data flow first.

### Separate Repo Coordination

Because Repo A and Repo B remain separate, local setup and contract verification can slow development if not kept explicit and lightweight.

## End-Of-Sprint Demo Script

1. Start Repo A and confirm FastAPI is reachable.
2. Start Repo B infrastructure and Spring Boot.
3. Call the Spring Boot upload endpoint with one lecture video.
4. Show that Spring returns a product response and stores the upstream `task_id` and `video_id`.
5. Show the Spring status endpoint while processing is still running or after it completes.
6. Show that Spring fetches transcript rows from FastAPI after processing completes.
7. Show transcript rows indexed into Elasticsearch.
8. Call the Spring product-facing search endpoint with a simple lecture query.
9. Show transcript-based search results returned by Spring.
10. Call out that the demo returns transcript text results only and does not promise timestamp-based seek.

## Sprint Outcome Checklist

- [ ] Product-facing upload flow exists in Spring
- [ ] Upstream FastAPI upload integration works
- [ ] `task_id` and `video_id` are persisted in Spring
- [ ] Polling or status refresh works for the first milestone
- [ ] Transcript rows can be fetched from FastAPI
- [ ] Empty transcript handling is explicit
- [ ] Transcript rows can be indexed into Elasticsearch
- [ ] Product-facing search returns results from Spring
- [ ] Thin slice is demoable locally with Repo A and Repo B running separately
