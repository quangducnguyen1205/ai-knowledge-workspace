# Repo B Database

## Purpose

This document summarizes what Repo B currently persists in PostgreSQL.

- It describes current relational persistence only.
- It does not describe the Elasticsearch search index as primary application storage.

## Current Relational Model

Repo B currently persists three main records:

- `Workspace`
- `Asset`
- `ProcessingJob`

## `Workspace`

Table: `workspaces`

Current fields:

- `id` UUID primary key
- `name`
- `createdAt`

Current role:

- Represents the current phase-1 logical container for assets.
- Supports product-side workspace scoping without introducing auth or collaboration yet.
- Provides the default workspace bootstrap used when `workspaceId` is omitted.

## `Asset`

Table: `assets`

Current fields:

- `id` UUID primary key
- `originalFilename`
- `title`
- `status`
- `workspace_id` UUID foreign key to `workspaces`
- `createdAt`
- `updatedAt`

Current status values:

- `PROCESSING`
- `TRANSCRIPT_READY`
- `SEARCHABLE`
- `FAILED`

Current role:

- Represents the product-owned media asset record.
- Tracks the current product-side lifecycle state.
- Associates the asset with one workspace.

## `ProcessingJob`

Table: `processing_jobs`

Current fields:

- `id` UUID primary key
- `assetId` UUID
- `fastapiTaskId`
- `fastapiVideoId`
- `processingJobStatus`
- `rawUpstreamTaskState`
- `createdAt`
- `updatedAt`

Current status values:

- `PENDING`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`

Current role:

- Tracks the Spring-side view of one upstream FastAPI processing task.
- Retains both upstream identifiers needed for task polling and transcript fetch.
- Keeps the raw upstream task state for debugging.

## Current Relationship Shape

- One `Workspace` can contain many `Asset` records.
- The current flow creates one `ProcessingJob` for one `Asset`.
- The link is currently stored through `ProcessingJob.assetId`.
- The code looks up the processing job by asset ID.

## Current Write Behavior

- Upload resolves a workspace first, then persists `Asset` and `ProcessingJob` together after FastAPI acknowledges the upload.
- On-demand status refresh can update both `ProcessingJob.processingJobStatus` and `Asset.status`.
- Transcript fetch can move an asset to `TRANSCRIPT_READY`.
- Empty transcript handling can move an asset to `FAILED`.
- Successful indexing can move an asset to `SEARCHABLE`.
- Asset reads backfill a missing workspace association to the configured default workspace so older local rows stay usable.

## Intentionally Not Persisted Yet

- A local transcript table
- A local transcript cache
- Workspace ownership or sharing rules
- Search history or query analytics

## Note On Elasticsearch

Elasticsearch is already used for search indexing and retrieval, but those transcript-row documents are not part of the primary relational schema described here. The current transcript-row search documents include `workspaceId` so Spring can enforce workspace-scoped search in the product API.
