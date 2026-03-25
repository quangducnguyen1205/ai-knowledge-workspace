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

## 8. Not-Yet-In-Scope Checks

### Not Implemented Yet

- [ ] Elasticsearch indexing of transcript rows
- [ ] Product-facing search endpoint in Spring
- [ ] Verification that transcript rows become searchable after indexing
- [ ] Verification of search results scoped through the product search contract

### Placeholder Checks For Later

- [ ] When indexing exists, verify only non-empty transcripts are indexed.
- [ ] When indexing exists, verify failed or empty-transcript assets are excluded from indexing.
- [ ] When search exists, verify Repo B does not depend on FastAPI `/videos/search`.
- [ ] When search exists, verify search responses come from Spring’s product contract, not the legacy upstream shape.
