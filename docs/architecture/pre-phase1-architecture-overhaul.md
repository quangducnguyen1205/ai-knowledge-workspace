# Pre-Phase-1 Architecture Overhaul

Status: current architecture decision record and implementation report. This document describes
the Spring product core after the pre-Phase-1 overhaul. Historical Project3 evidence remains in
Git history; it is not a second source of current runtime truth.

## Verified baseline

The implementation baseline was local `main` at
`758bbeda8d4ace804d47a783875104051b1fda54`. The worktree was clean and matched `origin/main`.
The existing architecture already had real Spring Modulith boundaries, transactional outbox and
inbox processing, owner-scoped workspace access, PostgreSQL canonical transcript snapshots, and
Elasticsearch as derived state. The gaps were mainly inside modules: some application services
depended on Spring Data or persistence implementations, controllers exposed JPA entities, reads
could trigger FastAPI I/O and writes, and an obsolete direct-upload path duplicated the normal
Project3 workflow.

The target remains a DDD-oriented modular monolith. It is not CQRS, a microservice split, or a
mechanical domain/persistence model duplication.

## Target architecture

The default dependency direction is:

```text
HTTP controller / Kafka listener / scheduler
                  |
                  v
       application input contract
                  |
                  v
       application/domain policy
                  |
                  v
       application-owned output port
                  |
                  v
 package-private persistence/external adapter
                  |
                  v
 package-private Spring Data repository / SDK / remote API
```

The normal processing flow is the only upload-processing flow:

```text
AssetController
  -> AssetUploadUseCase
  -> UploadAssetApplicationService
  -> ObjectStorageApplication                     (outside DB transaction)
  -> AssetUploadTransaction                       (DB transaction)
       -> AssetStore
       -> ProcessingRequestApplication
            -> ProcessingJobStore
            -> OutboxEventStore
  -> request relay -> Kafka -> FastAPI
  -> result listener -> ProcessingResultEventHandler
       -> ApplyProcessingResultApplicationService (DB transaction)
            -> TranscriptArtifactGateway          (HTTP call intentionally retained in transaction)
            -> ProcessingResultAssetPort
            -> ProcessingJobStore
            -> ProcessingResultEventStore
  -> indexing request/outbox -> Kafka -> ExecuteIndexJobApplicationService
       -> begin DB transaction
       -> SearchIndexPort                          (Elasticsearch outside DB transaction)
       -> finalize DB transaction
```

## Over-engineering findings and decisions

| Symbol or area | Evidence and protected behavior | Decision | Result / risk |
|---|---|---|---|
| `DirectProcessingCompatibilityAdapter` | Combined upload orchestration, FastAPI transport, task identifiers and persistence while duplicating the Kafka/outbox normal path. | **DELETE** | Direct upload/profile rollback is intentionally removed. Local databases must be recreated. |
| `AssetPersistenceService` | Exposed broad asset, transcript, ownership, delete and title persistence operations to application code. | **SPLIT** | Replaced by need-based `AssetStore` and `CanonicalTranscriptStore`, implemented by one package-private adapter. |
| Public `AssetRepository` and `AssetTranscriptRowSnapshotRepository` | Spring Data APIs were directly injectable outside persistence. | **INTERNALIZE** | Replaced by package-private `AssetJpaRepository` and `CanonicalTranscriptJpaRepository`. |
| `AssetDeletionService` and `AssetTitleUpdateService` | Separate command services shared authorization and coordinated derived/external state. | **MERGE** | `AssetCommandApplicationService` groups cohesive asset mutations behind `AssetCommandUseCase`. |
| `AssetQueryApplicationService` status/transcript fallback | Query paths polled FastAPI, changed job/asset state and captured transcript snapshots. | **SIMPLIFY** | Queries are now side-effect free and read only PostgreSQL product truth. |
| Controller responses using `Asset` | JPA fields could become an accidental HTTP contract. | **DELETE boundary leak** | `AssetView` is mapped to web-only `AssetResponse`; endpoint paths and JSON field names remain stable. |
| `WorkspaceQueryApplicationAdapter` | Forwarded calls without transformation or isolation. | **DELETE** | `WorkspaceService` directly implements cohesive `WorkspaceUseCase`; workspace remains simple CRUD. |
| Public `WorkspaceRepository` | Leaked Spring Data across the workspace module. | **INTERNALIZE** | `WorkspaceStore` plus package-private `WorkspacePersistenceAdapter` and `WorkspaceJpaRepository`. |
| Public processing repositories | Application services depended on framework repositories. | **INTERNALIZE** | `ProcessingJobStore` and `ProcessingResultEventStore` isolate package-private JPA repositories. |
| Public indexing repository | Framework paging/query names were visible to application code. | **INTERNALIZE** | `SearchIndexJobStore` describes indexing-job needs; adapter owns derived queries. |
| `OutboxPersistenceService` | Pass-through wrapper around a public repository. | **DELETE** | `OutboxEventStore` is the application capability and one adapter owns persistence. |
| Infrastructure-owned `OutboxMessagePublisher` | Application relay imported an infrastructure package for its outbound capability. | **MOVE** | Publisher and failure-classification ports are application-owned; Kafka/logging adapters implement them. |
| Search web `Response` records under `application.query` | Transport models polluted the application query contract. | **MOVE/SPLIT** | `SearchQuery`, `SearchResult` and `SearchHit` are application models; web response records stay at the HTTP boundary. |
| Assistant services accepting web request records | Application behavior depended on controller DTOs. | **SPLIT** | Input ports and application command/query models are transport-neutral; controllers map HTTP records. |
| Public identity repository | Auth application services imported Spring Data. | **INTERNALIZE** | `UserAccountStore` is application-owned; JPA implementation is internal. |
| One interface for every method | Would increase vocabulary without a meaningful boundary. | **REJECT** | Cohesive command/query/use-case contracts group related operations; simple internal services stay concrete. |
| Separate domain and persistence models everywhere | Current aggregates are simple and JPA annotations do not distort domain behavior. | **REJECT** | Entities remain combined but cannot cross HTTP or module boundaries. |
| Full CQRS / separate read database | No evidence requires independently deployed read models. | **REJECT** | Fit-for-purpose CQS is used only at behaviorally asymmetric boundaries. |
| Transaction seam classes | `AssetUploadTransaction`, `AssetMutationTransaction` and indexing begin/finalize seams prevent network calls from being hidden inside product transactions. | **KEEP** | They are deliberate Spring proxy and consistency boundaries, not layer ceremony. |
| Result artifact HTTP inside transaction | Moving it would require a new reservation/idempotency design and could invalidate atomic canonical replacement. | **KEEP** | Existing correctness and recovery semantics are preserved and architecture-tested. |
| Elasticsearch adapter breadth | One adapter supports narrow consumer-owned ports and centralizes mapping/error translation. | **KEEP** | Splitting it would not improve dependency direction. |
| Legacy session authentication | Independent product/runtime decision; still used by local identity and smoke flows. | **KEEP** | No tenant or Keycloak-only abstraction is introduced. |

## Module boundaries after the overhaul

| Module | Input boundary | Application policy | Output ports | Internal adapters |
|---|---|---|---|---|
| `workspace` | `WorkspaceUseCase` | `WorkspaceService` | `WorkspaceStore`, asset-usage consumer port | package-private `WorkspacePersistenceAdapter` / `WorkspaceJpaRepository` |
| `asset` | `AssetUploadUseCase`, `AssetCommandUseCase`, `AssetQueryUseCase` | upload, authorized reads, commands, transcript replacement | `AssetStore`, `CanonicalTranscriptStore`, storage/search/processing capabilities | package-private `AssetPersistenceAdapter` and two JPA repositories |
| `processing` | request API, result handler, recovery API | request intent, correlation, inbox idempotency, result application | `ProcessingJobStore`, `ProcessingResultEventStore`, artifact and asset ports | package-private processing persistence adapter/repositories; FastAPI artifact adapter |
| `search` | `SearchQueryUseCase`, explicit/automatic indexing APIs | query policy, snapshot fingerprint, indexing job lifecycle | search index ports, `SearchIndexJobStore`, transcript and asset capabilities | package-private job persistence adapter/repository; Elasticsearch client adapter |
| `assistant` | answer command and context query use cases | context limits, answer/citation validation | provider and search/context ports | FastAPI provider adapter and state-owner adapters |
| `outbox` | relay, recovery and operator entry points | claim/publish/finalize/retry classification | `OutboxEventStore`, `OutboxMessagePublisher`, failure classifier | package-private persistence adapter/repository; Kafka/logging publishers |
| `common` identity | authentication/OIDC application services | server-owned user identity and provisioning | `UserAccountStore` | package-private identity persistence adapter/repository |
| `storage` | `ObjectStorageApplication` | object-key and upload policy | technical S3 capability | S3/MinIO client adapter |
| `integration` | none exposed to web | neutral FastAPI integration mapping only | HTTP transport | package-private FastAPI adapters and wire DTOs |

Spring Data repositories and persistence adapters in the refactored slices are package-private.
JPA models remain module-owned implementation types. Controllers and listeners cannot inject a
Spring Data repository.

## Naming rules

- `*UseCase` is an inbound application contract, grouped by cohesive command or query behavior.
- `*ApplicationService` implements use-case policy or application orchestration.
- `*Store` is an application-owned persistence capability; it never exposes Spring Data,
  `Pageable`, `Sort`, `EntityManager`, or SDK types.
- `*JpaRepository` is a package-private Spring Data declaration.
- `*PersistenceAdapter` is package-private and implements one or more owning-module stores.
- `*Command`, `*Query`, `*Result`, and `*View` are application values whose suffix matches use.
- `*Request` and `*Response` are reserved for transport boundaries.
- `*Client` remains acceptable only for a real technical HTTP/SDK client.

Existing accurate names are retained. There is no repository-wide suffix rewrite.

## Transaction rules

| Command | Transaction and I/O sequence | Failure and idempotency |
|---|---|---|
| Asset upload | Validate and upload object outside a DB transaction; `AssetUploadTransaction` atomically writes asset, processing job and request outbox. | DB failure triggers best-effort object deletion; event ID and inbox/outbox rules remain unchanged. |
| Asset title update | Authorized load; update derived search title first only when searchable; `AssetMutationTransaction` commits product title. | Required search failure prevents a false successful product response. |
| Asset deletion | Authorized load; delete derived transcript documents and stored object; then `AssetMutationTransaction` deletes canonical transcript/job/asset state. | Required cleanup failure prevents success and database deletion. |
| Processing result | `ProcessingResultEventHandler` and application service join one DB transaction; artifact HTTP retrieval remains inside; canonical transcript, asset/job and inbox status change atomically. | Known artifact/apply failures become durable bounded `FAILED`; unexpected failures roll back and are redelivered. |
| Index request | One DB transaction writes indexing job and outbox intent. | Unique active fingerprint prevents duplicate active work. |
| Index execution | Begin transaction marks attempt; Elasticsearch write is outside DB transactions; finalize transaction marks indexed/searchable. | Failure finalization records retry state; Elasticsearch remains rebuildable. |
| Workspace CRUD | `WorkspaceService` owns read-only/read-write transaction boundaries. | Owner predicates and not-found mapping remain unchanged. |
| Outbox relay | Claim transaction, broker call outside DB transaction, then finalize/failure transaction. | At-least-once delivery and bounded recovery semantics remain. |

## Compatibility decisions

Removed from Spring:

- the `compatibility` profile and `direct_upload` trigger mode;
- direct FastAPI upload/status orchestration and its task/video identifiers;
- GET-like status refresh and transcript load-or-capture writes;
- deprecated `make run-compatibility` and `make run-standalone` entry points.

Retained:

- the normal Project3 Kafka topic names, keys, version-1 request/result payloads and outbox/inbox
  idempotency;
- FastAPI artifact retrieval by processing request event ID;
- explicit indexing and exact-ID/manual recovery controls;
- legacy session authentication and local identity behavior.

The FastAPI repository is not modified. Its standalone endpoint may continue to exist, but Spring
no longer calls it. This is an intentional removal of obsolete Spring behavior, not a public
Kafka or HTTP contract change.

## Data and migration decision

The historical Flyway chain represented local development evolution and included obsolete
direct-upload columns. Existing local data compatibility is explicitly not required, so the
selected strategy is one clean `V1__create_product_schema.sql` baseline.

The baseline contains credential user accounts, owner-scoped workspaces, assets, processing jobs,
canonical transcript rows,
outbox events, consumed processing-result events, and search indexing jobs with their current
foreign keys, uniqueness rules, recovery metadata, and indexes. It omits `fastapi_task_id` and
`fastapi_video_id`. `processing_request_event_id` is required and unique for every processing
job. Local PostgreSQL state created from the old chain must be recreated rather than migrated.

## Validation evidence

The following rules are executable in `ModuleBoundaryRulesTest` and
`BackendModularityBaselineTest`:

- application code cannot depend on infrastructure, Spring Data, or web transport;
- inbound adapters cannot access repositories;
- Spring Data repositories are non-public;
- JPA entities/repositories do not cross module boundaries;
- FastAPI wire DTOs and Elasticsearch implementation stay in their integration owners;
- indexing does not call Elasticsearch inside its database transaction seams;
- `common` and generic outbox remain feature-neutral;
- removed compatibility types cannot silently return;
- Spring Modulith verification remains cycle-free.

`CleanBaselineMigrationTest` migrates an empty database, validates JPA against it, and checks the
absence of obsolete direct-upload columns. The canonical validation command remains:

```bash
mvn -q -f services/workspace-core/pom.xml test
```

Runtime validation requires project-specific PostgreSQL, Kafka, MinIO, Elasticsearch, FastAPI,
Redis and optional assistant-provider availability. Results must be recorded as passed or blocked;
static tests are not evidence that an unavailable provider flow ran.

### Runtime result for this overhaul

- `docker compose --env-file .env -f infra/docker-compose.dev.yml config` parsed successfully and
  resolved the expected PostgreSQL, Elasticsearch, MinIO, Kafka and helper services.
- Inventory found no existing `infra` containers. It found the four product data volumes plus the
  opt-in Keycloak PostgreSQL volume; the Keycloak volume was explicitly excluded from reset scope.
- Deleting the four persistent product volumes was blocked by the execution approval layer. No
  persistent volume was deleted or changed by this validation.
- A temporary PostgreSQL 15.18 container used `tmpfs` with no attached volume. Spring applied
  exactly one Flyway V1 to an empty database, Hibernate validated the mappings, and the application
  started on port 18081.
- Real local HTTP calls registered one legacy-session user (`201`) and loaded/created its default
  workspace (`200`). PostgreSQL inspection showed one successful Flyway row, all eight product
  tables, a non-null `processing_request_event_id`, both transcript uniqueness constraints, and
  one user-account/one workspace row.
- The Spring process and temporary PostgreSQL container were stopped; `--rm` removed the tmpfs
  container. No validation container or persistent validation volume remains.
- Upload/media, Kafka/FastAPI result, duplicate-result, automatic indexing/search,
  assistant/provider and deletion-cleanup runtime flows are **BLOCKED** in this run because the
  persistent integration reset was not approved and no authorized real media/provider fixture was
  available. Their automated contract/transaction tests passed, but that is `TEST`, not `RUNTIME`.

## Rejected options

- No microservices, tenant context, generic repository framework, service locator, broad shared
  orchestration module, separate read database, or timestamp-aware transcript fields.
- No speculative interface is added merely because the reference accounting project has one.
- No change to result-application transaction topology is combined with this boundary cleanup.
- No preservation layer is added for local data or deleted direct-upload behavior.

## Rollback

Code recovery uses the recorded original commit and Git reflog. Runtime rollback is code/history
rollback followed by clean recreation of project-specific data stores; old local data is not a
supported rollback artifact. The immutable annotated `project3-submission-v1` tag is not moved.
