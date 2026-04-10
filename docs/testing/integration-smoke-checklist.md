# Integration Smoke Checklist

This checklist reflects the current `workspace-core` implementation in Repo B. It focuses on the product-facing Spring Boot endpoints that are implemented and testable now.

The helper script at `infra/scripts/smoke-thin-slice.sh` covers the current default-workspace happy path and can exercise a non-default workspace path when `SMOKE_WORKSPACE_NAME` is set.

## Helper Shortcut

Default-workspace path:

```bash
./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

Non-default workspace path:

```bash
SMOKE_WORKSPACE_NAME="Algorithms" ./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

With `SMOKE_WORKSPACE_NAME`, the helper creates a workspace, reads it back, uploads into it, checks workspace-scoped asset listing, indexes the transcript, and searches within that workspace.

## 1. Environment Readiness

### Implemented And Testable Now

- [ ] Copy `.env.example` to `.env` for Repo B.
- [ ] Start Repo B infrastructure with `docker compose --env-file .env -f infra/docker-compose.dev.yml up -d`.
- [ ] Confirm Repo B PostgreSQL is up.
- [ ] Confirm Repo B PostgreSQL is using the intended host port `5434`.
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

## 3. Product Workspace Management Checks

### Implemented And Testable Now

- [ ] Call `POST /api/workspaces` with JSON body:
  - [ ] `name`
- [ ] Expect HTTP `201`.
- [ ] Expect JSON with:
  - [ ] `id`
  - [ ] `name`
  - [ ] `createdAt`
- [ ] Call `GET /api/workspaces`.
- [ ] Expect HTTP `200`.
- [ ] Confirm the created workspace appears in the list.
- [ ] Confirm the configured default workspace also appears once it has been created lazily or explicitly read.
- [ ] Call `GET /api/workspaces/{workspaceId}` for the created workspace.
- [ ] Expect HTTP `200`.
- [ ] Confirm the returned `id`, `name`, and `createdAt` match the created workspace.

### Failure Path

- [ ] Call `POST /api/workspaces` with a blank or missing `name`.
- [ ] Expect HTTP `400`.
- [ ] Call `GET /api/workspaces/not-a-uuid`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_WORKSPACE_ID"`
- [ ] Call `GET /api/workspaces/<valid-but-unknown-uuid>`.
- [ ] Expect HTTP `404`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "WORKSPACE_NOT_FOUND"`

## 4. Product Upload Flow Checks

### Implemented And Testable Now

- [ ] Call `POST /api/assets/upload` with multipart form data:
  - [ ] `file`
  - [ ] optional `workspaceId`
  - [ ] optional `title`
- [ ] Use a real lecture-video file as the happy-path sample.
- [ ] Expect HTTP `202`.
- [ ] Expect JSON with:
  - [ ] `assetId`
  - [ ] `processingJobId`
  - [ ] `assetStatus`
  - [ ] `workspaceId`
- [ ] Confirm the response does not expose raw `fastapiTaskId`.
- [ ] Confirm the response does not expose raw `fastapiVideoId`.
- [ ] If `workspaceId` was omitted, confirm the response uses the configured default workspace ID.
- [ ] Confirm initial `assetStatus` is:
  - [ ] `PROCESSING` for accepted upstream work
  - [ ] or `FAILED` if the upstream acknowledgment is already failed

### Failure Path

- [ ] Call `POST /api/assets/upload` without `file`.
- [ ] Expect HTTP `400`.
- [ ] Call `POST /api/assets/upload` with an empty file.
- [ ] Expect HTTP `400`.
- [ ] Call `POST /api/assets/upload` with `workspaceId=not-a-uuid`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_WORKSPACE_ID"`
- [ ] Call `POST /api/assets/upload` with a valid-but-unknown `workspaceId`.
- [ ] Expect HTTP `404`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "WORKSPACE_NOT_FOUND"`

### Upstream Validation Path

- [ ] If you can safely simulate invalid upstream upload behavior in a non-production FastAPI environment, verify that Repo B returns HTTP `502` when the upload response is missing required fields such as `task_id` or `video_id`.
- [ ] If that simulation is not practical, mark this check as pending instead of assuming it passed.

## 5. Product Asset Listing Checks

### Implemented And Testable Now

- [ ] Call `GET /api/assets` without `workspaceId`.
- [ ] Expect HTTP `200`.
- [ ] Confirm each row only contains:
  - [ ] `assetId`
  - [ ] `title`
  - [ ] `assetStatus`
  - [ ] `workspaceId`
  - [ ] `createdAt`
- [ ] Confirm omitted `workspaceId` uses the configured default workspace scope.
- [ ] If you uploaded into a known non-default workspace, call `GET /api/assets?workspaceId=<workspaceId>`.
- [ ] Confirm the uploaded asset appears in that workspace-scoped list.
- [ ] Confirm non-default workspace listing only returns assets in that workspace.

### Legacy Default-Workspace Path

- [ ] If you have older local assets with null `workspace_id`, call `GET /api/assets` without `workspaceId`.
- [ ] Confirm those legacy assets still appear in the default-workspace list.
- [ ] Confirm those rows are backfilled to the default workspace after the read path.

### Failure Path

- [ ] Call `GET /api/assets?workspaceId=not-a-uuid`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_WORKSPACE_ID"`
- [ ] Call `GET /api/assets?workspaceId=<valid-but-unknown-uuid>`.
- [ ] Expect HTTP `404`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "WORKSPACE_NOT_FOUND"`

## 6. Product Status Refresh Checks

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

## 7. Product Transcript Fetch Checks

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

## 8. Empty-Transcript Handling Checks

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

## 9. Structured Integration Error Handling Checks

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

## 10. Product Indexing Checks

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
- [ ] Call `POST /api/assets/{assetId}/index` again for the same asset.
- [ ] Expect HTTP `200` again.
- [ ] Confirm the asset stays `SEARCHABLE` and the rerun does not require cleanup before retrying.

### Failure Path

- [ ] Stop Elasticsearch or point Repo B at an unavailable Elasticsearch host.
- [ ] Call `POST /api/assets/{assetId}/index` for an asset with usable transcript rows.
- [ ] Expect HTTP `503` or `502` depending on the failure mode.
- [ ] If a structured integration error body is returned, confirm:
  - [ ] `code = "ELASTICSEARCH_UNAVAILABLE"` or `ELASTICSEARCH_INTEGRATION_ERROR`
  - [ ] non-empty `message`
- [ ] Call `GET /api/assets/{assetId}/status` after the failed indexing attempt.
- [ ] Confirm the asset is not incorrectly marked `SEARCHABLE`.

## 11. Product Search Checks

### Implemented And Testable Now

- [ ] Call `GET /api/search?q=your-query` after at least one asset has been indexed successfully.
- [ ] Expect HTTP `200`.
- [ ] Expect JSON with:
  - [ ] `query`
  - [ ] `workspaceIdFilter`
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
- [ ] Confirm `workspaceIdFilter` matches the requested workspace or the configured default workspace when omitted.
- [ ] Confirm search only returns results inside the resolved workspace scope.
- [ ] Confirm search returns only assets that were successfully indexed and are `SEARCHABLE`.

### Optional Asset Filter Check

- [ ] Call `GET /api/search?q=your-query&assetId=<known-asset-id>`.
- [ ] Confirm results are restricted to that asset.

### Optional Workspace Filter Check

- [ ] If you have a known non-default workspace ID in local data, call `GET /api/search?q=your-query&workspaceId=<known-workspace-id>`.
- [ ] Confirm `workspaceIdFilter` matches that workspace ID.
- [ ] Confirm results stay restricted to that workspace.
- [ ] If you use `SMOKE_WORKSPACE_NAME`, confirm the helper's printed `searchWorkspaceId` matches the created workspace ID.

### Validation Path

- [ ] Call `GET /api/search?q=` with a blank or whitespace-only query value.
- [ ] Expect HTTP `400`.
- [ ] Call `GET /api/search?q=test&workspaceId=not-a-uuid`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_WORKSPACE_ID"`
- [ ] Call `GET /api/search?q=test&workspaceId=<valid-but-unknown-uuid>`.
- [ ] Expect HTTP `404`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "WORKSPACE_NOT_FOUND"`

### Elasticsearch Failure Path

- [ ] Stop Elasticsearch or point Repo B at an unavailable Elasticsearch host.
- [ ] Call `GET /api/search?q=test`.
- [ ] Expect HTTP `503` or `502` depending on the failure mode.
- [ ] If a structured integration error body is returned, confirm:
  - [ ] `code = "ELASTICSEARCH_UNAVAILABLE"` or `ELASTICSEARCH_INTEGRATION_ERROR`
  - [ ] non-empty `message`

## 12. Not-Yet-In-Scope Checks

### Not Implemented Yet

- [ ] Auth-based workspace ownership enforcement
- [ ] Workspace management beyond the current create/list/read surface and default-workspace bootstrap
- [ ] Local transcript-table persistence
- [ ] Hybrid or vector-assisted ranking
- [ ] Search snippets, highlights, or richer retrieval UX

### Placeholder Checks For Later

- [ ] When auth exists, verify workspace ownership rules are enforced consistently on upload, asset reads, indexing, and search.
- [ ] When hybrid retrieval exists, verify lexical-only fallback behavior remains understandable.
- [ ] When transcript persistence exists, verify transcript fetch behavior stays consistent with the product API.
- [ ] Keep verifying that Repo B does not depend on FastAPI `/videos/search`.
