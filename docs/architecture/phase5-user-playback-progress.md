# Phase 5 Slice 5A — User Playback Progress

Status: Spring backend contract implemented and validated. The frontend Resume experience is
separate, unimplemented work; Phase 5 is not complete.

## Ownership decision

Playback progress is **user interaction state**, not processing state. It is therefore owned by a
dedicated table and a dedicated application use case inside the asset module, and it is explicitly
*not* added to the Asset aggregate:

- Asset status, ProcessingJob state, canonical transcript rows, the derived search index and the
  stored media object are all product/processing truth shared by every viewer.
- A playback position belongs to exactly one viewer and carries no product meaning for anyone else.

Mixing the two would make an ordinary progress write participate in Asset lifecycle transactions
and would let a per-user value influence processing or indexing decisions.

## Boundary

```text
authenticated GET/PUT /api/assets/{assetId}/playback-progress
→ playback-progress web adapter          (HTTP mapping only)
→ playback-progress application service  (validation, authorization, orchestration)
→ authorized Asset lookup                (existing owner-scoped lookup, reused)
→ playback-progress store port           (application-owned outbound contract)
→ JPA persistence adapter                (upsert / read / asset-scoped delete)
```

The web adapter never touches a repository or JPA entity, the application layer never imports a
Spring Data or web transport type, and the Spring Data repository stays package-private behind the
application-owned store port. Authorization is not re-implemented in the controller: the service
calls the same `loadAuthorizedAsset` used by every other Asset read, so a missing Asset and an
Asset owned by another user are indistinguishable.

The current user identity is read through the published `identity` module API rather than derived
from workspace ownership, so the stored row is keyed by the authenticated viewer.

## Contract

`GET` returns the stored representation, or the frozen default `positionMs = 0`,
`completed = false`, `updatedAt = null` when nothing has been saved. A read never inserts a row.

`PUT` validates `positionMs` and `completed` first, then authorizes the Asset, then upserts. It
returns the same representation as `GET`, built from the persisted values. Both request fields are
required: a missing or null `completed` is a client error, never an implicit `false`, so a partial
body cannot silently clear a completion the user already reached.

The owning user identifier is never present in a public response, and no SQL, table name, stack
trace or storage detail appears in an error body.

## Source and status independence

Progress works identically for `UPLOAD` and `YOUTUBE` Assets and in `PROCESSING`, `FAILED`,
`TRANSCRIPT_READY` and `SEARCHABLE`. It requires no transcript, no Elasticsearch document, no MinIO
object and no FastAPI participation. A media object that cannot be streamed does not prevent
reading or writing progress.

A `completed` record keeps its last position. Spring stores the fact; it does not decide resume
behavior, and a client must not silently resume a completed record.

## Atomic write and concurrency limitation

The concurrency boundary belongs to the database, not to Java. A save is one statement:

```sql
INSERT INTO asset_playback_progress (asset_id, user_id, position_ms, completed, updated_at)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT (asset_id, user_id)
DO UPDATE SET position_ms = EXCLUDED.position_ms,
              completed   = EXCLUDED.completed,
              updated_at  = EXCLUDED.updated_at
```

The composite primary key is the conflict target, so the interleaving where two requests both find
no existing row and then both write cannot fail: PostgreSQL turns the losing insert into an update.
There is no read-then-branch, no retry loop, no distributed lock and no optimistic version field.

Within that, the policy is deterministic **last-write-wins**: the write PostgreSQL applies last
determines the stored position, completion flag and timestamp, including a write from another
device or tab. No error is surfaced for the overwritten value, and each response reports the values
that request persisted rather than re-reading whichever write ultimately won. There is no event
publication, no Kafka participation and no cross-device conflict resolution.

H2 cannot parse `ON CONFLICT ... DO UPDATE`. The production statement is deliberately **not**
weakened to fit the default test profile: concurrency is proven against a real PostgreSQL server in
a throwaway database, and a build-time contract test keeps the statement atomic even when that
integration test is not selected.

## Transaction and deletion boundaries

The read path is not transactional. The upsert runs inside one explicit `@Transactional` boundary,
matching the existing pattern where transactions live in dedicated collaborator services rather
than in orchestration services.

Asset deletion removes progress twice over: the existing product-truth deletion transaction removes
progress rows explicitly alongside transcript rows and the processing job, and the database
foreign key cascades as defence in depth. Source-aware deletion ownership for Upload and YouTube
Assets is unchanged.

## Not in this slice

- No watch-history or progress-list endpoint.
- No frontend Resume behavior; the frozen backend contract exists so that work can start.
- No progress-derived analytics, recommendation or reporting surface.
