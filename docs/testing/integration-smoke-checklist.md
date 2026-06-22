# Integration Smoke Checklist

This checklist reflects the current `workspace-core` implementation in Repo B. It focuses on the product-facing Spring Boot endpoints that are implemented and testable now.

The helper script at `infra/scripts/smoke-thin-slice.sh` covers the current default-workspace happy path and can exercise a non-default workspace path when `SMOKE_WORKSPACE_NAME` is set.

For the current deployable-demo baseline, the supported verification order is:

1. Start Repo A.
2. Start Repo B infrastructure.
3. Start Repo B Spring Boot on the host.
4. Run backend smoke against `http://localhost:8081`.
5. Start Repo FE and verify the browser path through `http://localhost:5173`.

For the current backend slice, the primary product-facing current-user path is session-based auth through register/login.
`POST /api/auth/session` and `X-Current-User-Id` remain available as secondary local/dev fallbacks.
If authenticated session, auth-session fallback, and header are all absent, Spring can fall back to the configured local/dev default user when `CURRENT_USER_DEV_FALLBACK_ENABLED=true`.
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

For the default localhost path, the helper can still fall back to convenience smoke credentials if those values are omitted. For any non-local `WORKSPACE_CORE_BASE_URL`, set `SMOKE_AUTH_EMAIL` and `SMOKE_AUTH_PASSWORD` explicitly.

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
- [ ] If upload fails with `OBJECT_STORAGE_ERROR`, classify it first as a Repo B MinIO readiness or bucket configuration issue.
- [ ] If backend smoke passes but browser verification through `http://localhost:5173` fails, classify that first as FE proxy/runtime integration, not immediately as a backend product bug.

## 1. Environment Readiness

### Implemented And Testable Now

- [ ] Copy `.env.example` to `.env` for Repo B.
- [ ] Start Repo B infrastructure with `docker compose --env-file .env -f infra/docker-compose.dev.yml up -d`.
- [ ] Confirm Repo B PostgreSQL is up.
- [ ] Confirm Repo B PostgreSQL is using the intended host port `5434`.
- [ ] Confirm Repo B Elasticsearch is up on `9201`.
- [ ] Confirm Repo B MinIO is up on `9000`.
- [ ] Confirm the configured MinIO bucket exists, or that the `minio-create-bucket` compose helper completed successfully.
- [ ] Confirm Repo B Kafka is up on `9092` if you are validating local Kafka infrastructure.
- [ ] Confirm the `kafka-create-topics` compose helper created `asset.processing.requested.v1`, `asset.processing.result.v1`, and `asset.indexing.requested.v1`.
- [ ] Confirm `asset.processing.requested.v1` has one partition and replication factor one with `kafka-topics.sh --describe`.
- [ ] Confirm `asset.processing.result.v1` has one partition and replication factor one with `kafka-topics.sh --describe`.
- [ ] Confirm `asset.indexing.requested.v1` has one partition and replication factor one with `kafka-topics.sh --describe`.
- [ ] Optionally produce and consume one harmless CLI test record directly through Kafka to verify the broker path without creating a fake product outbox row.
- [ ] Kafka is not required for the normal upload smoke path because `WORKSPACE_CORE_PROCESSING_TRIGGER_MODE=direct_upload` remains the default product trigger.
- [ ] Kafka request-path smoke should use `WORKSPACE_CORE_PROCESSING_TRIGGER_MODE=kafka_request`, `WORKSPACE_CORE_KAFKA_ENABLED=true`, `WORKSPACE_CORE_OUTBOX_RELAY_ENABLED=true`, `WORKSPACE_CORE_PROCESSING_SMOKE_COMMAND=relay_request_outbox_once`, and `WORKSPACE_CORE_PROCESSING_SMOKE_REQUEST_OUTBOX_EVENT_ID=<outbox-event-id>` for an explicit scoped one-shot relay invocation. Do not relay request outbox rows for ordinary `direct_upload` uploads; the smoke command relays only the selected event ID and will not publish arbitrary due outbox rows.
- [ ] Kafka publishing requires idempotent future consumers; scheduled relay execution is not enabled.
- [ ] Spring result-event handling can run through either the one-shot local file handler or the disabled-by-default Kafka listener. For the manual file path, use `WORKSPACE_CORE_PROCESSING_SMOKE_COMMAND=handle_result_file_once` with a temporary result-envelope file.
- [ ] The Kafka result listener is off by default. Enable it only for a controlled local run with `WORKSPACE_CORE_KAFKA_PROCESSING_RESULT_LISTENER_ENABLED=true`; the default consumer group is `workspace-processing-result-v1`, and default offset reset is `latest`, so start the listener before publishing result events.
- [ ] Listener acknowledgement policy: `APPLIED`, duplicate already-applied, durable `FAILED`, and known malformed/unsupported records commit offsets with `MANUAL_IMMEDIATE`; unexpected runtime or infrastructure failures do not acknowledge and should redeliver. No retry topic, DLQ, or automated failed-event recovery is enabled.
- [ ] Durable `FAILED` result rows can be retried only through exact-ID operator recovery: `WORKSPACE_CORE_PROCESSING_RECOVERY_COMMAND=retry_failed_result_event_once` plus `WORKSPACE_CORE_PROCESSING_RECOVERY_RESULT_EVENT_ID=<result-event-id>`. This uses the retained bounded metadata-only envelope; it is not a scan of all failed rows.
- [ ] Stale request outbox rows in `PUBLISHING` can be requeued only through exact-ID operator recovery: `WORKSPACE_CORE_PROCESSING_RECOVERY_COMMAND=requeue_stuck_outbox_event_once`, `WORKSPACE_CORE_PROCESSING_RECOVERY_OUTBOX_EVENT_ID=<outbox-event-id>`, and `WORKSPACE_CORE_PROCESSING_RECOVERY_MINIMUM_PUBLISHING_AGE=<duration>`. Requeue does not publish; invoke the scoped request relay separately if needed.
- [ ] For manual result-event handling, confirm `payload.processingRequestId` equals `causationEventId` and matches `ProcessingJob.processingRequestEventId`; do not use `fastapiTaskId` for Kafka result correlation.
- [ ] Derived search indexing auto-request is disabled by default with `WORKSPACE_CORE_SEARCH_INDEXING_AUTO_REQUEST_ENABLED=false`.
- [ ] If validating the indexing event path later, use a stable Spring-owned transcript snapshot, enable auto-request explicitly, capture the selected `asset.indexing.requested` outbox event ID, and relay only that selected row with `WORKSPACE_CORE_SEARCH_SMOKE_COMMAND=relay_indexing_outbox_once` and `WORKSPACE_CORE_SEARCH_SMOKE_INDEXING_OUTBOX_EVENT_ID=<outbox-event-id>`.
- [ ] The indexing listener is off by default. Enable it only for a controlled local run with `WORKSPACE_CORE_KAFKA_INDEXING_LISTENER_ENABLED=true`; the default consumer group is `workspace-search-indexer-v1`, and default offset reset is `latest`.
- [ ] Indexing events must contain bounded metadata only: `assetId`, `indexingJobId`, and `snapshotFingerprint`. Do not include transcript text, object keys, raw media bytes, credentials, or stack traces.
- [ ] PostgreSQL prevents duplicate active indexing jobs for the same asset/fingerprint. Repeated indexing of an already-indexed identical snapshot should be a successful no-op, while a changed transcript snapshot must create or use a distinct current job.
- [ ] The derived `asset-transcript-rows` Elasticsearch index is created lazily and explicitly by the Spring indexing write path when absent. A clean local Elasticsearch environment should not require manual index pre-creation before the selected indexing event is consumed.
- [ ] P3-B2 verified the controlled runtime path with Kafka and Elasticsearch: a stable Spring-owned snapshot produced one indexing job and one metadata-only outbox event, a scoped relay published exactly that selected event, the disabled-by-default indexing listener marked the job `INDEXED` and asset `SEARCHABLE`, search/context APIs returned the expected selected asset, and PostgreSQL state blocked stale Elasticsearch documents after the asset was set back to `TRANSCRIPT_READY`. This smoke did not run FastAPI media processing.
- [ ] Indexing completion rechecks the current transcript fingerprint before marking an asset `SEARCHABLE`; stale or superseded jobs must not make a newer snapshot searchable.
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
- [ ] Call `PATCH /api/workspaces/{workspaceId}` with a new `name`.
- [ ] Expect HTTP `200`.
- [ ] Confirm the workspace name is updated in both the direct read and workspace list.
- [ ] Call `DELETE /api/workspaces/{workspaceId}` only after confirming it contains no assets.
- [ ] Expect HTTP `204`.
- [ ] Confirm the deleted workspace no longer appears in the visible workspace list.
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
- [ ] Call `PATCH /api/workspaces/{workspaceId}` with a blank or missing `name`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_WORKSPACE_NAME"`
- [ ] Call `DELETE /api/workspaces/{workspaceId}` for the current user's default workspace.
- [ ] Expect HTTP `409`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "DEFAULT_WORKSPACE_DELETE_FORBIDDEN"`
- [ ] Call `DELETE /api/workspaces/{workspaceId}` for a non-default workspace that still contains assets.
- [ ] Expect HTTP `409`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "WORKSPACE_NOT_EMPTY"`
- [ ] Call `GET /api/workspaces/{workspaceId}` for a workspace created under one user, but first re-establish the auth session as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.
- [ ] Call `PATCH /api/workspaces/{workspaceId}` or `DELETE /api/workspaces/{workspaceId}` for a workspace owned by another user.
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
- [ ] Optional database sanity check: confirm the upload inserted one `asset.processing.requested` row in `outbox_events` with `event_version = 1` for the returned `assetId`.
- [ ] Confirm the outbox payload stores metadata and MinIO object references only, not raw media bytes or secrets.

### Failure Path

- [ ] Call `POST /api/assets/upload` without `file`.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_UPLOAD_FILE"`
- [ ] Call `POST /api/assets/upload` with an empty file.
- [ ] Expect HTTP `400`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_UPLOAD_FILE"`
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

### Workspace Ownership Path

- [ ] Confirm every returned asset includes a workspace-owned path for the current user.
- [ ] If an older local database contains assets with null `workspace_id`, recreate or manually migrate that database before using the normal Project3 smoke path.

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
- [ ] Expect HTTP `404`, which confirms the transcript endpoint no longer exposes rows for the deleted asset. If you need DB-level proof of transcript-row cleanup, verify it separately from this application read.
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
- [ ] Expect structured error JSON with:
  - [ ] `code = "TRANSCRIPT_NOT_READY"`
  - [ ] message explaining that transcript is not ready until processing reaches terminal success

### Not Found Path

- [ ] Call `GET /api/assets/{assetId}/transcript` with a random UUID that does not exist.
- [ ] Expect HTTP `404`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "ASSET_NOT_FOUND"`
- [ ] Call `GET /api/assets/{assetId}/transcript` for an asset created under one user, but first re-establish the auth session as a different user.
- [ ] Expect the same ownership-safe HTTP `404`.

## 8. Empty-Transcript Handling Checks

### Implemented And Testable Now

- [ ] Use or create a case where upstream task processing succeeds but transcript rows are empty.
- [ ] Call `GET /api/assets/{assetId}/transcript`.
- [ ] Expect HTTP `409`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "TRANSCRIPT_NOT_USABLE"`
  - [ ] message explaining that the transcript is empty or unusable for this asset
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
- [ ] Verify object-storage failure during upload returns HTTP `502`.
- [ ] Verify object-storage failure returns JSON:
  - [ ] `code = "OBJECT_STORAGE_ERROR"`
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
- [ ] Confirm the asset stays `SEARCHABLE`; if the transcript snapshot has not changed, the rerun is idempotent and should not require another Elasticsearch write before reporting success.
- [ ] Optional async-indexing foundation check: after a transcript snapshot is stable and auto-request is explicitly enabled, confirm Product PostgreSQL has one `asset_search_index_jobs` row for the current snapshot fingerprint and one `asset.indexing.requested` outbox row. Mark it as listener smoke only when the disabled-by-default indexing listener is started before relay and the selected Kafka record is observed through to `INDEXED`.

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
- [ ] Confirm search returns only assets that are currently `SEARCHABLE` according to Product PostgreSQL, not merely according to stale Elasticsearch document metadata.
- [ ] If possible, create or retain stale Elasticsearch documents for an asset whose Product PostgreSQL status is not `SEARCHABLE`; confirm workspace search and asset-scoped search do not return them.
- [ ] If you know a short phrase that appears verbatim in one transcript row or asset title, search for that phrase and confirm the obvious phrase match rises near the top of the returned results.
- [ ] Re-establish the auth session as a different user, then call `GET /api/search?q=your-query`.
- [ ] Confirm results from another user's workspace do not appear.

### Optional Asset Filter Check

- [ ] Call `GET /api/search?q=your-query&assetId=<known-asset-id>`.
- [ ] Confirm `assetIdFilter` matches the requested asset ID.
- [ ] Confirm results are restricted to that asset.
- [ ] Confirm the asset belongs to the same resolved workspace scope used for the search call.

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
- [ ] Expect structured error JSON with:
  - [ ] `code = "INVALID_SEARCH_QUERY"`
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
- [ ] Call `GET /api/search?q=test&workspaceId=<owned-workspace-id>&assetId=<asset-from-a-different-workspace-or-another-user>`.
- [ ] Expect HTTP `404`.
- [ ] Expect structured error JSON with:
  - [ ] `code = "ASSET_NOT_FOUND"`

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
