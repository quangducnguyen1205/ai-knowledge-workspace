# Project3 — AI Knowledge Workspace Final Baseline

## Status

Project3 is a locally integrated AI knowledge workspace with Spring Boot as the
product core, FastAPI as an internal processing/provider service, and a React
frontend that calls Spring only. This document consolidates the final P3-S5
repository baseline and the evidence accepted for submission.

The submission code baseline was statically revalidated on 2026-07-14. The Spring suite,
FastAPI suite and import/configuration checks, and frontend test/typecheck/build
all passed. At that frozen C1 baseline, the Spring Modulith ratchet reported 79
reviewed non-cycle exposure messages and zero cycle messages. The subsequent
behavior-preserving v1.1 architecture cleanup replaced that ratchet with strict
verification at zero violations and zero cycles. The authoritative current rules
are in [`Project3 Final Architecture`](../architecture/backend-modularity-baseline.md);
the C1 counts below remain historical submission evidence.

Runtime evidence is inherited from the bounded Project3 validation phases; C1
did not start services or repeat browser, media, provider, database, or Kafka
experiments. FastAPI B3.R1 remains classified
`P3_S5_B3_R1_RUNTIME_PARTIAL`; the integrated default path passed, and the
partial classification records compatibility/recovery evidence depth rather
than a known refactor defect. Frontend B4.R1 remains classified
`P3_S5_B4_R1_BROWSER_PASSED`.

## Product purpose

Project3 lets a user upload media into an owned workspace, wait for durable
asynchronous transcription and automatic indexing, search the canonical
transcript, ask a grounded question, and navigate from a citation back to the
supporting transcript context. The architecture deliberately keeps product
truth and policy in Spring while treating processing, search, storage, and LLM
systems as internal adapters or derived infrastructure.

The project demonstrates:

- workspace-scoped ownership and authorization;
- durable at-least-once cross-service processing;
- canonical transcript snapshots in PostgreSQL;
- Elasticsearch as a rebuildable derived index;
- grounded assistant answers with Spring-controlled citation identity;
- bounded retry, recovery, compatibility, and operator escape paths; and
- behavior-preserving modular-monolith and feature-ownership refactors.

It does not claim production-scale capacity, complete provider quality,
multi-tenant collaboration, or elimination of all architecture debt.

## Repository manifest

The following exact commits were clean, on `main`, and synchronized with their
configured upstreams before C1 validation:

| Repository | Role | Configured remote | Validated code HEAD | Primary stack | Canonical validation |
|---|---|---|---|---|---|
| `ai-knowledge-workspace` | Spring product core and submission documentation | `git@github.com:quangducnguyen1205/ai-knowledge-workspace.git` | `2ebe71b9c02d3528e9e717be742d487c22a93fad` | Java 21, Spring Boot 3.3.5, Maven | `mvn -q -f services/workspace-core/pom.xml test` |
| `DemoFastAPI` | Internal processing, result delivery, and provider adapter | `https://github.com/quangducnguyen1205/DemoFastAPI.git` | `1bb878f6e430cebce7bdf9ea4c297d4c1aa023e4` | Python, FastAPI 0.116.1, SQLAlchemy 2.0.42, Celery | `PYTHONPATH=backend python -m unittest discover -s backend -p 'test_*.py'` |
| `ai-knowledge-workspace-fe` | Browser presentation and interaction | `https://github.com/quangducnguyen1205/ai-knowledge-workspace-fe.git` | `b71e32615ad1ee4c468b1ffe01f3603ea98f8eed` | TypeScript 5.6, React 18.3, Vite 5.4, pnpm | `pnpm test && pnpm typecheck && pnpm build` |

The Spring submission documentation commit is intentionally layered on the
validated product-code HEAD. It cannot self-record its own hash without changing
that hash. The annotated `project3-submission-v1` tag is the canonical immutable
reference to the final documentation commit; the code HEAD above remains the
exact behavior baseline tested by C1.

## System ownership

### Browser/frontend

The frontend owns presentation, routing, session/bootstrap UI, upload
interaction, lifecycle-polling presentation, search interaction, assistant
interaction, and citation navigation. Its production source calls Spring
`/api/...` endpoints through the shared HTTP boundary. It does not call FastAPI,
Kafka, Celery, Elasticsearch, MinIO, or an LLM provider directly.

### Spring product core

Spring owns public HTTP APIs; authentication and authorization integration;
workspaces; asset metadata and lifecycle; canonical transcript snapshots;
PostgreSQL product truth; durable processing and indexing intent; processing
result validation, inbox idempotency, and product application; indexing and
search policy; assistant context and citation policy; and the public assistant
API.

### FastAPI internal service

FastAPI owns the processing-request Kafka adapter, idempotent Celery dispatch,
worker execution, media acquisition, transcription/provider adapters,
processing artifacts, the durable processing-result outbox, result relay and
reconciliation, and the internal LLM-provider adapter. It does not own public
product state, workspace authorization, canonical transcripts, or browser-facing
product APIs.

### Infrastructure

- PostgreSQL is Spring product truth. FastAPI uses isolated processing scratch
  and outbox state, not a second product truth.
- MinIO stores binary media and processing artifacts.
- Kafka transports asynchronous integration events; it is not product storage.
- Redis supports cache/Celery infrastructure only.
- Elasticsearch is a derived search index and is not authoritative product
  state.
- Ollama or another configured provider is reached only through the internal
  FastAPI assistant adapter.
- Nginx/Vite delivers or proxies the frontend; product calls terminate at
  Spring.

## Final architecture

The normal product path is an asynchronous integration across three repositories
with explicit ownership at every boundary:

```text
React browser
  -> Spring public API and PostgreSQL product transaction
  -> Spring request outbox and Kafka
  -> FastAPI request consumer and Celery worker
  -> FastAPI result outbox and Kafka
  -> Spring result inbox and canonical transcript snapshot
  -> Spring indexing intent and Elasticsearch derived index
  -> Spring search/assistant APIs
  -> React search, answer, citation, and transcript navigation
```

Within Spring, consumer-owned ports and state-owning adapters keep product
modules acyclic. Feature-owned event codecs write neutral `OutboxDraft` values;
the outbox module owns only generic persistence, relay, failure classification,
and bounded reconciliation. Processing-result and indexing listeners are thin
integration adapters. Application use cases own product orchestration, and the
indexing executor preserves the database-begin, external-write,
database-finalize split.

Within FastAPI, request ingestion, execution, result recording, durable delivery,
and runtime composition have explicit boundaries. Stable ASGI, Celery, consumer,
manual-relay, and automatic-relay entrypoints remain thin adapters. Within the
frontend, shared HTTP behavior and feature APIs are separate from upload,
lifecycle, search, assistant, and citation presentation.

## Integrated default flow

1. The browser uploads an asset to the Spring upload API.
2. Spring validates the user/workspace boundary, stores the binary through the
   object-storage adapter, and atomically persists asset metadata, a processing
   job, and a processing-request outbox row.
3. The Spring relay publishes `asset.processing.requested.v1` to Kafka using the
   preserved event identity, key, and versioned payload contract.
4. The FastAPI consumer validates the event, idempotently accepts it, and
   dispatches the stable Celery task.
5. The worker acquires the media, transcribes it, stores the processing artifact,
   and records exactly one durable success or failure result intent.
6. The FastAPI result relay publishes the versioned processing result to Kafka.
7. Spring parses the result, uses its consumed-result inbox for idempotency,
   retrieves and validates a successful artifact, updates the processing job,
   and applies the asset outcome.
8. Spring replaces the canonical transcript snapshot and creates automatic
   indexing intent in the existing successful product transaction.
9. The indexing path performs a database begin check, writes the derived
   Elasticsearch documents outside that database transaction, and finalizes only
   after the fingerprint/state recheck. The asset becomes `SEARCHABLE`.
10. Frontend lifecycle polling observes `PROCESSING` -> `TRANSCRIPT_READY` ->
    `SEARCHABLE` through Spring and stops at the terminal searchable state.
11. Search requests go to Spring, which queries Elasticsearch while preserving
    PostgreSQL ownership and authorization.
12. Assistant requests go to Spring. Spring selects bounded authorized context,
    assigns canonical source identities, calls the internal FastAPI provider
    adapter, validates mapped citations, and returns the public answer.
13. The frontend renders citations in validated order and navigates to the
    corresponding transcript row/context through the Spring-backed asset route.

## Compatibility and recovery surfaces

These surfaces remain functional but are not the normal integrated path:

| Surface | Status | Purpose |
|---|---|---|
| Spring `direct_upload` and `compatibility` profile | Deprecated, functional | Bounded rollback to direct FastAPI processing |
| `make run-compatibility` | Deprecated, functional | Explicit compatibility startup |
| `make run-standalone` | Deprecated alias, functional | Legacy local caller compatibility |
| FastAPI direct processing endpoint | Deprecated, functional | Spring rollback and audited standalone use |
| Explicit indexing API and frontend action | Supported recovery | Recover a `TRANSCRIPT_READY` asset when automatic indexing does not complete |
| Manual/one-shot relays | Supported recovery | Bounded operator publication through the existing relay state machine |
| Exact-ID processing-result recovery | Supported recovery | Explicitly re-apply an eligible selected result through the normal use case |
| Failed-outbox reconciliation | Enabled in `project3`, bounded | Requeue only typed transient exhausted rows after cooldown and below the maximum recovery-cycle limit |
| Legacy-session authentication | Retained default/fallback | Authentication cutover is a separate decision |

Unknown, permanent, historical-unclassified, and recovery-exhausted outbox rows
remain terminal for manual review. No compatibility path is scheduled for
removal by this submission, and none is safe to delete solely because the normal
path passed.

## Data truth and infrastructure boundaries

The architecture separates authoritative product writes from derived or
transport state:

- Spring PostgreSQL owns users, workspaces, assets, processing/indexing product
  state, canonical transcript snapshots, durable intent, and consumed-result
  idempotency.
- FastAPI PostgreSQL state owns processing acceptance, scratch artifacts, and
  durable result-delivery intent only.
- Elasticsearch documents are derived from the canonical transcript and may be
  rebuilt; search success cannot redefine product truth.
- Kafka carries at-least-once events. Durable outboxes and idempotent consumers
  handle the gap between database commits and publication/consumption.
- MinIO objects and artifacts are referenced by owned metadata; they do not
  replace product lifecycle state.
- Redis/Celery and the provider are execution infrastructure, not product state.

## Security and trust boundaries

- The browser is an untrusted client and reaches only Spring product endpoints.
- Spring resolves the current identity, enforces workspace/asset scope, and owns
  the authorization decisions for upload, transcript, search, and assistant use.
- Provider output is untrusted. Spring supplies bounded sources, FastAPI exposes
  only provider-facing citation aliases, and Spring validates alias-mapped
  canonical source identities before returning citations.
- Secrets and provider configuration remain server-side and are not part of the
  frontend contract or this documentation.
- Internal FastAPI endpoints are infrastructure-facing. Production-grade
  service authentication/network policy remains deferred and must not be
  inferred from local integration success.
- At-least-once Kafka delivery is expected; consumers must remain idempotent.

## Key design decisions

1. **Spring is the product core.** Public APIs, authorization, product lifecycle,
   transcript truth, indexing decisions, and assistant policy remain together.
2. **FastAPI is an internal execution service.** Its processing/provider
   strengths are reused without making it a second product backend.
3. **Durable intent precedes asynchronous transport.** Both service directions
   use outboxes rather than treating Kafka acknowledgement as a product commit.
4. **PostgreSQL is authoritative; Elasticsearch is derived.** Indexing has
   explicit begin/write/finalize boundaries and final-state checks.
5. **Citation identity is Spring-controlled.** Provider aliases prevent raw
   product identifiers from becoming provider trust inputs.
6. **Failure recovery is typed and bounded.** Unknown failures fail closed;
   transient exhausted rows have cooldown and cycle limits.
7. **Compatibility remains visually secondary but operationally intact.** The
   coherent `project3` profile is the normal path; rollback and recovery paths
   require explicit operator intent.
8. **Refactoring preserves contracts.** Golden HTTP/Kafka tests, architecture
   ratchets, transaction characterization, and stable entrypoints protect the
   behavior baseline.

## Static validation

The C1 static validation executed from clean synchronized baselines:

| Repository | Result |
|---|---|
| Spring | 85 test suites, 464 tests, 0 failures, 0 errors, 0 skipped; application context/profile, HTTP/Kafka contracts, transaction boundaries, and architecture tests passed |
| Spring architecture | 79 reviewed violation messages, 0 cycle messages; committed fingerprint matched |
| FastAPI | 65 tests passed; `compileall` passed; stable ASGI/Celery/consumer/manual-relay/automatic-relay imports passed without service startup; base and Project3 Compose configurations parsed |
| Frontend | 15 test files and 74 tests passed; TypeScript typecheck passed; Vite production build passed |
| Git integrity | `git diff --check` passed and tracked worktrees stayed clean before documentation edits |

The exact commands and evidence classifications are recorded in
[`project3-validation-matrix.md`](project3-validation-matrix.md).

## Runtime and browser validation

Accepted pre-C1 runtime evidence includes ten consecutive fresh default-path
successes, controlled application and Kafka restarts, no duplicate terminal
product effects, compatibility rollback, explicit indexing recovery, grounded
assistant/citation passes, and a real transient outbox failure followed by
cooldown, automatic requeue, and successful final delivery.

FastAPI B3.R1 then exercised the refactored integrated path through request
ingestion, Celery, artifact/result outbox, Kafka result, Spring canonical
snapshot, automatic indexing, and `SEARCHABLE`. It found no refactor-induced
runtime defect. Its final classification is intentionally
`P3_S5_B3_R1_RUNTIME_PARTIAL`: direct-upload processing was not re-exercised end
to end, transient result recovery was observed but not deterministically driven
through a complete controlled cycle, and the manual relay check was a bounded
successful no-op.

Frontend B4.R1 is `P3_S5_B4_R1_BROWSER_PASSED`. The browser run proved upload
through Spring, lifecycle polling, automatic indexing, `SEARCHABLE`, search,
assistant answer, citation rendering/navigation, desktop/mobile usability, and
the absence of direct browser requests to FastAPI.

C1 did not repeat runtime or browser validation. These are bounded local
observations, not load, production-availability, transcription-quality, or
security-certification claims.

## Known limitations and deferred debt

- The C1 submission recorded 79 reviewed non-cycle exposure messages. That
  specific debt is resolved by the later strict zero-violation architecture gate;
  the historical C1 validation table remains unchanged as evidence.
- `TranscriptSearchIndexClient` still combines several Elasticsearch concerns.
- Processing transcript-artifact HTTP retrieval retains its characterized
  transaction participation; C1 does not redesign that boundary.
- PostgreSQL concurrent claim by two reconcilers was not directly exercised in
  the final resilience drill, and a full three-cycle `RECOVERY_EXHAUSTED`
  runtime fixture was unavailable.
- Neither service has a full Kafka DLQ topology. FastAPI also retains the current
  SQLAlchemy metadata/narrow schema upgrader rather than Alembic and has no
  crash-age policy for an abandoned `publishing` row.
- The FastAPI direct-upload path and manual relay have only the B3.R1 evidence
  depth described above; their earlier compatibility behavior remains retained.
- Internal FastAPI HTTP authentication and production network policy remain
  deployment hardening work.
- Frontend `AppRouter` still coordinates some cross-feature reconciliation;
  `AssetDetailScreen` has a broad prop surface; workspace feature ownership can
  be narrowed further.
- Authentication cutover, collaboration/membership/RBAC, production sizing,
  observability/SLOs, provider diversity, and broad language-quality claims are
  outside this baseline.

## Code reading order

Read by product flow rather than repository folder order:

1. **Ownership and lifecycle:** Spring `WorkspaceAccessPolicy`, `Asset`,
   `AssetStatus`, transcript snapshot model, processing job, and indexing job.
2. **Browser boundary:** frontend `shared/api/http-client.ts`, upload API/hook,
   then Spring `AssetController` and `UploadAssetApplicationService`.
3. **Processing intent:** Spring `ProcessingRequestApplicationService`, the
   processing request codec, neutral `OutboxDraft`, `OutboxRelayService`, and
   processing relay scheduler.
4. **FastAPI execution:** consumer adapter,
   `processing/application/dispatch.py`, Celery adapter/task, and
   `processing/application/execute.py`.
5. **Durable result delivery:** FastAPI result recorder, event codec, outbox
   repository, relay, and reconciliation application services.
6. **Product result application:** Spring result Kafka listener, event handler,
   `ProcessingResultInbox`, `ApplyProcessingResultApplicationService`, artifact
   gateway, and `AssetTranscriptSnapshotService`.
7. **Indexing and lifecycle:** Spring indexing request service, indexing listener,
   `ExecuteIndexJobApplicationService`, `IndexingAttemptTransactionService`, and
   `TranscriptSearchIndexClient`; then frontend `use-asset-lifecycle.ts`.
8. **Search and assistant:** frontend search controller, Spring search and
   assistant services, FastAPI assistant adapter, then frontend assistant and
   citation components/navigation.
9. **Resilience last:** outbox entities/state transitions, typed failure
   classifiers, Spring/FastAPI reconciliations, idempotency inboxes, manual
   recovery, and compatibility adapters.

On a first reading, skip framework configuration, JPA/SQLAlchemy boilerplate,
low-level client response parsing, and compatibility branches until the normal
flow is clear.

## Supported local validation commands

Run commands from the root of the repository they belong to. They are static or
test/build checks; they do not start the integrated runtime.

Spring:

```bash
mvn -q -f services/workspace-core/pom.xml test
git diff --check
```

FastAPI:

```bash
PYTHONPATH=backend python -m unittest discover -s backend -p 'test_*.py'
python -m compileall -q backend/app
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=backend python -c \
  'from app.main import app; from app.core.celery_app import celery_app; import app.consumers.asset_processing_consumer; import app.relays.processing_outbox_relay; import app.relays.processing_outbox_auto_relay; assert app is not None and celery_app is not None'
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.project3.yml config --quiet
git diff --check
```

Frontend:

```bash
pnpm test
pnpm typecheck
pnpm build
git diff --check
```

These commands assume the existing frozen dependency trees are present. They do
not substitute for the bounded runtime and browser evidence described above.

## Submission commits and tags

The final code commits verified for submission are:

- Spring: `2ebe71b9c02d3528e9e717be742d487c22a93fad`
- FastAPI: `1bb878f6e430cebce7bdf9ea4c297d4c1aa023e4`
- Frontend: `b71e32615ad1ee4c468b1ffe01f3603ea98f8eed`

Each repository uses the same annotated local submission tag:
`project3-submission-v1`. In Spring the tag identifies the final documentation
commit layered on the verified code commit. In FastAPI and frontend it identifies
the exact verified code HEAD listed above. The tags are local submission
references unless explicitly pushed in a separate authorized operation.
