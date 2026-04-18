# Repo B Database

## Purpose

This document summarizes what Repo B currently persists in PostgreSQL.

- It describes current relational persistence only.
- It does not describe the Elasticsearch search index as primary application storage.

## Current Relational Model

Repo B currently persists five main records:

- `UserAccount`
- `Workspace`
- `Asset`
- `ProcessingJob`
- `AssetTranscriptRowSnapshot`

## Simplified Persistence Relationship Diagram

This diagram is intentionally simplified and asset-centric. The detailed sections below are the source of truth for the current field lists and notes.

```mermaid
erDiagram
    WORKSPACE ||--o{ ASSET : contains
    ASSET ||--|| PROCESSING_JOB : tracks
    ASSET ||--o{ ASSET_TRANSCRIPT_ROW_SNAPSHOT : snapshots

    WORKSPACE {
        uuid id PK
        string ownerId
        boolean defaultWorkspace
        instant createdAt
    }

    ASSET {
        uuid id PK
        uuid workspace_id FK
        string title
        string status
        instant createdAt
        instant updatedAt
    }

    PROCESSING_JOB {
        uuid id PK
        uuid assetId FK
        string fastapiTaskId
        string fastapiVideoId
        string processingJobStatus
    }

    ASSET_TRANSCRIPT_ROW_SNAPSHOT {
        uuid snapshotId PK
        uuid assetId FK
        string transcriptRowId
        int segmentIndex
        string text
        instant createdAt
    }
```

## Ownership And Derived-Data Shape

```mermaid
flowchart LR
    U["Authenticated user / UserAccount"] --> W["Owned workspace"]
    W --> A["Asset"]
    A --> J["ProcessingJob"]
    A --> T["Transcript snapshot rows"]
    T --> E["Elasticsearch transcript-row documents"]
```

The transcript-row documents in Elasticsearch are derived search documents, not the system of record. Ownership still flows from user -> workspace -> asset in the product core.

## `UserAccount`

Table: `user_accounts`

Current fields:

- `id` UUID primary key
- `email`
- `passwordHash`
- `createdAt`

Current role:

- Represents the current minimal product user record for session-based auth.
- Supports register, login, logout, and `GET /api/me`.
- Owns workspaces logically through `Workspace.ownerId`.
- Is intentionally narrow and does not introduce roles, sharing, or broader auth-platform features yet.

## `Workspace`

Table: `workspaces`

Current fields:

- `id` UUID primary key
- `name`
- `ownerId`
- `defaultWorkspace`
- `createdAt`

Current role:

- Represents the current product-side ownership container for assets.
- Supports ownership-aware workspace scoping without introducing collaboration or richer auth features yet.
- Provides one default workspace path per current user when `workspaceId` is omitted.
- Stores ownership through `ownerId` as a product-level logical link to `UserAccount`, not as a relational foreign key.
- Can be created explicitly through the current minimal workspace API, or lazily when the default workspace is first needed.

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

## `AssetTranscriptRowSnapshot`

Table: `asset_transcript_rows`

Current fields:

- `snapshotId` UUID primary key
- `assetId` UUID
- `transcriptRowId`
- `videoId`
- `segmentIndex`
- `text`
- `createdAt`

Current role:

- Stores the product-owned transcript snapshot for one asset.
- Persists only the currently verified transcript fields used by the product API and indexing flow.
- Supports transcript read, transcript context, and explicit indexing without requiring a fresh upstream transcript fetch in the normal path.

## Current Relationship Shape

- One `Workspace` can contain many `Asset` records.
- The current flow creates one `ProcessingJob` for one `Asset`.
- One `Asset` can have many `AssetTranscriptRowSnapshot` rows.
- The link is currently stored through `ProcessingJob.assetId`.
- The code looks up the processing job by asset ID.

## Current Write Behavior

- Workspace create persists a minimal `Workspace` row with `name`, and default-scope reads can lazily create the current user's default workspace row if it is still missing.
- Upload resolves a workspace first, then persists `Asset` and `ProcessingJob` together after FastAPI acknowledges the upload.
- On-demand status refresh can update both `ProcessingJob.processingJobStatus` and `Asset.status`.
- Transcript capture can persist local transcript snapshot rows after transcript data is validated as usable.
- Transcript read, transcript context, and explicit indexing use those local transcript rows in the normal path.
- Transcript capture can move an asset to `TRANSCRIPT_READY`.
- Empty transcript handling can move an asset to `FAILED`.
- Successful indexing can move an asset to `SEARCHABLE`.
- Asset reads and the local/dev default-user legacy listing path can backfill a missing workspace association to the current user's default workspace so older local rows stay usable.
- Asset deletion removes local transcript snapshot rows together with the linked `ProcessingJob` and `Asset`.

## Intentionally Not Persisted Yet

- Transcript version history
- Transcript sync state beyond the current snapshot
- Workspace sharing rules
- Search history or query analytics

## Note On Elasticsearch

Elasticsearch is already used for search indexing and retrieval, but those transcript-row documents are not part of the primary relational schema described here. The current transcript-row search documents include `workspaceId` so Spring can enforce workspace-scoped search in the product API.
