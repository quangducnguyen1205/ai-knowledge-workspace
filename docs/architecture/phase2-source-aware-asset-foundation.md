# Phase 2 — Source-Aware Asset Foundation

Status: Slice 1 implemented in Spring product core. Phase 2 is not complete.

## Scope delivered

Spring Asset product state now has an explicit source identity:

```text
AssetSourceType
├── UPLOAD
└── YOUTUBE
```

The `Asset` domain exposes two controlled creation paths:

- `Asset.uploaded(...)` requires upload filename, object-storage coordinates, content type and a
  non-negative size, and carries no YouTube identity;
- `Asset.youtube(...)` requires a nonblank bounded `youtubeVideoId` and carries no upload-specific
  metadata.

There is no public constructor for arbitrary mixed source state. JPA lifecycle validation and V3
database constraints independently protect the source shape.

## Ownership and persistence

Spring Asset owns:

- source type;
- workspace-scoped YouTube video identity;
- product title and lifecycle;
- upload object ownership.

`V3__add_asset_source_identity.sql` adds `source_type` and `youtube_video_id`, backfills existing
assets as `UPLOAD`, relaxes upload-only column nullability for future YouTube assets, enforces the
two source shapes and adds `(workspace_id, youtube_video_id)` uniqueness. It does not add a default
or fabricate YouTube identity.

PostgreSQL remains product truth. Canonical transcript rows, search documents and assistant
citations remain source-neutral and are unchanged.

## Existing upload compatibility

The public multipart endpoint and its ordering are unchanged:

```text
validate
→ store in MinIO outside a DB transaction
→ one transaction writes Asset(UPLOAD) + ProcessingJob + V1 outbox intent
```

The Kafka processing request event remains V1 and still carries the same upload object reference.
The API response only gains additive `sourceType = "UPLOAD"` and nullable
`youtubeVideoId = null`.

Deletion now dispatches owned-resource cleanup by explicit source type:

```text
UPLOAD:  Elasticsearch → MinIO → database
YOUTUBE: Elasticsearch → database
```

Required cleanup failure still prevents deletion of PostgreSQL product state.

## Explicitly not delivered

Slice 1 does not add:

- a public YouTube creation endpoint or URL normalizer;
- canonical URL persistence or a `sourceUrl` response;
- Kafka V2 production or routing;
- FastAPI acquisition, `yt-dlp` or retry/lease behavior;
- frontend source selection;
- player, seeking or synchronization behavior.

The next Phase 2 work remains the retry-safe FastAPI lease foundation, YouTube V2 processing and
acquisition, Spring V2 production plus the public YouTube endpoint, frontend entry, and
cross-repository runtime acceptance.
