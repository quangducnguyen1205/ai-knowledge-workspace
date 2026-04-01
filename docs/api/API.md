# Repo B API

## Purpose

This document is the current product-facing API summary for Repo B (`workspace-core`).

- Spring Boot is the product entry point.
- Repo A (FastAPI) remains an internal processing dependency.
- FastAPI `/videos/search` is not part of the product API.

## Current Product Endpoints

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
- HTTP `502` or `504` if upstream FastAPI fails

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

Common failure cases:

- HTTP `400` if `q` is missing or blank
- HTTP `503` if Elasticsearch is unavailable
- HTTP `502` if Elasticsearch returns an integration error

## Error Shape Notes

Structured integration errors currently use:

- `code`
- `message`

Current structured error codes:

- `FASTAPI_CONNECTIVITY_ERROR`
- `FASTAPI_INTEGRATION_ERROR`
- `ELASTICSEARCH_UNAVAILABLE`
- `ELASTICSEARCH_INTEGRATION_ERROR`

Validation and state errors such as `400`, `404`, and `409` are currently returned through Spring's standard status handling.

## Other Current Endpoint

- `GET /api/assets/{assetId}` currently exists as a simple asset read endpoint returning the persisted asset record.
- It is useful for debugging and local inspection, but it is not one of the main phase 1 flow endpoints documented above.
