# Integration Smoke Checklist

This checklist reflects the current `workspace-core` implementation in Repo B. It focuses on the product-facing Spring Boot endpoints that are implemented and testable now.

## 1. Environment Readiness

### Implemented And Testable Now

- [ ] Copy `.env.example` to `.env` for Repo B.
- [ ] Start Repo B infrastructure with `docker compose --env-file .env -f infra/docker-compose.dev.yml up -d`.
- [ ] Confirm Repo B PostgreSQL is up.
- [ ] Start Spring Boot for `services/workspace-core`.
- [ ] Check Spring Boot health:
  - [ ] `curl http://localhost:8081/health`
  - [ ] Expect HTTP `200`
  - [ ] Expect JSON with:
    - [ ] `status = "UP"`
    - [ ] `service = "workspace-core"`

## 2. Upstream FastAPI Readiness

### Implemented And Testable Now

- [ ] Start Repo A separately.
- [ ] Confirm `FASTAPI_BASE_URL` points to the running Repo A instance.
- [ ] Check basic FastAPI reachability:
  - [ ] `curl http://localhost:8000/openapi.json`
  - [ ] Expect HTTP `200` if OpenAPI is enabled locally
- [ ] If OpenAPI is disabled, verify any known Repo A endpoint is reachable before testing Repo B.

### Failure Path

- [ ] Stop Repo A or point `FASTAPI_BASE_URL` to a bad host.
- [ ] Call one product endpoint that needs Repo A, such as upload or status refresh on a non-terminal asset.
- [ ] Expect Repo B to return HTTP `504`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "FASTAPI_CONNECTIVITY_ERROR"`
  - [ ] non-empty `message`

## 3. Product Upload Flow Checks

### Implemented And Testable Now

- [ ] Call `POST /api/assets/upload` with multipart form data:
  - [ ] `file`
  - [ ] optional `title`
- [ ] Use a real lecture-video file as the happy-path sample.
- [ ] Expect HTTP `202`.
- [ ] Expect JSON with:
  - [ ] `assetId`
  - [ ] `processingJobId`
  - [ ] `assetStatus`
- [ ] Confirm the response does not expose raw `fastapiTaskId`.
- [ ] Confirm the response does not expose raw `fastapiVideoId`.
- [ ] Confirm initial `assetStatus` is:
  - [ ] `PROCESSING` for accepted upstream work
  - [ ] or `FAILED` if the upstream acknowledgment is already failed

### Failure Path

- [ ] Call `POST /api/assets/upload` without `file`.
- [ ] Expect HTTP `400`.
- [ ] Call `POST /api/assets/upload` with an empty file.
- [ ] Expect HTTP `400`.

### Upstream Validation Path

- [ ] If you can safely simulate invalid upstream upload behavior in a non-production FastAPI environment, verify that Repo B returns HTTP `502` when the upload response is missing required fields such as `task_id` or `video_id`.
- [ ] If that simulation is not practical, mark this check as pending instead of assuming it passed.

## 4. Product Status Refresh Checks

### Implemented And Testable Now

- [ ] After a successful upload, call `GET /api/assets/{assetId}/status`.
- [ ] Expect HTTP `200`.
- [ ] Expect JSON with:
  - [ ] `assetId`
  - [ ] `processingJobId`
  - [ ] `assetStatus`
  - [ ] `processingJobStatus`
- [ ] While upstream work is still in progress, expect `processingJobStatus` to move through:
  - [ ] `PENDING` or `RUNNING`
- [ ] Confirm `assetStatus` remains `PROCESSING` while the job is not terminal.
- [ ] After terminal upstream failure, expect:
  - [ ] `processingJobStatus = FAILED`
  - [ ] `assetStatus = FAILED`
- [ ] After terminal upstream success, expect:
  - [ ] `processingJobStatus = SUCCEEDED`
  - [ ] `assetStatus` to remain conservative until transcript is checked
- [ ] Confirm the endpoint remains asset-centric and does not expose raw FastAPI IDs.

### Failure Path

- [ ] Call `GET /api/assets/{assetId}/status` with a random UUID that does not exist.
- [ ] Expect HTTP `404`.
- [ ] If you have a DB fixture or manual record that can represent an asset without a linked processing job, call the status endpoint for that asset.
- [ ] Expect HTTP `409`.

### Upstream Invalid Payload Path

- [ ] If you can safely simulate an upstream task-status response without `status`, verify that Repo B returns HTTP `502`.
- [ ] If you cannot simulate that safely, mark this check as pending.

## 5. Product Transcript Fetch Checks

### Implemented And Testable Now

- [ ] Call `GET /api/assets/{assetId}/transcript` only after the status response shows `processingJobStatus = SUCCEEDED`.
- [ ] Expect HTTP `200` when transcript rows are present.
- [ ] Confirm the response is a JSON array ordered by transcript `segmentIndex`.
- [ ] Confirm each row only contains:
  - [ ] `id`
  - [ ] `videoId`
  - [ ] `segmentIndex`
  - [ ] `text`
  - [ ] `createdAt`
- [ ] Confirm there are no invented fields such as:
  - [ ] timestamps
  - [ ] speaker labels
  - [ ] chunk IDs
  - [ ] snippet fields

### Non-Ready Path

- [ ] Call `GET /api/assets/{assetId}/transcript` before the processing job reaches `SUCCEEDED`.
- [ ] Expect HTTP `409`.
- [ ] Expect a clear message that transcript is not ready until processing reaches terminal success.

### Not Found Path

- [ ] Call `GET /api/assets/{assetId}/transcript` with a random UUID that does not exist.
- [ ] Expect HTTP `404`.

## 6. Empty-Transcript Handling Checks

### Implemented And Testable Now

- [ ] Use or create a case where upstream task processing succeeds but transcript rows are empty.
- [ ] Call `GET /api/assets/{assetId}/transcript`.
- [ ] Expect HTTP `409`.
- [ ] Expect a clear message that the transcript is empty for this asset.
- [ ] Confirm the asset is not treated as transcript-ready.
- [ ] Confirm the asset is not treated as searchable.
- [ ] Confirm a later status read does not show the asset as usable.

### Notes

- [ ] Treat this as a required smoke path, not an edge path to skip.
- [ ] Do not record this case as a pass just because upstream processing succeeded.

## 7. Structured Integration Error Handling Checks

### Implemented And Testable Now

- [ ] Verify upstream connectivity failure returns HTTP `504`.
- [ ] Verify upstream connectivity failure returns JSON:
  - [ ] `code = "FASTAPI_CONNECTIVITY_ERROR"`
  - [ ] non-empty `message`
- [ ] Verify upstream HTTP failure or malformed upstream behavior returns HTTP `502`.
- [ ] Verify upstream integration failure returns JSON:
  - [ ] `code = "FASTAPI_INTEGRATION_ERROR"`
  - [ ] non-empty `message`

### Suggested Runnable Cases

- [ ] Repo A down or unreachable -> expect `504`
- [ ] Repo A returns `4xx` or `5xx` for upload/status/transcript -> expect `502`
- [ ] Repo A returns invalid response body for a required contract -> expect `502`

## 8. Product Indexing Checks

### Implemented And Testable Now

- [ ] Use an asset that already has a non-empty transcript response through Spring.
- [ ] Call `POST /api/assets/{assetId}/index`.
- [ ] Expect HTTP `200`.
- [ ] Expect JSON with:
  - [ ] `assetId`
  - [ ] `assetStatus = "SEARCHABLE"`
  - [ ] `indexedDocumentCount` greater than `0`
- [ ] Call `GET /api/assets/{assetId}/status` after indexing.
- [ ] Confirm the returned `assetStatus` is `SEARCHABLE`.

### Failure Path

- [ ] Stop Elasticsearch or point Repo B at an unavailable Elasticsearch host.
- [ ] Call `POST /api/assets/{assetId}/index` for an asset with usable transcript rows.
- [ ] Expect HTTP `503` or `502` depending on the failure mode.
- [ ] If a structured integration error body is returned, confirm:
  - [ ] `code = "ELASTICSEARCH_UNAVAILABLE"` or `ELASTICSEARCH_INTEGRATION_ERROR`
  - [ ] non-empty `message`
- [ ] Call `GET /api/assets/{assetId}/status` after the failed indexing attempt.
- [ ] Confirm the asset is not incorrectly marked `SEARCHABLE`.

## 9. Product Search Checks

### Implemented And Testable Now

- [ ] Call `GET /api/search?q=your-query` after at least one asset has been indexed successfully.
- [ ] Expect HTTP `200`.
- [ ] Expect JSON with:
  - [ ] `query`
  - [ ] `assetIdFilter`
  - [ ] `resultCount`
  - [ ] `results`
- [ ] Confirm each result only contains:
  - [ ] `assetId`
  - [ ] `assetTitle`
  - [ ] `transcriptRowId`
  - [ ] `segmentIndex`
  - [ ] `text`
  - [ ] `createdAt`
  - [ ] `score`
- [ ] Confirm results come from Spring's product response shape, not a FastAPI search response.
- [ ] Confirm search returns only assets that were successfully indexed and are `SEARCHABLE`.

### Optional Asset Filter Check

- [ ] Call `GET /api/search?q=your-query&assetId=<known-asset-id>`.
- [ ] Confirm results are restricted to that asset.

### Validation Path

- [ ] Call `GET /api/search?q=` with a blank or whitespace-only query value.
- [ ] Expect HTTP `400`.

### Elasticsearch Failure Path

- [ ] Stop Elasticsearch or point Repo B at an unavailable Elasticsearch host.
- [ ] Call `GET /api/search?q=test`.
- [ ] Expect HTTP `503` or `502` depending on the failure mode.
- [ ] If a structured integration error body is returned, confirm:
  - [ ] `code = "ELASTICSEARCH_UNAVAILABLE"` or `ELASTICSEARCH_INTEGRATION_ERROR`
  - [ ] non-empty `message`

## 10. Not-Yet-In-Scope Checks

### Not Implemented Yet

- [ ] Real workspace-scoped search enforcement
- [ ] Local transcript-table persistence
- [ ] Hybrid or vector-assisted ranking
- [ ] Search snippets, highlights, or richer retrieval UX

### Placeholder Checks For Later

- [ ] When workspace persistence exists, verify search and indexing respect product-side ownership scope.
- [ ] When hybrid retrieval exists, verify lexical-only fallback behavior remains understandable.
- [ ] When transcript persistence exists, verify transcript fetch behavior stays consistent with the product API.
- [ ] Keep verifying that Repo B does not depend on FastAPI `/videos/search`.
