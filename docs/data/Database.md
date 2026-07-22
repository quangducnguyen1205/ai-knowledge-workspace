# Product Database

Status: current Spring PostgreSQL schema. PostgreSQL is product truth; Elasticsearch, Kafka,
MinIO and Redis are derived, transport, binary or execution infrastructure.

## Migration policy

The Spring backend uses the immutable clean baseline plus one additive Phase 1 migration:

```text
services/workspace-core/src/main/resources/db/migration/
├── V1__create_product_schema.sql
└── V2__add_transcript_timing.sql
```

The previous local-development migration chain was consolidated before timestamp-aware transcript
work. `V1` remains unchanged. Existing clean-V1 databases migrate in place through `V2`; databases
from the older pre-baseline chain remain outside this compatibility promise. Do not set
`baseline-on-migrate=true` to disguise an unsupported schema.

`spring.jpa.hibernate.ddl-auto=validate` remains the normal setting, so Flyway creates the schema
and Hibernate verifies mappings without mutating it.

## Ownership

| Table | Owner | Purpose |
|---|---|---|
| `user_accounts` | common identity | server-owned credential/OIDC user identity |
| `workspaces` | workspace | owner-scoped workspace and default-workspace state |
| `assets` | asset | product asset metadata, object reference and lifecycle |
| `processing_jobs` | processing | one durable processing request correlation per asset |
| `asset_transcript_rows` | asset | canonical ordered transcript snapshot |
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

Assets store `id`, `workspace_id`, filename/title/status, object-storage metadata and creation/
update metadata. `(storage_bucket, object_key)` is unique. Binary data is never stored in
PostgreSQL. Asset ownership is inherited through the workspace and enforced by owner-scoped
application/repository operations.

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

`CleanBaselineMigrationTest` starts from an empty database, migrates to V1, proves timing columns
are absent, then migrates V1→V2, validates JPA mappings, and exercises valid legacy/zero rows plus
partial, negative and backwards-timing constraint failures.

```bash
mvn -q -f services/workspace-core/pom.xml test
```
