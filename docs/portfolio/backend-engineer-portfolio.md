# Project3 — Backend Engineer Portfolio Handoff

A handoff note for using Project3 in a Backend Engineer application. It is written to be defensible
in an interview: every claim below is something the repository can demonstrate on request.

## One-paragraph summary

Project3 is a video knowledge workspace: you add a video (upload or YouTube), it is transcribed
asynchronously, and you can then search inside the spoken content, jump to the exact moment, save
that moment as a stable link, and resume where you stopped. The backend is a Spring Boot 3.3.5
**modular monolith** (Spring Modulith, ports and adapters) that owns all product truth in
PostgreSQL, with Elasticsearch as a fully rebuildable derived index, Kafka as an at-least-once
integration transport with a transactional outbox and an idempotent inbox, MinIO for media objects,
and one separate internal FastAPI/Celery service that owns transcription and nothing product-visible.

## What this demonstrates, and where to look

| Claim | Where it is demonstrated |
|---|---|
| Enforced module boundaries, not conventions | `architecture/ModuleBoundaryRulesTest`, `ApplicationModules.verify()`, `adapter/in/module/*PortAdapter.java` |
| Distributed messaging without dual writes | `outbox/`, `processing/` inbox keyed by `eventId`, claim → send → finalize |
| Truth versus derived state, handled explicitly | search stale-hit validation in `search/application/`, `POST /api/assets/{id}/index` rebuild |
| Concurrency delegated to the database | `INSERT … ON CONFLICT DO UPDATE` for playback progress; unique constraint as the boundary for saved moments |
| Authorization designed once and inherited | `loadAuthorizedAsset`; foreign resources return `404`, never `403` |
| Schema discipline | Flyway `V1`–`V7`, immutable, additive, with `ddl-auto=validate` |
| Performance decisions from measurement | `V7__index_resumable_playback_progress.sql` carries the `EXPLAIN (ANALYZE, BUFFERS)` evidence in its header |
| Operability | health-gated startup, bounded memory, `/api/build-info`, [deployment runbook](../runbooks/deployment.md) |

## The three engineering stories worth telling

### 1. Derived state is never allowed to become the answer

Elasticsearch returns candidates; PostgreSQL decides. Before a hit is returned, the canonical row is
re-read and compared on identity, segment, timing, normalized text and creation identity. A drifted
hit is dropped **in place** — order preserved, no replacement promoted, count recomputed.

This was proven at runtime, not asserted: changing a row's text in PostgreSQL without reindexing
removed exactly that hit (12 → 11, the 13th candidate was *not* promoted), while a neighbouring
row's context snippet showed the **new** PostgreSQL text.

*What it shows:* an opinion about where truth lives, implemented as a mechanism, and verified by
constructing the failure it prevents.

### 2. Concurrency is a schema decision

Playback progress is a single atomic upsert. Saved moments have no upsert at all — the unique
constraint `(user_id, asset_id, transcript_row_id)` **is** the concurrency boundary: attempt the
insert, translate the integrity violation to "already saved", inside `REQUIRES_NEW` so only the
loser's unit rolls back. Eight concurrent saves converge on one row with zero surfaced failures.

The interesting part is the constraint discovered along the way: H2 (PostgreSQL mode) supports
neither `ON CONFLICT` nor partial indexes. That was probed rather than assumed, and it changed the
design — the portable test suite exercises the exception path while the concurrency claim rests on
a real PostgreSQL integration suite.

*What it shows:* preferring a database invariant over application locking, and letting a measured
platform limitation change the design instead of being papered over.

### 3. An index added from a plan, not a hunch

Continue Watching joins playback progress to the owning Asset. Measured on real PostgreSQL:

| Shape | Before | After `V7` |
|---|---|---|
| 5 000-Asset Workspace, 82 462 progress rows | seq scan, 79 609 rows removed, 1 009 buffers, 6.9–26.2 ms | index scan, 143 buffers, 0.24–0.91 ms |
| Realistic shape (200 Workspaces × 60 Assets, ~2 000 users) | 249 buffers | 49 buffers |

A partial index restricted to resumable rows measured slightly better and smaller — and was
**rejected**, because H2 cannot parse partial indexes and forking the migration chain per vendor
would mean the portable tests stop validating the production schema. The trade-off is recorded in
the migration header.

*What it shows:* reading plans rather than adding indexes reflexively, and choosing the option that
keeps the test strategy honest over the one that benchmarks marginally better.

## Scope and verification, stated accurately

- **Tests.** The backend suite is 722 tests (0 failures, 17 skipped) plus four opt-in
  Docker-dependent integration profiles; the frontend has 53 test files. This is a measure of how
  the work was verified — it is not a product metric and says nothing about users or traffic.
- **AI functionality.** The system *uses* AI: Whisper for transcription and an optional local Ollama
  model for grounded assistant answers over retrieved transcript context. It does not train,
  fine-tune or evaluate models, and no retrieval-quality benchmark has been run. The assistant is a
  bounded feature over the same canonical transcript, not the centre of the system.
- **Operational status.** Runs reproducibly on a single machine under Docker Compose. It has never
  served production traffic, has no commercial users, and does not run on Kubernetes or any cloud.
- **Search quality.** Phase 7 established a versioned lexical evaluation corpus with measured
  Elasticsearch 8.11.1 behaviour and *recorded gaps*, including no support for unaccented
  Vietnamese queries. See `architecture/phase7-search-quality-baseline.md`.

## Known limitations to raise before an interviewer finds them

1. `keycloak_jwt` is a foundation, not a shipped mode — a native media element cannot carry an
   in-memory bearer token, so Upload playback has no path in that mode.
2. Single Kafka broker, single Elasticsearch node, replication factor 1. Correct for a laptop,
   unacceptable for production.
3. No dead-letter topic; failed integration rows are inspected in PostgreSQL by an operator.
4. Asset title change requires Elasticsearch and an already-indexed Asset, otherwise it returns
   `503` rather than accepting the write and reconciling later.
5. Transcript replacement is wholesale, so a re-transcription silently invalidates saved moments
   that pointed at replaced rows.

All five are documented with reasoning in [`../architecture/architecture-review.md`](../architecture/architecture-review.md).

## Suggested résumé lines

> Built a Spring Boot modular monolith (Spring Modulith, ports and adapters, 11 modules) with
> build-time-enforced boundaries; integrated an internal FastAPI/Celery transcription service over
> Kafka using a transactional outbox and an idempotent inbox.

> Designed search so PostgreSQL stays product truth and Elasticsearch stays a rebuildable derived
> index, with per-hit validation that drops stale results in place; verified by constructing the
> drift condition at runtime.

> Resolved a sequential-scan regression in a bounded list query by reading
> `EXPLAIN (ANALYZE, BUFFERS)` plans and adding a measured composite index (1 009 → 143 buffers),
> choosing the portable form over a faster partial index to keep the portable test suite validating
> the production schema.

## Interview preparation pointers

| If asked about | Read first |
|---|---|
| The overall system | [`../architecture/system-inventory.md`](../architecture/system-inventory.md) |
| Design trade-offs and debt | [`../architecture/architecture-review.md`](../architecture/architecture-review.md) |
| Running or operating it | [`../runbooks/deployment.md`](../runbooks/deployment.md) |
| A live walkthrough | [`../demo/demo-script.md`](../demo/demo-script.md) |
| Where the research value is | [`../thesis/thesis-handoff.md`](../thesis/thesis-handoff.md) |
