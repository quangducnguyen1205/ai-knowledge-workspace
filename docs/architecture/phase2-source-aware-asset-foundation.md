# Phase 2 — Source-Aware Asset Foundation

Status: Spring Slice 3 implemented. Frontend entry and cross-repository runtime acceptance remain;
Phase 2 is not complete.

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

The Spring product core now additionally delivers:

- authoritative allowlisted YouTube URL normalization and `POST /api/assets/youtube`;
- derived, non-persisted `sourceUrl` in public Asset read/create/retry models;
- exact YouTube processing request V2 production and version-aware topic routing;
- authorized failed-Asset retry using a fresh request ID and source-appropriate V1/V2 intent;
- late-result protection through the existing current-request correlation;
- sanitized nullable processing `failureCode` exposure from the existing ProcessingJob state.

Still not delivered by this Spring slice:

- frontend source selection;
- cross-repository runtime acceptance of a successful live YouTube acquisition;
- player, seeking or synchronization behavior.

FastAPI V2 must be deployed before Spring publishes V2. Upload production remains request V1 and
result V1; YouTube production uses request V2 and the same result V1. Phase 2 is not complete until
frontend source entry and cross-repository runtime acceptance are finished.
