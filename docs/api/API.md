# Repo B API

## Purpose

This document is the current product-facing API summary for Repo B (`workspace-core`).

- Spring Boot is the product entry point.
- Repo A (FastAPI) remains an internal processing dependency.
- FastAPI `/videos/search` is not part of the product API.

## Current Product Endpoints

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
- Workspace create stays intentionally small and does not add ownership, sharing, or collaboration rules.

Common failure cases:

- HTTP `400` with `code = "INVALID_WORKSPACE_NAME"` if `name` is blank or longer than the current maximum length
- HTTP `400` if the request body is missing or malformed

### `GET /api/workspaces`

Lists workspaces in Repo B.

Response:

- HTTP `200`
- Body: array of rows with:
  - `id`
  - `name`
  - `createdAt`

Current behavior:

- Spring ensures the configured default workspace exists before returning the list.
- Results are intentionally minimal and do not include asset counts or membership data.

### `GET /api/workspaces/{workspaceId}`

Reads one workspace in Repo B.

Response:

- HTTP `200`
- Body:
  - `id`
  - `name`
  - `createdAt`

Current behavior:

- Reading the configured default workspace ID returns the bootstrap workspace, creating it first if needed.

Common failure cases:

- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if the workspace does not exist

### `GET /api/assets`

Lists assets in the resolved workspace scope.

Query parameters:

- `workspaceId` optional

Response:

- HTTP `200`
- Body: array of rows with:
  - `assetId`
  - `title`
  - `assetStatus`
  - `workspaceId`
  - `createdAt`

Current behavior:

- Spring resolves the requested `workspaceId`, or falls back to the configured default workspace when omitted.
- Non-default workspace listing only returns assets already associated with that workspace.
- Default-workspace listing also includes older local assets whose `workspace_id` is still null.
- When default-workspace listing encounters a legacy asset with no workspace, Spring backfills that asset to the default workspace.

Common failure cases:

- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if a provided `workspaceId` does not exist

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
- Spring resolves the requested `workspaceId`, or falls back to the configured default workspace when omitted.
- Spring validates the upstream response before persisting local state.
- Spring associates the created asset with one workspace in Repo B.
- Raw FastAPI IDs are stored internally but not returned to the client.

Common failure cases:

- HTTP `400` if `file` is missing or empty
- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if a provided `workspaceId` does not exist
- HTTP `502` or `504` if upstream FastAPI fails

### `GET /api/assets/{assetId}`

Reads one persisted asset record.

Response:

- HTTP `200`
- Body: the current persisted asset record

Current behavior:

- This remains a simple product-owned asset read endpoint.
- It is useful for debugging and local inspection.
- If the asset still has no workspace association, Spring backfills it to the default workspace before returning it.

Common failure cases:

- HTTP `404` if the asset does not exist

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

- Spring fetches transcript rows from FastAPI using stored `fastapiVideoId`.
- Only the currently verified transcript fields are exposed.
- Transcript fetch is rejected until `processingJobStatus = SUCCEEDED`.
- Empty transcript is treated as not usable.

Common failure cases:

- HTTP `404` if the asset or processing job does not exist
- HTTP `409` if processing is not ready or transcript rows are empty

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
- Context rows are selected by transcript ordering on `segmentIndex`.
- If a transcript row has a real upstream `id`, context lookup matches only that `id`.
- The fallback identifier `segment-{segmentIndex}` is accepted only when the upstream transcript row `id` is missing or blank.
- The endpoint is intentionally narrow and does not add timestamps, speaker labels, snippet metadata, or transcript caching.

Common failure cases:

- HTTP `400` with `code = "INVALID_TRANSCRIPT_CONTEXT_WINDOW"` if `window` is malformed, zero, negative, or above the current maximum
- HTTP `404` with `code = "TRANSCRIPT_ROW_NOT_FOUND"` if the requested row does not belong to that asset transcript
- HTTP `404` if the asset does not exist
- HTTP `409` if the transcript is not ready or is empty

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
- One Elasticsearch document is written per transcript row.
- Indexed transcript-row documents include `workspaceId`.
- Repeated indexing reuses stable transcript-row document IDs for the same asset and transcript row.
- Only usable non-empty transcript rows can be indexed.
- Successful indexing refreshes the transcript index before returning.
- Successful indexing marks the asset `SEARCHABLE`.
- Indexing failure does not collapse a usable asset back to `FAILED`.

Common failure cases:

- HTTP `404` if the asset or processing job does not exist
- HTTP `409` if transcript data is not ready or is empty
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
- Spring resolves the requested `workspaceId`, or falls back to the configured default workspace when omitted.
- Search only considers documents inside the resolved workspace scope.
- Only documents for assets already marked `SEARCHABLE` are eligible.
- `assetId` is an exact filter when provided.
- The current search baseline is simple text search over transcript text and asset title.
- Search ordering is deterministic on score ties: `_score desc`, then `segmentIndex`, `assetId`, and `transcriptRowId`.

Common failure cases:

- HTTP `400` if `q` is missing or blank
- HTTP `400` with `code = "INVALID_WORKSPACE_ID"` if `workspaceId` is not a valid UUID
- HTTP `404` with `code = "WORKSPACE_NOT_FOUND"` if a provided `workspaceId` does not exist
- HTTP `503` if Elasticsearch is unavailable
- HTTP `502` if Elasticsearch returns an integration error

## Error Shape Notes

Structured error responses currently use:

- `code`
- `message`

Current structured error codes:

- `FASTAPI_CONNECTIVITY_ERROR`
- `FASTAPI_INTEGRATION_ERROR`
- `ELASTICSEARCH_UNAVAILABLE`
- `ELASTICSEARCH_INTEGRATION_ERROR`
- `INVALID_WORKSPACE_NAME`
- `WORKSPACE_NOT_FOUND`
- `INVALID_WORKSPACE_ID`
- `INVALID_TRANSCRIPT_CONTEXT_WINDOW`
- `TRANSCRIPT_ROW_NOT_FOUND`
- `INVALID_REQUEST_PARAMETER`

Other validation and state errors such as transcript-not-ready `409` still use Spring's standard status handling.
