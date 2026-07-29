# Product Database

Status: current Spring PostgreSQL schema. PostgreSQL is product truth; Elasticsearch, Kafka,
MinIO and Redis are derived, transport, binary or execution infrastructure.

## Migration policy

The Spring backend uses the immutable clean baseline plus additive Phase 1, Phase 2 Slice 1 and
Phase 5 Slice 5A migrations:

```text
services/workspace-core/src/main/resources/db/migration/
├── V1__create_product_schema.sql
├── V2__add_transcript_timing.sql
├── V3__add_asset_source_identity.sql
├── V4__strengthen_youtube_video_id_constraint.sql
└── V5__add_asset_playback_progress.sql
```

The previous local-development migration chain was consolidated before timestamp-aware transcript
work. `V1`, `V2`, `V3` and `V4` remain unchanged. Existing clean-V1 databases migrate in place
through `V2`, `V3`, `V4` and `V5`; databases from the older pre-baseline chain remain outside this
compatibility promise. Do not set `baseline-on-migrate=true` to disguise an unsupported schema.

`V5` is purely additive: it creates one new table and changes no existing table, column, constraint
or index.

`spring.jpa.hibernate.ddl-auto=validate` remains the normal setting, so Flyway creates the schema
and Hibernate verifies mappings without mutating it.

## Ownership

| Table | Owner | Purpose |
|---|---|---|
| `user_accounts` | common identity | server-owned credential/OIDC user identity |
| `workspaces` | workspace | owner-scoped workspace and default-workspace state |
| `assets` | asset | product source identity, optional upload object reference and lifecycle |
| `processing_jobs` | processing | one durable processing request correlation per asset |
| `asset_transcript_rows` | asset | canonical ordered transcript snapshot |
| `asset_playback_progress` | asset | per-user playback position and completion state |
| `outbox_events` | outbox | durable publication intent and recovery state |
| `consumed_processing_result_events` | processing | result inbox/idempotency and bounded recovery envelope |
| `asset_search_index_jobs` | search | derived-indexing intent, attempt and fingerprint state |

## Core relationships

```text
user_accounts                  workspaces
 credential/OIDC users          owner_id is the current product identity string
                                      1 ---- * assets
                                                   1 ---- 0..1 processing_jobs
                                                   1 ---- * asset_transcript_rows
                                                   1 ---- * asset_search_index_jobs
                                                   1 ---- * asset_playback_progress

outbox_events                         generic durable intent
consumed_processing_result_events     processing-result inbox
```

### `user_accounts`

Important columns:

- `id`: credential-account identifier;
- `email`: normalized and unique;
- `password_hash`: server-owned credential hash;
- `identity_provider` plus `external_subject`: optional paired, unique OIDC identity;
- `created_at`: server-managed metadata.

### `workspaces`

Important columns are `id`, `name`, `owner_id`, `default_workspace` and `created_at`. `owner_id` is
the resolved product identity string; it intentionally also supports the explicit local-development
identity path and therefore is not foreign-keyed to `user_accounts`. Indexes support owner-scoped
and default-workspace reads. Deterministic default IDs plus transactional creation protect normal
concurrent creation; application policy detects multiple defaults rather than claiming a database
unique constraint that does not exist.

### `assets`

Assets store `id`, `workspace_id`, `source_type`, nullable `youtube_video_id`, title/status,
source-specific metadata and creation/update metadata. `source_type` is required and limited to
`UPLOAD` or `YOUTUBE`.

- `UPLOAD` requires filename, bucket, object key, content type and a non-negative size; it must not
  carry a YouTube video ID.
- `YOUTUBE` requires a nonblank video ID of at most 128 characters using only
  `[A-Za-z0-9_-]`; upload filename, storage fields, content type, size and ETag must all be null.

`V3` backfills every existing asset to `UPLOAD` before making `source_type` non-null. It does not
install a runtime default. `V4` preserves the V3 checksum and strengthens
`ck_assets_youtube_video_id_valid` to the domain-safe bounded character set. Exact 11-character
YouTube validation remains application policy for the public creation slice. `(storage_bucket,
object_key)` remains unique for retained upload objects, while `(workspace_id, youtube_video_id)`
prevents duplicate YouTube identity inside one workspace. PostgreSQL nullable uniqueness permits
any number of upload assets and permits the same YouTube video in different workspaces.

Binary data is never stored in PostgreSQL. Asset source identity and lifecycle are Spring product
truth. FastAPI may later own acquisition execution, but it must not become the authority for
`source_type` or `youtube_video_id`. Asset ownership is inherited through the workspace and
enforced by owner-scoped application/repository operations.

### `asset_playback_progress`

Rows store `asset_id`, `user_id`, `position_ms`, `completed` and `updated_at`. The composite primary
key `(asset_id, user_id)` guarantees exactly one row per user and Asset, and also serves the only
two access paths this slice needs: an exact per-user lookup and asset-scoped deletion. No secondary
index is created because there is no watch-history or progress-list read.

`user_id` is the resolved product identity string and reuses the `workspaces.owner_id` type and
rationale: it intentionally supports the explicit local-development identity path and is therefore
not foreign-keyed to `user_accounts`. `ck_asset_playback_progress_position_non_negative` keeps
`position_ms >= 0` as defence in depth behind application validation, `completed` is non-null and
`updated_at` is a timezone-aware application-controlled timestamp.

`fk_asset_playback_progress_asset` cascades on Asset deletion. The application deletion boundary
also removes progress explicitly inside the same product-truth transaction, so cleanup does not
depend on the database cascade alone.

Playback progress is user interaction state, not processing state. It is deliberately outside the
Asset aggregate: it never participates in status transitions, transcript replacement, indexing or
media availability, and it is readable and writable in every Asset status and for both source
types. Concurrency is deterministic last-write-wins for this slice; there is no version column.

### `processing_jobs`

Each job has a unique asset and a non-null, unique `processing_request_event_id`, plus processing
status and bounded raw upstream state. The request event ID is the correlation identity shared
with the Kafka result contract. There are no direct FastAPI upload task/video columns.

### `asset_transcript_rows`

Rows store snapshot identity, asset, transcript-row identity, source video identity,
`segment_index`, nullable `start_ms`/`end_ms`, text and source creation metadata.
`ck_asset_transcript_rows_timing` permits either both timing columns null or both present with
`start_ms >= 0` and `end_ms >= start_ms`. `(asset_id, segment_index)` and
`(asset_id, transcript_row_id)` are unique when the nullable value is present. A successful result
validates the complete artifact and replaces the asset snapshot atomically. This is the canonical
transcript used by indexing, search context and assistant citations. No timing backfill is run;
legacy rows remain null.

### `outbox_events`

The generic envelope stores event identity/type/version, aggregate identity, topic/key, payload,
publication status, claim/retry fields and bounded recovery metadata. Product modules create
neutral drafts; the outbox module does not know asset or search payload semantics.

Publication is at-least-once:

```text
product transaction -> outbox row
claim transaction -> Kafka send outside DB transaction -> finalize/failure transaction
```

Typed transient exhausted failures can be requeued after cooldown and below the configured cycle
limit. Unknown, permanent and recovery-exhausted failures remain operator-visible.

### `consumed_processing_result_events`

`event_id` is the primary idempotency key. The row records event/aggregate/causation identity,
status, safe bounded error detail, optional bounded recoverable envelope and processing metadata.
Known apply/artifact failures become durable `FAILED`; unexpected runtime failures roll back the
transaction so Kafka can redeliver.

### `asset_search_index_jobs`

The row stores the asset, transcript snapshot fingerprint, active-fingerprint key, status, request
outbox identity, attempt count, last error and indexed metadata. A unique active key prevents two
active jobs for the same asset/fingerprint while allowing historical terminal jobs.

Elasticsearch documents are derived from `asset_transcript_rows`; deleting or rebuilding the
index does not change product truth.

## Clean-schema validation

`CleanBaselineMigrationTest` starts from an empty database and migrates V1→V2→V3→V4→V5. It proves
that V3 preserves and backfills existing upload data, leaves no `source_type` default, accepts both
valid source shapes, enforces non-negative upload size and rejects mixed shapes. It also verifies
workspace-scoped YouTube uniqueness, nullable upload identities and the Phase 1 transcript-timing
constraints. For V5 it proves the new table is additive, that existing asset rows are untouched,
that composite identity, the non-negative check and the Asset foreign key all hold, and that
deleting an Asset removes only its own progress rows. `AssetSourcePersistenceTest` validates JPA
round-trips for both source types, and `AssetPlaybackProgressPersistenceTest` validates the
playback-progress upsert, isolation, timestamp refresh and deletion behavior against the migrated
schema with `ddl-auto=validate`.

```bash
mvn -q -f services/workspace-core/pom.xml test
```
