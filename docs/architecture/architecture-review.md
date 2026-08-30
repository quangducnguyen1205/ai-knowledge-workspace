# Project3 Architecture Review

A critical review of the system as built, not a description of what it was meant to be. Findings
are classified as **accepted strength**, **known trade-off**, **technical debt** or **future
scaling trigger**.

## What this system is, precisely

Spring `workspace-core` is a **modular monolith** built with Spring Modulith: one deployable, one
database, hard module boundaries verified at build time. It is **not** microservices. There is
exactly one other service — the FastAPI processor — and it exists because transcription is a
different runtime concern (Python, Celery, GPU-adjacent, long-running), not because the product was
decomposed by domain.

The internal style is a **deliberate combination**, not pure anything:

- **Ports and adapters** is applied consistently: every module has `application/port/in`,
  `application/port/out`, `adapter/in/web`, `adapter/out/persistence`.
- **DDD tactical patterns** appear where they earn their place — `Asset` is a real aggregate with
  invariants in `asset/domain/Asset.java` — and are deliberately absent elsewhere. `SavedMoment`
  has no domain object at all; it is a record plus a service, because it has no invariant beyond a
  unique constraint.
- There is **no CQRS**, no event sourcing and no domain-event bus. The outbox carries *integration*
  events between services, not domain events inside the monolith.

Calling this "DDD" or "hexagonal" without qualification would be inaccurate.

## Module boundaries

Eleven modules: `asset`, `assistant`, `common`, `identity`, `integration`, `outbox`, `processing`,
`savedmoment`, `search`, `storage`, `workspace`.

Cross-module access uses one of two shapes, both enforced by ArchUnit and Spring Modulith:

1. **Published API** — `workspace/api/WorkspaceAccessUseCase`, `identity/api/CurrentUserContext`.
2. **Consumer-owned outbound port implemented by the provider** — `search` declares
   `SearchAssetQueryPort`; `asset` implements it in `asset/adapter/in/module/SearchAssetPortAdapter`.
   `savedmoment` declares `SavedMomentAssetPort`; `asset` implements it in
   `SavedMomentAssetPortAdapter`.

Shape 2 inverts the dependency arrow so the *provider* depends on the *consumer's* contract. That
is why adding Saved Moments in Phase 8 created no cycle: the arrow runs `asset → savedmoment`, the
same direction as the cleanup call, and `ApplicationModules.verify()` stays green.

`ModuleBoundaryRulesTest` additionally proves that Spring Data repositories are package-private,
controllers never depend on JPA entities, application layers never import
`org.springframework.data..` or `org.springframework.web..`, and repositories and entities never
cross a module boundary.

> **Accepted strength.** The boundary rules are executable, not aspirational. Every phase since 7
> has been forced to route new cross-module access through a port rather than reach for a
> repository.

> **Known trade-off.** The port-per-consumer pattern produces real duplication: `SearchAssetDetails`,
> `SavedMomentCanonicalMoment` and `ResumableAssetPlayback` are three near-identical projections of
> the same Asset. That is the price of not sharing a leaky "AssetDto" across modules, and it was
> paid deliberately.

## PostgreSQL as product truth, Elasticsearch as derived state

Every product fact lives in PostgreSQL. Elasticsearch holds one document per canonical transcript
row and is fully rebuildable through `POST /api/assets/{id}/index`.

The interesting consequence is Slice 7.4's **stale-hit validation**: before returning a hit, Spring
re-reads the canonical row from PostgreSQL and compares identity, segment, timing, normalized text
and creation identity. A hit whose derived document has drifted is dropped in place — order is
preserved, no replacement candidate is pulled in, and `resultCount` is recomputed.

Phase 7 proved this at runtime: changing a row's text in PostgreSQL without reindexing removed that
hit from the response (`12 → 11`, and the 13th candidate was *not* promoted), while the neighbour's
context snippet showed the **new** PostgreSQL text — demonstrating that snippets come from product
truth, not from the index.

> **Accepted strength.** Derived state can never silently become the product's answer.

> **Known trade-off.** Validation costs one targeted PostgreSQL round trip per search, grouped by
> Asset. It is bounded at 12 targets and 36 rows, but it is a real cost paid on every browser
> search.

> **Future scaling trigger.** If the per-Asset context query ever becomes the search latency floor,
> the next step is a cache keyed by transcript snapshot fingerprint — not removing validation.

## The FastAPI processor boundary

FastAPI owns transcription and nothing else that is product-visible. The boundary is enforced by
what crosses it: the `transcript.ready` event carries only `processingRequestId`, and Spring then
*pulls* the artifact through `TranscriptArtifactGateway`. FastAPI cannot push product state.

> **Accepted strength.** A compromised or buggy processor cannot invent Assets, Workspaces or
> authorization outcomes.

> **Known trade-off.** It also means deterministic transcripts cannot be produced without either
> running real Whisper or seeding PostgreSQL directly. Phases 7–10 all had to declare a
> direct-PostgreSQL fixture for this reason, which is honest but is friction.

> **Technical debt.** `ProcessingResultEventParser` accepts both camelCase and snake_case payload
> aliases. That tolerance was pragmatic during integration; a single canonical shape would be
> stricter.

## Kafka, outbox and inbox

Publication is at-least-once with a transactional outbox:

```text
product transaction → outbox row
claim transaction → Kafka send outside the DB transaction → finalize/failure transaction
```

The result path has a matching inbox keyed by `eventId`, so duplicate delivery is a no-op. Typed
transient failures can be requeued after cooldown within a bounded cycle limit; unknown, permanent
and recovery-exhausted rows stay operator-visible and terminal.

> **Accepted strength.** No dual-write between the database and Kafka, and no unbounded retry loop.

> **Known trade-off.** Kafka runs as a single KRaft broker with replication factor 1. That is
> correct for a laptop and unacceptable for production; it is a topology limitation, not a design
> one.

> **Technical debt.** There is no dead-letter topic. Failed rows are inspected in PostgreSQL. This
> is deliberate — a DLQ without an operator UI is a place messages go to be forgotten — but it does
> mean recovery is a manual, SQL-assisted activity.

## Canonical transcript ownership

`asset_transcript_rows` is replaced atomically per Asset when a result is applied. It is the single
input to indexing, to search context hydration and to assistant citations. Row identity
(`transcript_row_id`) is authoritative; a supplied identifier never falls back to a segment index.

> **Accepted strength.** One canonical source removed a whole class of "which transcript is right"
> bugs. Phases 8 and 9 both depend on it and neither needed a snapshot copy.

> **Known trade-off.** Replacement is wholesale. A transcript edit invalidates saved moments that
> pointed at replaced rows — correctly, but silently: they simply stop appearing.

## Saved Moments

`savedmoment` stores canonical identity only: user, Workspace, Asset, transcript row, timestamp.
Presentation is resolved from current Asset state on every read.

The unique constraint `(user_id, asset_id, transcript_row_id)` **is** the concurrency boundary. The
application attempts the insert and treats the rejection as "already saved", in its own
`REQUIRES_NEW` transaction so the loser rolls back cleanly. Eight concurrent saves converge on one
row with zero surfaced failures (`SavedMomentPostgresIT`).

> **Accepted strength.** No read-then-branch, no retry loop, no lock.

> **Known trade-off.** H2 cannot parse `ON CONFLICT`, so the portable suite exercises the
> constraint through the exception path while the concurrency claim rests on the PostgreSQL suite.

## Playback progress and Continue Watching

Progress is one atomic PostgreSQL `INSERT … ON CONFLICT … DO UPDATE`, deterministic last-write-wins,
status independent by design.

Continue Watching (Phase 9) is a **pure read** over that same table, joined to the Asset that still
owns the row. It deliberately carries **no Asset-status filter**, because progress itself has none;
adding one would make the list disagree with the endpoint feeding it.

Phase 10 measured the query on real PostgreSQL and found a genuine cliff: at a 5 000-Asset
Workspace the planner fell back to a sequential scan removing 79 609 rows (1 009 buffers,
6.9–26.2 ms). `V7` adds `(user_id, updated_at DESC)`, which restores an index scan (143 buffers,
0.24–0.91 ms) and also improves the realistic shape (249 → 49 buffers).

> **Accepted strength.** The index was added from a measured plan, and its column order is
> justified by the actual join path rather than guessed.

> **Known trade-off.** A partial index restricted to resumable rows measured slightly better and
> smaller, but H2 cannot parse partial indexes and the portable tests run the same migration chain.
> Splitting the chain per vendor would mean the portable tests stop validating the production
> schema, so the portable composite index was chosen and the small difference accepted.

## Authorization

There is one authorization boundary and everything inherits it: a Workspace is owned by a user;
Assets belong to a Workspace; every Asset read goes through `loadAuthorizedAsset`, which makes a
missing Asset and a foreign Asset indistinguishable.

Consistently proven at runtime across phases: a second authenticated user receives
`404 WORKSPACE_NOT_FOUND` / `404 ASSET_NOT_FOUND` / `404 SAVED_MOMENT_NOT_FOUND` and never a
`403` that would confirm existence. Error bodies carry no SQL, table name, identifier or stack
trace.

Search adds a defence-in-depth check: candidates returned by Elasticsearch are filtered against the
PostgreSQL-authorized scope before result policy, and out-of-scope hits are discarded with a
bounded warning rather than failing the request.

> **Accepted strength.** Authorization is enforced at one place and re-checked where derived state
> could drift.

> **Technical debt.** `legacy_session` is the real mode; `keycloak_jwt` is a foundation with a known
> gap — a native media element cannot carry an in-memory bearer token, so Upload playback is
> unavailable in that mode (recorded in Phase 4). Closing it needs a signed-URL or cookie strategy
> that has not been designed.

> **Accepted strength.** The development identity fallback (`X-Current-User-Id` header,
> `POST /api/auth/session`, default local user) has been removed outright: no runtime profile
> resolves an anonymous request to a user, so the Phase 10 production-like guard profile and its
> startup validator are gone too.

## Failure isolation

| Dependency down | Effect |
|---|---|
| Elasticsearch | search returns bounded `503`; everything else works |
| Kafka | async processing and title sync stop; reads unaffected |
| MinIO | Upload playback and Upload deletion fail with bounded `503` |
| FastAPI | new transcriptions stop; existing content fully usable |
| PostgreSQL | the service does not start — correct, it is product truth |

> **Accepted strength.** Failures are scoped to the capability that needs the dependency.

> **Known trade-off.** Asset title change requires Elasticsearch *and* an already-indexed Asset;
> otherwise it returns `503` rather than accepting the rename and reconciling later. Observed in
> Phase 9. Refusing to report success when the derived sync matched nothing is defensible, but it
> couples a product write to derived-state availability.

## Deployment trade-offs

Single-machine Compose: one broker, one Elasticsearch node, one PostgreSQL, no TLS between
services, no orchestration. Bounded memory limits and readiness-based startup make it reproducible
on a laptop.

> **Known trade-off.** This is a development and demonstration topology. It has no HA, no backup
> automation, no secret manager and no horizontal scaling story.

> **Future scaling trigger.** The first real trigger is not traffic — it is *transcription
> throughput*. Whisper is the only component that saturates a machine, and it is already isolated
> behind Kafka, so scaling it means running more FastAPI workers rather than changing the product
> core.

## Summary of findings

| Classification | Findings |
|---|---|
| **Accepted strength** | Executable module boundaries; PostgreSQL truth with stale-hit validation; processor cannot push product state; outbox/inbox without dual write; single authorization boundary; database-owned concurrency for progress and saved moments; measured index decisions |
| **Known trade-off** | Duplicated per-consumer Asset projections; hydration cost per search; single-broker / single-node topology; wholesale transcript replacement; portable-over-optimal index; title change coupled to search availability; H2 cannot exercise PostgreSQL-specific SQL |
| **Technical debt** | `keycloak_jwt` media-playback gap; no dead-letter topic or operator recovery UI; snake_case/camelCase tolerance in the result parser; `study` compatibility names in frontend internals |
| **Future scaling trigger** | Transcription throughput before request throughput; context-hydration latency floor; search corpus growth past a single Elasticsearch node |
