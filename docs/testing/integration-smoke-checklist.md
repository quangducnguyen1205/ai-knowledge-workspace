# Integration Smoke Checklist

This checklist reflects the current `workspace-core` implementation in Repo B. It focuses on the product-facing Spring Boot endpoints that are implemented and testable now.

The helper script at `infra/scripts/smoke-thin-slice.sh` covers the current default-workspace happy path and can exercise a non-default workspace path when `SMOKE_WORKSPACE_NAME` is set.

For this Phase 2 basic-auth slice, the primary product-facing current-user path is now session-based auth through register/login.
`POST /api/auth/session` and `X-Current-User-Id` remain available as secondary local/dev fallbacks.
If authenticated session, auth-session fallback, and header are all absent, Spring falls back to the configured local/dev default user.
The smoke helper now follows the authenticated product path by default and only uses the older auth-session shortcut when `SMOKE_USE_LEGACY_AUTH_FALLBACK=1` is set explicitly.

## Helper Shortcut

Default-workspace path:

```bash
./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

Non-default workspace path:

```bash
SMOKE_WORKSPACE_NAME="Algorithms" ./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

Optional search-to-context follow-up:

```bash
SMOKE_VERIFY_CONTEXT=1 ./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

Local shortcut targets:

```bash
make test-workspace-core
make smoke MEDIA_FILE=/absolute/path/to/lecture-video.mp4
make smoke-workspace MEDIA_FILE=/absolute/path/to/lecture-video.mp4 WORKSPACE_NAME="Algorithms"
```

Authenticated smoke overrides:

```bash
make smoke \
  MEDIA_FILE=/absolute/path/to/lecture-video.mp4 \
  SMOKE_AUTH_EMAIL="smoke-user@example.com" \
  SMOKE_AUTH_PASSWORD="password123"
```

Explicit legacy fallback override:

```bash
make smoke \
  MEDIA_FILE=/absolute/path/to/lecture-video.mp4 \
  SMOKE_USE_LEGACY_AUTH_FALLBACK=1 \
  SMOKE_LEGACY_USER_ID="smoke-dev-user"
```

With `SMOKE_WORKSPACE_NAME`, the helper creates a workspace, reads it back, uploads into it, checks workspace-scoped asset listing, indexes the transcript, and searches within that workspace.
With `SMOKE_VERIFY_CONTEXT`, the helper also uses the top search hit to call `GET /api/assets/{assetId}/transcript/context` and prints the returned row window.
The `Makefile` smoke targets require `MEDIA_FILE` explicitly so the repo does not assume a contributor-specific local file path.
The helper now establishes an authenticated backend session with register/login before running the golden path.
If register returns `EMAIL_ALREADY_REGISTERED`, the helper falls back to login with the same credentials so reruns stay repeatable.
Use the legacy fallback path only when you intentionally want a local/dev shortcut rather than the main authenticated product flow.

## 0. Verification Order

- [ ] Run backend smoke against `http://localhost:8081` first.
- [ ] Only treat FE proxy checks as the next layer after backend smoke passes.
- [ ] If backend smoke fails before upload, classify it first as environment, auth-session setup, or Spring runtime issue.
- [ ] If upload or non-terminal status refresh fails with `FASTAPI_CONNECTIVITY_ERROR`, classify it first as an upstream FastAPI readiness/integration issue.
- [ ] If backend smoke passes but browser verification through `http://localhost:5173` fails, classify that first as FE proxy/runtime integration, not immediately as a backend product bug.

## 1. Environment Readiness

### Implemented And Testable Now

- [ ] Copy `.env.example` to `.env` for Repo B.
- [ ] Start Repo B infrastructure with `docker compose --env-file .env -f infra/docker-compose.dev.yml up -d`.
- [ ] Confirm Repo B PostgreSQL is up.
- [ ] Confirm Repo B PostgreSQL is using the intended host port `5434`.
- [ ] Confirm Repo B Elasticsearch is up on `9201`.
- [ ] Start Spring Boot for `services/workspace-core`.
- [ ] Check Spring Boot health:
  - [ ] `curl http://localhost:8081/health`
  - [ ] Expect HTTP `200`
  - [ ] Expect JSON with:
    - [ ] `status = "UP"`
    - [ ] `service = "workspace-core"`
- [ ] If you plan to verify the browser path too, start the frontend and confirm `http://localhost:5173` is serving the app shell before treating FE failures as product bugs.

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

## 2A. Product Auth Entry Checks

### Implemented And Testable Now

- [ ] Call `POST /api/auth/register` with JSON body:
  - [ ] `email`
  - [ ] `password`
- [ ] Expect HTTP `201`.
- [ ] Expect JSON with:
  - [ ] `id`
  - [ ] `email`
- [ ] Reuse the returned session cookie for `GET /api/me`.
- [ ] Expect `GET /api/me` to return the same `id` and `email`.
- [ ] Call `POST /api/auth/logout`.
- [ ] Expect HTTP `204`.
- [ ] Call `GET /api/me` again with the same cookie.
- [ ] Expect HTTP `401` with `code = "AUTHENTICATION_REQUIRED"`.
- [ ] Call `POST /api/auth/login` with the same email/password.
- [ ] Expect HTTP `200`.
- [ ] Reuse the returned session cookie for subsequent workspace, asset, and search checks.
- [ ] Confirm the default smoke helper now performs this authenticated setup automatically unless you opt into legacy fallback mode.

### Failure Path

- [ ] Call `POST /api/auth/register` with missing body.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_AUTH_REQUEST"`
- [ ] Call `POST /api/auth/register` with malformed email or short password.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_EMAIL"` or `code = "INVALID_PASSWORD"`
- [ ] Call `POST /api/auth/login` with valid-looking but wrong credentials.
- [ ] Expect HTTP `401`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_CREDENTIALS"`
- [ ] Call `GET /api/me` without an authenticated session.
- [ ] Expect HTTP `401`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "AUTHENTICATION_REQUIRED"`

### Local/Dev Fallback Path

- [ ] Call `POST /api/auth/session` with JSON body:
  - [ ] `userId`
- [ ] Expect HTTP `200`.
- [ ] Expect JSON with:
  - [ ] `userId`
- [ ] Repeat the same endpoint with a different `userId`.
- [ ] Confirm the active session user changes to the new value.
- [ ] Call `POST /api/auth/session` with an empty or missing `userId`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_CURRENT_USER_ID"`

## 3. Product Workspace Management Checks

### Implemented And Testable Now

- [ ] Call `GET /api/workspaces` after establishing an authenticated user through register/login or the local/dev auth-session fallback.
- [ ] Confirm Spring returns workspaces for that session user.
- [ ] Call `POST /api/workspaces` with JSON body:
  - [ ] `name`
- [ ] Expect HTTP `201`.
- [ ] Expect JSON with:
  - [ ] `id`
  - [ ] `name`
  - [ ] `createdAt`
- [ ] Call `GET /api/workspaces`.
- [ ] Expect HTTP `200`.
- [ ] Confirm the created workspace appears in the list for that current user.
- [ ] Confirm the current user's default workspace also appears once it has been created lazily or explicitly read.
- [ ] Call `GET /api/workspaces/{workspaceId}` for the created workspace.
- [ ] Expect HTTP `200`.
- [ ] Confirm the returned `id`, `name`, and `createdAt` match the created workspace.
- [ ] Re-authenticate as a different user.
- [ ] Confirm the first user's non-default workspace does not appear.
- [ ] Confirm a separate default workspace is created lazily for the second user if needed.

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
- [ ] Call `GET /api/workspaces/{workspaceId}` for a workspace created under one user, but first re-establish the auth session as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.

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
- [ ] If `workspaceId` was omitted, confirm the response uses the current user's default workspace.
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
- [ ] Confirm the response is a paginated envelope with:
  - [ ] `items`
  - [ ] `page`
  - [ ] `size`
  - [ ] `totalElements`
  - [ ] `totalPages`
  - [ ] `hasNext`
- [ ] Confirm each row inside `items` only contains:
  - [ ] `assetId`
  - [ ] `title`
  - [ ] `assetStatus`
  - [ ] `workspaceId`
  - [ ] `createdAt`
- [ ] Confirm omitted `workspaceId` uses the current user's default workspace scope.
- [ ] Call `GET /api/assets?page=0&size=1`.
- [ ] Confirm paging metadata changes consistently with the returned subset.
- [ ] Call `GET /api/assets?assetStatus=SEARCHABLE`.
- [ ] Confirm only `SEARCHABLE` assets are returned inside the resolved workspace scope.
- [ ] If you uploaded into a known non-default workspace, call `GET /api/assets?workspaceId=<workspaceId>`.
- [ ] Confirm the uploaded asset appears in that workspace-scoped list.
- [ ] Confirm non-default workspace listing only returns assets in that workspace.
- [ ] Re-authenticate as a different user, then call `GET /api/assets`.
- [ ] Confirm assets in another user's workspace do not appear.

### Legacy Default-Workspace Path

- [ ] If you have older local assets with null `workspace_id`, call `GET /api/assets` without `workspaceId`.
- [ ] Confirm those legacy assets still appear only for the configured local/dev default user.
- [ ] Confirm those rows are backfilled to the current user's default workspace after the read path.

### Failure Path

- [ ] Call `GET /api/assets?workspaceId=not-a-uuid`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_WORKSPACE_ID"`
- [ ] Call `GET /api/assets?page=-1`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_ASSET_PAGE"`
- [ ] Call `GET /api/assets?size=0`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_ASSET_SIZE"`
- [ ] Call `GET /api/assets?size=101`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_ASSET_SIZE"`
- [ ] Call `GET /api/assets?assetStatus=NOT_A_REAL_STATUS`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_ASSET_STATUS"`
- [ ] Call `GET /api/assets?workspaceId=<valid-but-unknown-uuid>`.
- [ ] Expect HTTP `404`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "WORKSPACE_NOT_FOUND"`
- [ ] Call `GET /api/assets?workspaceId=<workspace-owned-by-another-user>`.
- [ ] Expect the same ownership-safe HTTP `404`.

## 5A. Product Asset Read Checks

### Implemented And Testable Now

- [ ] Call `GET /api/assets/{assetId}` for an asset owned by the current user.
- [ ] Expect HTTP `200`.
- [ ] Confirm the returned asset matches the persisted asset metadata.

### Ownership Path

- [ ] Call `GET /api/assets/{assetId}` for an asset created under one user, but first re-authenticate as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.

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
- [ ] Call `GET /api/assets/{assetId}/status` for an asset created under one user, but first re-establish the auth session as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.
- [ ] If you have a DB fixture or manual record that can represent an asset without a linked processing job, call the status endpoint for that asset.
- [ ] Expect HTTP `409`.

### Upstream Invalid Payload Path

- [ ] If you can safely simulate an upstream task-status response without `status`, verify that Repo B returns HTTP `502`.
- [ ] If you cannot simulate that safely, mark this check as pending.

## 6A. Product Asset Deletion Checks

### Implemented And Testable Now

- [ ] Call `DELETE /api/assets/{assetId}` for an asset in any current state:
  - [ ] `PROCESSING`
  - [ ] `TRANSCRIPT_READY`
  - [ ] `SEARCHABLE`
  - [ ] `FAILED`
- [ ] Expect HTTP `204`.
- [ ] Call `GET /api/assets/{assetId}` afterward.
- [ ] Expect HTTP `404`.
- [ ] Call `GET /api/assets/{assetId}/transcript` afterward.
- [ ] Expect HTTP `404`, which also confirms the local transcript snapshot was removed with the asset.
- [ ] If the deleted asset was `SEARCHABLE`, call `GET /api/search?q=...&assetId=<deleted-asset-id>` after deletion.
- [ ] Confirm search no longer returns hits for that deleted asset.

### Failure Path

- [ ] Call `DELETE /api/assets/<random-uuid>` for an asset that does not exist.
- [ ] Expect HTTP `404`.
- [ ] Call `DELETE /api/assets/{assetId}` for an asset created under one user, but first re-establish the auth session as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.
- [ ] If possible, stop Elasticsearch and call `DELETE /api/assets/{assetId}` for a `SEARCHABLE` asset.
- [ ] Expect HTTP `503` or `502` depending on the failure mode.
- [ ] Confirm the local asset still exists after the failed delete attempt.

## 6B. Product Asset Title Update Checks

### Implemented And Testable Now

- [ ] Call `PATCH /api/assets/{assetId}` with JSON body:
  - [ ] `title`
- [ ] Expect HTTP `200`.
- [ ] Confirm the returned asset now includes the updated `title`.
- [ ] Repeat the same `PATCH` with a title that normalizes to the same stored value.
- [ ] Expect HTTP `200` again.
- [ ] Confirm the call behaves like a no-op success.
- [ ] If the asset is currently `SEARCHABLE`, call `GET /api/search?q=...&assetId=<assetId>` after the patch.
- [ ] Confirm returned search hits now reflect the updated `assetTitle`.

### Failure Path

- [ ] Call `PATCH /api/assets/{assetId}` with `{"title":"   "}`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_ASSET_TITLE"`
- [ ] Call `PATCH /api/assets/{assetId}` with a title longer than the current max length.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_ASSET_TITLE"`
- [ ] Call `PATCH /api/assets/<random-uuid>` with a valid title.
- [ ] Expect HTTP `404`.
- [ ] Call `PATCH /api/assets/{assetId}` for an asset created under one user, but first re-establish the auth session as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.
- [ ] If possible, stop Elasticsearch and call `PATCH /api/assets/{assetId}` for a `SEARCHABLE` asset.
- [ ] Expect HTTP `503` or `502` depending on the failure mode.
- [ ] Confirm the persisted asset title is unchanged after the failed patch attempt.

## 7. Product Transcript Fetch Checks

### Implemented And Testable Now

- [ ] Call `GET /api/assets/{assetId}/transcript` only after the status response shows `processingJobStatus = SUCCEEDED`.
- [ ] Expect HTTP `200` when transcript rows are present.
- [ ] Treat the first successful transcript read as the point where Spring can capture the local product-owned transcript snapshot if it is still missing.
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
- [ ] Call `GET /api/assets/{assetId}/transcript` for an asset created under one user, but first re-establish the auth session as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.

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
- [ ] Treat indexing as operating from the local transcript snapshot in the normal path.
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
- [ ] If you can safely simulate a partial Elasticsearch bulk item failure, confirm the whole indexing request still fails and the asset remains non-`SEARCHABLE`.
- [ ] Call `POST /api/assets/{assetId}/index` for an asset created under one user, but first re-establish the auth session as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.

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
- [ ] Confirm `workspaceIdFilter` matches the requested workspace or the current user's default workspace when omitted.
- [ ] Confirm search only returns results inside the resolved workspace scope.
- [ ] Confirm search returns only assets that were successfully indexed and are `SEARCHABLE`.
- [ ] If you know a short phrase that appears verbatim in one transcript row or asset title, search for that phrase and confirm the obvious phrase match rises near the top of the returned results.
- [ ] Re-establish the auth session as a different user, then call `GET /api/search?q=your-query`.
- [ ] Confirm results from another user's workspace do not appear.

### Optional Asset Filter Check

- [ ] Call `GET /api/search?q=your-query&assetId=<known-asset-id>`.
- [ ] Confirm results are restricted to that asset.

### Optional Workspace Filter Check

- [ ] If you have a known non-default workspace ID in local data, call `GET /api/search?q=your-query&workspaceId=<known-workspace-id>`.
- [ ] Confirm `workspaceIdFilter` matches that workspace ID.
- [ ] Confirm results stay restricted to that workspace.
- [ ] If you use `SMOKE_WORKSPACE_NAME`, confirm the helper's printed `searchWorkspaceId` matches the created workspace ID.

### Search-To-Context Follow-Up

- [ ] After a successful search, pick one result's `transcriptRowId`.
- [ ] If the chosen result has no `transcriptRowId`, use `segment-{segmentIndex}` only for that row.
- [ ] Call `GET /api/assets/{assetId}/transcript/context?transcriptRowId=<rowId>`.
- [ ] Expect HTTP `200`.
- [ ] Confirm the response contains:
  - [ ] `assetId`
  - [ ] `transcriptRowId`
  - [ ] `hitSegmentIndex`
  - [ ] `window`
  - [ ] `rows`
- [ ] Confirm each context row only contains:
  - [ ] `id`
  - [ ] `videoId`
  - [ ] `segmentIndex`
  - [ ] `text`
  - [ ] `createdAt`
- [ ] Call the same endpoint with `window=0`.
- [ ] Expect HTTP `400`.
- [ ] Call the same endpoint with a valid-but-missing `transcriptRowId`.
- [ ] Expect HTTP `404`.
- [ ] Call `GET /api/assets/{assetId}/transcript/context?transcriptRowId=<rowId>` for an asset created under one user, but first re-establish the auth session as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.

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
- [ ] Call `GET /api/search?q=test&workspaceId=<workspace-owned-by-another-user>`.
- [ ] Expect the same ownership-safe HTTP `404`.

### Elasticsearch Failure Path

- [ ] Stop Elasticsearch or point Repo B at an unavailable Elasticsearch host.
- [ ] Call `GET /api/search?q=test`.
- [ ] Expect HTTP `503` or `502` depending on the failure mode.
- [ ] If a structured integration error body is returned, confirm:
  - [ ] `code = "ELASTICSEARCH_UNAVAILABLE"` or `ELASTICSEARCH_INTEGRATION_ERROR`
  - [ ] non-empty `message`

## 12. Not-Yet-In-Scope Checks

### Not Implemented Yet

- [ ] Workspace management beyond the current create/list/read surface and default-workspace bootstrap
- [ ] Hybrid or vector-assisted ranking
- [ ] Search snippets, highlights, or richer retrieval UX

### Placeholder Checks For Later

- [ ] When hybrid retrieval exists, verify lexical-only fallback behavior remains understandable.
- [ ] Keep verifying that Repo B does not depend on FastAPI `/videos/search`.
