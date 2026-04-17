# Repo B API

## Purpose

This document is the current product-facing API summary for Repo B (`workspace-core`).

- Spring Boot is the product entry point.
- Repo A (FastAPI) remains an internal processing dependency.
- FastAPI `/videos/search` is not part of the product API.

## Current User Foundation

Repo B now uses a minimal current-user identity foundation for ownership-aware workspace scope.

- The primary product-facing path is now session-based basic auth through register/login.
- `POST /api/auth/register` and `POST /api/auth/login` establish the current user in the Spring HTTP session.
- `GET /api/me` reads the authenticated session user.
- `POST /api/auth/logout` clears the authenticated session.
- For local/dev support, Spring still accepts `POST /api/auth/session` and `X-Current-User-Id` as secondary fallbacks.
- If session, auth-session fallback, and header are all absent, Spring still falls back to the configured local/dev default user for ownership-aware local workflows.
- This slice is intentionally not a full authentication platform.
- Ownership is enforced first at the workspace boundary and then inherited by workspace-scoped asset listing, search, and asset-by-id flows.

## Current Product Endpoints

### `POST /api/auth/register`

Registers one minimal product user and establishes the authenticated Spring session.

Request:

- Content type: `application/json`
- Body:
  - `email` required
  - `password` required

Response:

- HTTP `201`
- Body:
  - `id`
  - `email`

Current behavior:

- Spring normalizes `email` by trimming and lowercasing before persistence.
- Passwords are stored as hashed values, not raw plaintext.
- Successful register also authenticates the new user into the current session.
- This slice intentionally does not add email verification, password reset, roles, or JWTs.

Common failure cases:

- HTTP `400` with `code = "INVALID_AUTH_REQUEST"` if the request body is missing
- HTTP `400` with `code = "INVALID_REQUEST_BODY"` if the request body is malformed JSON
- HTTP `400` with `code = "INVALID_EMAIL"` if `email` is missing, malformed, or too long
- HTTP `400` with `code = "INVALID_PASSWORD"` if `password` is missing, blank, too short, or too long
- HTTP `409` with `code = "EMAIL_ALREADY_REGISTERED"` if the normalized email already exists

### `POST /api/auth/login`

Authenticates one minimal product user and establishes the authenticated Spring session.

Request:

- Content type: `application/json`
- Body:
  - `email` required
  - `password` required

Response:

- HTTP `200`
- Body:
  - `id`
  - `email`

Current behavior:

- Spring normalizes `email` by trimming and lowercasing before credential lookup.
- Successful login stores the authenticated user in the Spring HTTP session.

Common failure cases:

- HTTP `400` with `code = "INVALID_AUTH_REQUEST"` if the request body is missing
- HTTP `400` with `code = "INVALID_REQUEST_BODY"` if the request body is malformed JSON
- HTTP `400` with `code = "INVALID_EMAIL"` if `email` is missing, malformed, or too long
- HTTP `400` with `code = "INVALID_PASSWORD"` if `password` is missing, blank, too short, or too long
- HTTP `401` with `code = "INVALID_CREDENTIALS"` if the email/password pair is not valid

### `POST /api/auth/logout`

Clears the authenticated Spring session.

Response:

- HTTP `204`

Current behavior:

- Logout is intentionally small and invalidates the current HTTP session when present.
- This slice does not add device/session management.

### `GET /api/me`

Reads the currently authenticated product user.

Response:

- HTTP `200`
- Body:
  - `id`
  - `email`

Current behavior:

- `GET /api/me` only uses the authenticated session user.
- Local/dev header or default-user fallback does not count as authenticated product auth for this endpoint.

Common failure cases:

- HTTP `401` with `code = "AUTHENTICATION_REQUIRED"` if no authenticated session user exists

### `POST /api/auth/session`

Establishes a secondary local/dev current-user session shortcut.

Request:

- Content type: `application/json`
- Body:
  - `userId` required

Response:

- HTTP `200`
- Body:
  - `userId`

Current behavior:

- This is now a local/dev fallback path rather than the primary product auth path.
- Spring trims `userId` before storing it in the session.
- Repeating the call replaces the current session user with the new `userId`.
- This remains useful for narrow local/dev ownership checks without going through register/login.

Common failure cases:

- HTTP `400` with `code = "INVALID_CURRENT_USER_ID"` if `userId` is missing, blank after trim, or longer than the current max length
- HTTP `400` with `code = "INVALID_REQUEST_BODY"` if the request body is malformed JSON

### `POST /api/workspaces`

Creates one workspace in Repo B.

Request:

- Content type: `application/json`
- Body:
  - `name` required

Response:

- HTTP `201`
- Body:
  - `id`
  - `name`
  - `createdAt`

Current behavior:

- This is a minimal product-owned workspace create endpoint.
- Spring trims the requested name before persisting.
- The created workspace is owned by the current user.
- Workspace create stays intentionally small and does not add sharing, collaboration, or a full auth model.

Common failure cases:

- HTTP `400` with `code = "INVALID_WORKSPACE_NAME"` if `name` is missing, blank, or longer than the current maximum length
- HTTP `400` with `code = "INVALID_REQUEST_BODY"` if the request body is malformed JSON

### `GET /api/workspaces`

Lists workspaces in Repo B.

Response:

- HTTP `200`
- Body: array of rows with:
  - `id`
  - `name`
  - `createdAt`

Current behavior:

- Spring ensures the current user's default workspace exists before returning the list.
- The list only returns workspaces owned by the current user.
- Results are intentionally minimal and do not include asset counts or membership data.

Common failure cases:

- HTTP `409` with `code = "DEFAULT_WORKSPACE_CONFLICT"` if the current user's default workspace state is internally conflicted
- HTTP `409` with `code = "DEFAULT_WORKSPACE_ID_CONFLICT"` if Spring cannot adopt or create the reserved default workspace safely

### `GET /api/workspaces/{workspaceId}`

Reads one workspace in Repo B.

Response:

- HTTP `200`
- Body:
  - `id`
  - `name`
  - `createdAt`

Current behavior:

- Workspace read is ownership-aware.
- The same `WORKSPACE_NOT_FOUND` response is used when a workspace does not exist or is not owned by the current user.

Common failure cases:

- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if the workspace does not exist or is not owned by the current user

### `PATCH /api/workspaces/{workspaceId}`

Updates one owned workspace name.

Request:

- Content type: `application/json`
- Body:
  - `name` required

Response:

- HTTP `200`
- Body:
  - `id`
  - `name`
  - `createdAt`

Current behavior:

- This v1 slice supports workspace name update only.
- Spring trims the requested name before validation and persistence.
- Ownership remains enforced through the same ownership-safe `WORKSPACE_NOT_FOUND` behavior.
- This slice does not change workspace membership, sharing, or default-workspace rules.

Common failure cases:

- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `400` with `code = "INVALID_WORKSPACE_NAME"` if `name` is missing, blank, or longer than the current max length
- HTTP `400` with `code = "INVALID_REQUEST_BODY"` if the request body is malformed JSON
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if the workspace does not exist or is not owned by the current user

### `DELETE /api/workspaces/{workspaceId}`

Deletes one owned workspace under conservative v1 rules.

Response:

- HTTP `204`

Current behavior:

- Only non-default workspaces can be deleted.
- Deletion is ownership-aware and uses the same ownership-safe `WORKSPACE_NOT_FOUND` response when the workspace does not exist or is not owned by the current user.
- Deletion is intentionally conservative in v1:
  - the workspace must not be the default workspace
  - the workspace must not still contain assets
- Spring does not silently migrate assets during workspace deletion.

Common failure cases:

- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if the workspace does not exist or is not owned by the current user
- HTTP `409` with `code = "DEFAULT_WORKSPACE_DELETE_FORBIDDEN"` if the workspace is the current user's default workspace
- HTTP `409` with `code = "WORKSPACE_NOT_EMPTY"` if the workspace still contains assets

### `GET /api/assets`

Lists assets in the resolved workspace scope with simple pagination.

Query parameters:

- `workspaceId` optional
- `page` optional, default `0`
- `size` optional, default `20`, max `100`
- `assetStatus` optional

Response:

- HTTP `200`
- Body:
  - `items`: array of rows with:
    - `assetId`
    - `title`
    - `assetStatus`
    - `workspaceId`
    - `createdAt`
  - `page`
  - `size`
  - `totalElements`
  - `totalPages`
  - `hasNext`

Current behavior:

- Spring resolves the requested `workspaceId`, or falls back to the current user's default workspace when omitted.
- Pagination and optional `assetStatus` filtering are applied inside the resolved workspace scope.
- Non-default workspace listing only returns assets already associated with that workspace.
- For the configured local/dev default user, default-workspace listing also includes older local assets whose `workspace_id` is still null.
- When that legacy path is used, Spring backfills those returned null-workspace assets to the current user's default workspace.
- Ordering is deterministic:
  - `createdAt desc`
  - tie-break by `assetId desc`
- Empty result sets return HTTP `200` with `items = []`.

Common failure cases:

- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `400` with `code = "INVALID_ASSET_PAGE"` if `page` is malformed or negative
- HTTP `400` with `code = "INVALID_ASSET_SIZE"` if `size` is malformed, non-positive, or greater than `100`
- HTTP `400` with `code = "INVALID_ASSET_STATUS"` if `assetStatus` is not one of the current product asset statuses
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if a provided `workspaceId` does not exist or is not owned by the current user
- HTTP `409` with `code = "DEFAULT_WORKSPACE_CONFLICT"` if `workspaceId` is omitted and the current user's default workspace state is internally conflicted
- HTTP `409` with `code = "DEFAULT_WORKSPACE_ID_CONFLICT"` if `workspaceId` is omitted and Spring cannot adopt or create the reserved default workspace safely

### `POST /api/assets/upload`

Starts the product-side upload flow for one media file.

Request:

- Content type: `multipart/form-data`
- Fields:
  - `file` required
  - `workspaceId` optional
  - `title` optional

Response:

- HTTP `202`
- Body:
  - `assetId`
  - `processingJobId`
  - `assetStatus`
  - `workspaceId`

Current behavior:

- Spring forwards `file` and `title` to FastAPI upload.
- Spring resolves the requested `workspaceId`, or falls back to the current user's default workspace when omitted.
- Spring validates the upstream response before persisting local state.
- Spring associates the created asset with one workspace in Repo B.
- Raw FastAPI IDs are stored internally but not returned to the client.

Common failure cases:

- HTTP `400` with `code = "INVALID_UPLOAD_FILE"` if `file` is missing or empty
- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if a provided `workspaceId` does not exist or is not owned by the current user
- HTTP `409` with `code = "DEFAULT_WORKSPACE_CONFLICT"` if `workspaceId` is omitted and the current user's default workspace state is internally conflicted
- HTTP `409` with `code = "DEFAULT_WORKSPACE_ID_CONFLICT"` if `workspaceId` is omitted and Spring cannot adopt or create the reserved default workspace safely
- HTTP `502` or `504` if upstream FastAPI fails

All asset-by-id endpoints below are ownership-aware through the asset's workspace.
Repo B uses the same ownership-safe HTTP `404` when an asset does not exist or is not owned by the current user.

### `GET /api/assets/{assetId}`

Reads one persisted asset record.

Response:

- HTTP `200`
- Body: the current persisted asset record

Current behavior:

- This remains a simple product-owned asset read endpoint.
- It is useful for debugging and local inspection.
- For the configured local/dev default user, if the asset still has no workspace association, Spring backfills it to that user's default workspace before returning it.

Common failure cases:

- HTTP `404` with `code = "ASSET_NOT_FOUND"` if the asset does not exist or is not owned by the current user

### `PATCH /api/assets/{assetId}`

Updates the product-owned asset title.

Request:

- Content type: `application/json`
- Body:
  - `title` required

Response:

- HTTP `200`
- Body: the updated persisted asset record

Current behavior:

- This v1 slice supports title-only update.
- Spring trims `title` before validation and persistence.
- If the normalized title is unchanged, Spring treats the request as a no-op success.
- For assets in `PROCESSING`, `TRANSCRIPT_READY`, or `FAILED`, Spring only updates the local DB title.
- For assets in `SEARCHABLE`, Spring first syncs `assetTitle` in Elasticsearch, then updates the local DB title.
- If searchable-asset Elasticsearch sync fails, Spring does not update the DB title.
- This slice does not move workspaces, replace files, or call FastAPI.

Common failure cases:

- HTTP `400` with `code = "INVALID_ASSET_TITLE"` if `title` is missing, blank after trim, or longer than the current max length
- HTTP `400` with `code = "INVALID_REQUEST_BODY"` if the request body is malformed JSON
- HTTP `404` with `code = "ASSET_NOT_FOUND"` if the asset does not exist or is not owned by the current user
- HTTP `503` if Elasticsearch is unavailable while syncing title metadata for a `SEARCHABLE` asset
- HTTP `502` if Elasticsearch returns an integration error while syncing title metadata for a `SEARCHABLE` asset

### `DELETE /api/assets/{assetId}`

Deletes one product-owned asset.

Response:

- HTTP `204`

Current behavior:

- Deletion is asset-centric and always removes the local `Asset` record.
- Deletion also removes any local transcript snapshot rows for that asset.
- Deletion also removes the linked `ProcessingJob` record in the same local DB transaction when it exists.
- Spring allows deletion for assets in `PROCESSING`, `TRANSCRIPT_READY`, `SEARCHABLE`, or `FAILED`.
- If the asset is currently `SEARCHABLE`, Spring first deletes that asset's transcript-row documents from Elasticsearch before deleting local DB records.
- Workspace records are never deleted by this endpoint.
- This slice does not call upstream FastAPI delete or cancel APIs.

Common failure cases:

- HTTP `404` with `code = "ASSET_NOT_FOUND"` if the asset does not exist or is not owned by the current user
- HTTP `503` if Elasticsearch is unavailable while deleting a `SEARCHABLE` asset
- HTTP `502` if Elasticsearch returns an integration error while deleting a `SEARCHABLE` asset

### `GET /api/assets/{assetId}/status`

Reads current product-side status for one asset.

Response:

- HTTP `200`
- Body:
  - `assetId`
  - `processingJobId`
  - `assetStatus`
  - `processingJobStatus`

Current behavior:

- Status is asset-centric.
- If the local processing job is already terminal, Spring returns stored state.
- If the local processing job is non-terminal, Spring polls FastAPI on demand and updates local state.

Common failure cases:

- HTTP `404` with `code = "ASSET_NOT_FOUND"` if the asset does not exist or is not owned by the current user
- HTTP `404` with `code = "PROCESSING_JOB_NOT_FOUND"` if the asset exists but its local processing job record is missing

### `GET /api/assets/{assetId}/transcript`

Returns transcript rows through Spring after processing reaches terminal success.

Response:

- HTTP `200`
- Body: array of rows with:
  - `id`
  - `videoId`
  - `segmentIndex`
  - `text`
  - `createdAt`

Current behavior:

- Spring serves transcript rows from a local product-owned transcript snapshot in the normal path.
- If no local snapshot exists yet but processing has already succeeded, Spring fetches transcript rows from FastAPI using stored `fastapiVideoId`, filters for usable transcript rows, persists that local snapshot, then returns it.
- Only the currently verified transcript fields are exposed.
- Transcript fetch is rejected until `processingJobStatus = SUCCEEDED`.
- Empty or unusable transcript is treated as not usable.

Common failure cases:

- HTTP `404` with `code = "ASSET_NOT_FOUND"` if the asset does not exist or is not owned by the current user
- HTTP `404` with `code = "PROCESSING_JOB_NOT_FOUND"` if the asset exists but its local processing job record is missing
- HTTP `409` with `code = "TRANSCRIPT_NOT_READY"` if processing has not reached terminal success yet
- HTTP `409` with `code = "TRANSCRIPT_NOT_USABLE"` if transcript rows are empty or unusable

### `GET /api/assets/{assetId}/transcript/context?transcriptRowId=...&window=...`

Returns a small Spring-owned transcript window around one transcript row.

Query parameters:

- `transcriptRowId` required
- `window` optional, default `2`, max `5`

Response:

- HTTP `200`
- Body:
  - `assetId`
  - `transcriptRowId`
  - `hitSegmentIndex`
  - `window`
  - `rows[]`

Each context row currently contains:

- `id`
- `videoId`
- `segmentIndex`
- `text`
- `createdAt`

Current behavior:

- Spring resolves transcript context through the same product-side transcript path used by `GET /api/assets/{assetId}/transcript`.
- Transcript context therefore uses the same local transcript snapshot in the normal path.
- Context rows are selected by transcript ordering on `segmentIndex`.
- If a transcript row has a real upstream `id`, context lookup matches only that `id`.
- The fallback identifier `segment-{segmentIndex}` is accepted only when the upstream transcript row `id` is missing or blank.
- The endpoint is intentionally narrow and does not add timestamps, speaker labels, snippet metadata, or transcript caching.

Common failure cases:

- HTTP `400` with `code = "INVALID_TRANSCRIPT_CONTEXT_WINDOW"` if `window` is malformed, zero, negative, or above the current maximum
- HTTP `404` with `code = "TRANSCRIPT_ROW_NOT_FOUND"` if the requested row does not belong to that asset transcript
- HTTP `404` with `code = "ASSET_NOT_FOUND"` if the asset does not exist or is not owned by the current user
- HTTP `404` with `code = "PROCESSING_JOB_NOT_FOUND"` if the asset exists but its local processing job record is missing
- HTTP `409` with `code = "TRANSCRIPT_NOT_READY"` if the transcript is not ready
- HTTP `409` with `code = "TRANSCRIPT_NOT_USABLE"` if transcript rows are empty or unusable

### `POST /api/assets/{assetId}/index`

Indexes usable transcript rows into Elasticsearch.

Response:

- HTTP `200`
- Body:
  - `assetId`
  - `assetStatus`
  - `indexedDocumentCount`

Current behavior:

- Indexing is explicit and product-side.
- Spring indexes from the local product-owned transcript snapshot in the normal path.
- If no local snapshot exists yet but processing has already succeeded, Spring captures that snapshot first through the same transcript path before indexing.
- Spring builds one logical Elasticsearch document per transcript row and writes them through one bulk indexing request per asset.
- Indexed transcript-row documents include `workspaceId`.
- Repeated indexing reuses stable transcript-row document IDs for the same asset and transcript row.
- Only usable non-empty transcript rows can be indexed.
- Successful indexing refreshes the transcript index before returning.
- Successful indexing marks the asset `SEARCHABLE`.
- Indexing failure does not collapse a usable asset back to `FAILED`.

Common failure cases:

- HTTP `404` with `code = "ASSET_NOT_FOUND"` if the asset does not exist or is not owned by the current user
- HTTP `404` with `code = "PROCESSING_JOB_NOT_FOUND"` if the asset exists but its local processing job record is missing
- HTTP `409` with `code = "TRANSCRIPT_NOT_READY"` if transcript data is not ready
- HTTP `409` with `code = "TRANSCRIPT_NOT_USABLE"` if transcript data is empty or unusable
- HTTP `503` if Elasticsearch is unavailable
- HTTP `502` if Elasticsearch returns an integration error

### `GET /api/search?q=...`

Runs the current Spring-owned product search against Elasticsearch.

Query parameters:

- `q` required
- `workspaceId` optional
- `assetId` optional

Response:

- HTTP `200`
- Body:
  - `query`
  - `workspaceIdFilter`
  - `assetIdFilter`
  - `resultCount`
  - `results[]`

Each result currently contains:

- `assetId`
- `assetTitle`
- `transcriptRowId`
- `segmentIndex`
- `text`
- `createdAt`
- `score`

Current behavior:

- Search is backed by Elasticsearch, not FastAPI.
- Spring resolves the requested `workspaceId`, or falls back to the current user's default workspace when omitted.
- Search only considers documents inside the resolved workspace scope owned by the current user.
- Only documents for assets already marked `SEARCHABLE` are eligible.
- `assetId` is an exact filter when provided.
- The current search baseline is still lexical search over transcript text and asset title.
- Spring keeps the baseline `multi_match` query and adds a small phrase-style boost layer so clearer exact or phrase-like matches can rise more appropriately.
- Search ordering is deterministic on score ties: `_score desc`, then `segmentIndex`, `assetId`, and `transcriptRowId`.

Common failure cases:

- HTTP `400` with `code = "INVALID_SEARCH_QUERY"` if `q` is missing or blank
- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if a provided `workspaceId` does not exist or is not owned by the current user
- HTTP `409` with `code = "DEFAULT_WORKSPACE_CONFLICT"` if `workspaceId` is omitted and the current user's default workspace state is internally conflicted
- HTTP `409` with `code = "DEFAULT_WORKSPACE_ID_CONFLICT"` if `workspaceId` is omitted and Spring cannot adopt or create the reserved default workspace safely
- HTTP `503` if Elasticsearch is unavailable
- HTTP `502` if Elasticsearch returns an integration error

## Error Shape Notes

Structured error responses currently use:

- `code`
- `message`

Default-workspace integrity conflicts currently use:

- `DEFAULT_WORKSPACE_CONFLICT`
- `DEFAULT_WORKSPACE_ID_CONFLICT`

Current structured error codes:

- `FASTAPI_CONNECTIVITY_ERROR`
- `FASTAPI_INTEGRATION_ERROR`
- `ELASTICSEARCH_UNAVAILABLE`
- `ELASTICSEARCH_INTEGRATION_ERROR`
- `INVALID_WORKSPACE_NAME`
- `WORKSPACE_NOT_FOUND`
- `ASSET_NOT_FOUND`
- `PROCESSING_JOB_NOT_FOUND`
- `INVALID_WORKSPACE_ID`
- `INVALID_UPLOAD_FILE`
- `INVALID_SEARCH_QUERY`
- `INVALID_TRANSCRIPT_CONTEXT_WINDOW`
- `TRANSCRIPT_NOT_READY`
- `TRANSCRIPT_NOT_USABLE`
- `TRANSCRIPT_ROW_NOT_FOUND`
- `INVALID_REQUEST_PARAMETER`
- `INVALID_REQUEST_BODY`
