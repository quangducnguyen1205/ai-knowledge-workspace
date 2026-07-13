# Backend Modularity Baseline

Status: P3-BE0 audit baseline plus P3-BE1 Spring Modulith verification ratchet. This document is evidence from the current Spring Boot backend and does not change behavior, packages, dependencies, APIs, schemas, or event contracts.

## Scope And Non-Goals

This baseline maps the current modular-monolith shape of `services/workspace-core` so the next phase can introduce the smallest useful architecture verification and only then refactor packages or public module APIs.

Non-goals:

- no microservice split;
- no broad clean-architecture or hexagonal rewrite;
- no Java package reorganization in this phase;
- no runtime Spring Modulith feature, ArchUnit, jMolecules, or production dependency addition;
- no database schema, Flyway, API, Kafka event, outbox, auth, processing, search, or assistant behavior change.

The current backend is one Maven module, `services/workspace-core/pom.xml`, on Spring Boot `3.3.5` and Java `21`. P3-BE1 adds only Spring Modulith `1.2.5` as test-scoped architecture inspection support through the Spring Modulith BOM plus `spring-modulith-starter-test`. After P3-BE2A, the production Java tree contains about `9,553` lines across the Spring application root `com.aiknowledgeworkspace.workspacecore`.

## P3-BE1 Verification Baseline

P3-BE1 `[ĐÃ XÁC MINH TỪ CODE]` adds `BackendModularityBaselineTest` under
`services/workspace-core/src/test/java/.../architecture/`. The test uses:

```java
ApplicationModules.of(WorkspaceCoreApplication.class)
```

with Spring Modulith's default direct-package detection. It does not use custom
detection, broad allow-lists, open modules, module exclusions, `@ApplicationModule`,
`@NamedInterface`, or `allowedDependencies`.

The test has two checks:

- the detected direct package roots remain:
  `asset`, `assistant`, `common`, `integration`, `outbox`, `processing`,
  `search`, `storage`, and `workspace`;
- the real `detectViolations()` output matches the committed baseline resource
  `services/workspace-core/src/test/resources/architecture/spring-modulith-violations-baseline.txt`.

This is a ratchet. A future failure means module detection changed or the
violation report changed. That can be good or bad, but it must be reviewed
intentionally instead of silently drifting in CI.

P3-BE1.1 stabilizes that ratchet by normalizing non-semantic Java source line
locations in the Modulith report. Entries such as `(AssetPersistenceService.java:104)`
are stored and compared as `(AssetPersistenceService.java)`: class/file names,
cycle text, module/package names, and dependency descriptions remain preserved,
but harmless line movement no longer fails the baseline. This does not suppress,
filter, or hash away any module violation.

Strict `ApplicationModules.verify()` is intentionally not green yet. The
P3-BE1.1 baseline recorded `137` violation messages, including dependency cycles
and non-exposed type dependencies.

## P3-BE2A Asset Application Boundary

P3-BE2A `[ĐÃ XÁC MINH TỪ CODE]` introduces a narrow public application boundary
owned by the `asset` root package. The new public contracts are:

- `AssetReadService` plus immutable read models (`AssetDetails`,
  `AssetIndexingSource`, `AssetTranscriptContext`, `AssetTranscriptRowView`) for
  authorized asset details, canonical transcript context, searchable visibility,
  and indexing-source reads.
- `AssetProcessingResultApplicationService` plus `AssetTranscriptRowInput` for
  applying the asset-owned portion of successful or failed processing results,
  including terminal asset state and canonical transcript snapshot replacement.
- `AssetSearchabilityService` for asset-owned lifecycle transitions to
  `TRANSCRIPT_READY` and `SEARCHABLE`.

Processing, search, and assistant code now consume these asset contracts instead
of the selected direct `AssetRepository`, `AssetTranscriptRowSnapshotRepository`,
`Asset`, `AssetService`, or `AssetPersistenceService` internals that P3-BE0
identified. Asset remains owner of asset lifecycle, canonical transcript
snapshots, transcript context, and searchability state. Search still owns
indexing jobs and Elasticsearch writes; processing still owns result-event
idempotency, processing jobs, FastAPI artifact retrieval, and result orchestration;
assistant owns context packing plus the public grounded answer orchestration.

The Modulith baseline changed intentionally after this refactor. Module roots are
unchanged, strict `ApplicationModules.verify()` is still not green, and the
committed violation report now records `161` messages: the same `51` cycle
messages and `110` non-exposed-type dependency messages. The increase is caused
by the new explicit asset public contracts and by preserving current transitional
edges such as `asset -> integration.fastapi`; it is a reviewed ratchet update,
not proof of full modularity.

P3-BE2B `[ĐÃ XÁC MINH TỪ CODE]` adds one more asset-owned public query:
`AssetWorkspaceUsageService.workspaceHasAssets(UUID workspaceId)`. Workspace
deletion now consumes this boolean application fact instead of injecting
`AssetRepository` and calling `countByWorkspace_Id` directly. Asset repository
ownership remains internal to the asset module. The module-level `workspace ->
asset` dependency remains intentional, and the same `161`-message Modulith
baseline remains a ratchet.

P3-F2A.1 `[ĐÃ XÁC MINH TỪ CODE]` exposes only the assistant-specific FastAPI
transport records and client as the named `integration::assistant` Modulith API.
The HTTP `FastApiAssistantClientImpl` lives under `integration.fastapi.assistant.internal`,
so the assistant module no longer depends on non-exposed FastAPI assistant
types. The baseline remains `161` messages; strict verification is still blocked
by older deferred edges.

## P3-S5.B1 Structural Foundation Ratchet

P3-S5.B1 `[VERIFIED BY TESTS]` establishes the first behavior-preserving module
boundaries without changing HTTP, Kafka, persistence, profile, or recovery
contracts:

- `outbox::application` is the named neutral API for enqueue, relay, automatic
  reconciliation, and retained manual recovery. Its immutable `OutboxDraft`
  carries only the generic persisted envelope values: event identity, type and
  version, aggregate identity, event key, and serialized payload. JPA entities
  and repositories remain internal to outbox.
- Processing owns the `asset.processing.requested` codec under
  `processing::request-event`; search owns the `asset.indexing.requested` codec
  under `search::indexing-request-event`. Contract tests freeze topic selection,
  event/key/aggregate identities, payload field names and values, null handling,
  and timestamp formats. In particular, processing payload `requestedAt` remains
  numeric epoch seconds while envelope `occurredAt` remains an ISO date-time.
- Relay callers now use `RelayRequest`, `RelaySelection`, and
  `RelayExecutionPolicy` instead of nullable event selectors and boolean failure
  switches. The existing claim/publish/failure state machine and publisher are
  still single implementations inside outbox.
- Feature-specific exception mappings now live in feature-owned advice classes.
  `common.web` retains neutral framework, validation, authentication, and
  fallback mappings plus the shared error response shape.
- OIDC provisioning calls a narrow `common::workspace-provisioning` port whose
  workspace-owned adapter creates the default workspace inside the existing
  user-creation transaction. This removes the reverse common-to-workspace
  repository/entity dependency without changing authentication behavior.

The architecture ratchet now fingerprints the exact normalized violation set
instead of storing thousands of repeated detail lines. It records `107`
violation messages and `5` cycle messages, down from `161` and `51`. Direct
`asset <-> outbox`, `common <-> search`, and `common <-> workspace` cycles are
removed. Dedicated ArchUnit rules prevent outbox from importing product feature
implementations and prevent `common.web` from importing feature or integration
packages.

Strict `ApplicationModules.verify()` is still intentionally red. The remaining
cycle paths are combinations of `asset`, `processing`, `search`, and `workspace`
orchestration and are deferred to P3-S5.B2. They are legacy debt recorded by the
ratchet, not the target architecture and not permission to introduce new edges.

## P3-S5.B2A Orchestration Cycle Elimination

P3-S5.B2A `[VERIFIED BY TESTS]` removes the five remaining product-orchestration
cycle messages through consumer-owned ports and state-owning adapters. Processing,
search, and workspace declare the asset capabilities they consume under their
named `application` interfaces; package-private asset adapters implement those
ports by delegating to the existing asset-owned services. Processing-result
idempotency and parsing remain in processing, canonical transcript and lifecycle
state remain in asset, indexing jobs and Elasticsearch writes remain in search,
and workspace deletion still consults the single asset-owned usage query.

The reverse direction is also narrowed. Asset now creates and reads processing
jobs through `processing::application`, and invokes automatic/explicit indexing
and search maintenance through `search::application`; it no longer imports the
processing or search repositories, entities, or concrete orchestration services.
All calls remain synchronous and join the same existing transaction boundaries.
No application event, extra database transaction, schema change, or contract
change was introduced.

The reviewed ratchet is now `101` violation messages and `0` cycle messages,
down from `107` and `5`. Strict verification remains red because non-cycle
named-interface/exposure debt still exists. Direct architecture rules now guard
the new directions: processing/search/workspace cannot depend on asset
implementations, while asset may consume only the processing/search application
boundaries (plus the existing public processing status enum used by the frozen
asset response contract). B2B may decompose orchestration classes only after
these zero-cycle rules and the event/error contract tests remain green.

## P3-S5.B2B Asset And Transcript Decomposition

P3-S5.B2B `[VERIFIED BY TESTS]` keeps the B2A dependency graph and makes the
normal asset flow readable through dedicated application use cases:

- `UploadAssetApplicationService` owns validation, workspace resolution, object
  storage, trigger selection, persistence delegation, and failure cleanup.
- `AssetQueryApplicationService` owns list/detail/status/transcript response
  projection; `AssetController`, title update, and deletion call it rather than
  the former god service.
- `AssetTranscriptSnapshotService` is the single canonical snapshot write owner.
  It filters unusable compatibility rows, replaces the PostgreSQL snapshot,
  creates automatic indexing intent through the existing search application
  API, and applies transcript-ready/failed lifecycle rules.
- `AssetTranscriptQueryService` is the single ordered canonical read owner for
  indexing sources, transcript context, assistant context, and search adapters.
- `DirectProcessingCompatibilityAdapter` contains the deprecated FastAPI direct
  upload/status/transcript mapping. It is package-private and delegates every
  captured transcript to the canonical snapshot service.

The upload coordinator and controller transcript fallback remain outside an
enclosing database transaction, while asset/job/outbox persistence, async result
application, and indexing fallback retain their existing transactional
participation. No event, HTTP, schema, profile, or compatibility contract is
changed. The reviewed ratchet is `83` violation messages and `0` cycle messages,
down from `101` and `0`. Strict verification remains red only for reviewed
non-cycle exposure debt. A thin non-bean `AssetService` facade remains for legacy
unit fixtures and is explicitly deferred to B2C; it delegates all methods and
contains no business rules.

## Current Package Inventory

Direct packages under `com.aiknowledgeworkspace.workspacecore`:

| Package | Files | Current responsibilities | Controllers | Services / schedulers | Repositories / entities | Event, adapter, or contract notes | Domain or technical |
|---|---:|---|---|---|---|---|---|
| `asset` | 44 | Asset metadata, focused upload/query use cases, canonical transcript snapshot write/read ownership, object-storage reference handling, compatibility adaptation, lifecycle persistence, and title/delete/index API entrypoints. | `AssetController` | `UploadAssetApplicationService`, `AssetQueryApplicationService`, `AssetTranscriptSnapshotService`, `AssetTranscriptQueryService`, `AssetPersistenceService`, `AssetSearchabilityService`, `AssetWorkspaceUsageService`, `AssetDeletionService`, `AssetTitleUpdateService`; package-private `DirectProcessingCompatibilityAdapter`; thin non-bean `AssetService` test facade | `AssetRepository`, `AssetTranscriptRowSnapshotRepository`; entities `Asset`, `AssetTranscriptRowSnapshot` | Controllers and B2A adapters consume focused asset use cases; FastAPI DTO mapping is confined to the compatibility adapter and canonical services contain no provider contracts. | Domain/application-specific with persistence and compatibility adapters kept explicit. |
| `assistant` | 13 | Assistant context pack API and grounded answer orchestration. | `AssistantContextController`, `AssistantAnswerController` | `AssistantContextService`, `AssistantAnswerService` | none | DTOs for context/answer request, source, citation, response; reuses search and asset context services; calls the named `integration::assistant` FastAPI contract for answer generation. | Domain/application-specific. |
| `common` | 35 | Technical cross-cutting concerns plus identity/auth and shared config. | `AuthController`, `HealthController`; `ApiExceptionHandler` advice | `AuthService`, `CurrentUserService`, `OidcUserProvisioningService` | `UserAccountRepository`; entity `UserAccount` | Security configs, current-user resolution, OIDC mapping/provisioning, Elasticsearch/FastAPI properties/configs, shared API error handling. | Mixed: identity domain plus technical/shared. |
| `integration` | 14 | FastAPI client boundary, assistant FastAPI named interface, processing FastAPI DTOs/exceptions, and HTTP implementations. | none | `FastApiProcessingClientImpl`, internal `FastApiAssistantClientImpl` components | none | Uses `RestClient`; DTOs for FastAPI upload/status/transcript artifact responses; exposes only assistant answer transport records/client via `integration::assistant`. | Technical adapter, provider-specific. |
| `outbox` | 16 | Durable outbox event model, event factory, relay state machine, Kafka/logging/failing publishers, Kafka topic settings. | none | `OutboxRelayService` | `OutboxEventRepository`; entity `OutboxEvent` | Owns `asset.processing.requested` and `asset.indexing.requested` payload records and relay status transitions. | Technical platform with product event-contract knowledge. |
| `processing` | 30 | Processing job entity/state, trigger mode config, request relay scheduler, result event parsing/application, result listener, recovery/smoke commands. | none | `ProcessingResultEventHandler`, `ProcessingRecoveryService`, request relay scheduler | `ProcessingJobRepository`; entity `ProcessingJob`; `ConsumedProcessingResultEventRepository`; entity `ConsumedProcessingResultEvent` | Kafka listener for `asset.processing.result.v1`, result envelope/payload contracts, transcript artifact validation. | Domain/application integration. |
| `search` | 31 | Search API, Elasticsearch client, transcript index documents/mapping, indexing jobs, indexing event parser/listener, explicit indexing, indexing relay/smoke. | `SearchController` | `SearchService`, `TranscriptIndexingService`, `AssetSearchIndexRequestService`, `AssetSearchIndexingExecutor`, relay scheduler | `AssetSearchIndexJobRepository`; entity `AssetSearchIndexJob` | Kafka listener for `asset.indexing.requested.v1`; Elasticsearch `RestClient` write/search path. | Domain/application plus technical adapter. |
| `storage` | 8 | Object storage abstraction, S3/MinIO adapter, object key generation and properties. | none | `S3ObjectStorageClient` component | none | Uses AWS S3 SDK `S3Client`; exposes object-storage request/result records. | Technical adapter. |
| `workspace` | 15 | Workspace model, ownership/access policy, default workspace, workspace CRUD. | `WorkspaceController` | `WorkspaceService`, `WorkspaceAccessPolicy` | `WorkspaceRepository`; entity `Workspace` | Public workspace DTOs and validation exceptions. | Domain-specific. |

Current tests mirror these roots: `asset`, `assistant`, `common/identity`, `outbox`, `processing`, `search`, `storage`, and `workspace`.

Large production files that are useful refactor signals:

| File | Lines | Observation |
|---|---:|---|
| `asset/AssetService.java` | 603 | Handles upload flow, direct FastAPI status mapping, object storage, asset listing/status/transcript/context, and ownership-mediated asset reads. |
| `search/TranscriptSearchIndexClient.java` | 473 | Combines Elasticsearch query construction, index bootstrap/mapping, bulk writes, deletes, title sync, response validation, and exception translation. |
| `common/web/ApiExceptionHandler.java` | 273 | Centralizes HTTP error mapping but imports exceptions from nearly every domain package. |
| `outbox/OutboxRelayService.java` | 266 | Owns claim/publish/finalize/failure mechanics plus typed request/indexing relay entrypoints. |
| `assistant/AssistantContextService.java` | 260 | Orchestrates search results and transcript context; currently appropriately reuses services rather than repositories. |
| `processing/result/ProcessingResultEventParser.java` | 250 | Owns processing result envelope parsing/validation. |
| `asset/AssetPersistenceService.java` | 239 | Owns asset/job/snapshot persistence but also creates processing outbox and triggers indexing auto-request. |
| `search/AssetSearchIndexingExecutor.java` | 230 | Owns indexing job lifecycle and writes derived docs, but directly loads/saves asset state and transcript snapshots. |
| `workspace/WorkspaceService.java` | 226 | Owns workspace/default-workspace behavior and uses asset repository for delete conflict guard. |
| `processing/result/ProcessingResultEventHandler.java` | 221 | Owns result idempotency and processing result application, but directly mutates asset and processing-job state. |

## Current Dependency Map

The table below records meaningful cross-package dependencies in production code. "Acceptable" means aligned with the current implementation and product flow. "Questionable" means it may be right today but should be narrowed or made explicit before architecture enforcement. "Violation candidate" means likely to fail a strict module boundary if introduced as-is.

| From | Depends on | Kind | Classification | Evidence |
|---|---|---|---|---|
| `asset` | `workspace` | Ownership and workspace resolution service/entity. | Acceptable, but should be a public workspace API. | `asset/AssetService.java:17-18`, `asset/Asset.java:3` |
| `asset` | `storage` | Object key generation, object storage write/delete, stored object metadata. | Acceptable platform dependency. | `asset/AssetService.java:12-16`, `asset/AssetDeletionService.java:4` |
| `asset` | `integration.fastapi` | Direct-upload path and transitional transcript fallback read. | Transitional/questionable; direct upload remains default but future modularity should isolate provider DTOs. | `asset/AssetService.java:3-7`, `asset/AssetService.java:331-352`, `asset/AssetService.java:569-584` |
| `asset` | `processing` | Direct use of `ProcessingJob`, repository, status, trigger mode. | Confirmed boundary-leak risk if `processing` becomes separate module. Asset upload currently creates/reads jobs directly. | `asset/AssetService.java:8-11`, `asset/AssetPersistenceService.java:8-10` |
| `asset` | `outbox` | Creates and stores processing request outbox rows. | Questionable; durable outbox mechanics are platform, but product event creation is currently inside asset persistence. | `asset/AssetPersistenceService.java:5-7`, `asset/AssetPersistenceService.java:104-120` |
| `asset` | `search` | Explicit indexing controller dependency, delete/title sync, auto-index request after snapshot replace. | Confirmed cycle risk because search also depends on asset. | `asset/AssetController.java:18`, `asset/AssetPersistenceService.java:11`, `asset/AssetDeletionService.java:3`, `asset/AssetTitleUpdateService.java:3` |
| `workspace` | `common.identity` | Current user and access policy. | Acceptable if identity is a named public API. | `workspace/WorkspaceService.java:4`, `workspace/WorkspaceAccessPolicy.java:3` |
| `workspace` | `asset` | Delete conflict guard asks `AssetWorkspaceUsageService.workspaceHasAssets`. | Improved in P3-BE2B; workspace now depends on an asset public application query instead of asset repository internals. | `workspace/WorkspaceService.java`, `asset/AssetWorkspaceUsageService.java` |
| `processing` | `asset` | Result handler delegates asset terminal state and snapshot replacement through `AssetProcessingResultApplicationService` and `AssetTranscriptRowInput`. | Improved in P3-BE2A; this is now an explicit asset public application boundary rather than direct asset persistence/entity mutation. | `processing/result/ProcessingResultEventHandler.java` |
| `processing` | `integration.fastapi` | Retrieves transcript artifact rows by processing request ID and maps them to asset input records at the module boundary. | Acceptable processing adapter dependency; FastAPI DTOs no longer flow into asset persistence. | `processing/result/ProcessingResultEventHandler.java`, `processing/result/TranscriptArtifactValidator.java` |
| `processing` | `outbox` | Recovery, smoke, request relay, listener config use outbox state and Kafka properties. | Acceptable platform dependency, but should be through named outbox relay API. | `processing/request/ProcessingRequestRelayScheduler.java:3-4`, `processing/recovery/ProcessingRecoveryService.java:3-6` |
| `search` | `asset` | Searchability gate, transcript/context reads, indexing-source reads, and asset searchability transitions through asset public contracts. | Improved in P3-BE2A; direct asset repository/entity access was removed for the affected search/indexing flows, while the search-to-asset dependency remains intentional. | `search/SearchService.java`, `search/AssetSearchIndexingExecutor.java`, `search/TranscriptIndexingService.java`, `search/TranscriptIndexDocumentMapper.java` |
| `search` | `processing` | Explicit indexing loads `ProcessingJobRepository` to validate transcript availability. | Questionable; likely should be a product transcript-read API instead of processing repository access. | `search/TranscriptIndexingService.java:11-12`, `search/TranscriptIndexingService.java:49-56` |
| `search` | `outbox` | Indexing event payload parsing, auto-request outbox creation, indexing relay/smoke. | Acceptable but needs named outbox event-contract interface. | `search/AssetSearchIndexRequestService.java:5-7`, `search/AssetIndexingEventParser.java:3-4` |
| `search` | `common.config` | Elasticsearch properties. | Acceptable technical config dependency, but config may move under platform. | `search/TranscriptSearchIndexClient.java:4` |
| `assistant` | `search` | Reuses existing search API/service. | Acceptable; this is the intended retrieval boundary. | `assistant/AssistantContextService.java:11-13` |
| `assistant` | `asset` | Reuses the asset public transcript-context/searchability read API. | Improved in P3-BE2A; assistant no longer depends on `AssetService`, `Asset`, or asset HTTP response DTOs for context assembly. | `assistant/AssistantContextService.java` |
| `outbox` | `asset`, `workspace`, `storage` | Event factory accepts entities/value records to build payloads. | Violation candidate for strict platform module; outbox platform currently contains product event construction. | `outbox/OutboxEventFactory.java:3-5`, `outbox/OutboxEventFactory.java:28-67` |
| `common.web` | all domain packages | Global exception handler imports domain exceptions and infrastructure exceptions. | Confirmed boundary-leak risk for default Modulith verification; common technical code depends inward on all modules. | `common/web/ApiExceptionHandler.java:3-28` |
| `common.identity` | `workspace` | OIDC first-login provisioning uses workspace repository/properties. | Questionable but intentional product flow; should become identity -> workspace public API. | `common/identity/TransactionalOidcUserCreationExecutor.java:3-5` |
| `assistant` | `integration.fastapi.assistant` | Calls the explicit named FastAPI assistant transport API for grounded answer generation. | Acceptable after P3-F2A.1; only the assistant-specific client and transport records are exposed, while HTTP implementation remains internal. | `assistant/AssistantAnswerService.java`, `integration/fastapi/assistant/package-info.java` |
| `integration.fastapi` | Spring `RestClient` | External HTTP adapter. | Acceptable technical adapter. | `integration/fastapi/FastApiProcessingClientImpl.java`, `integration/fastapi/assistant/internal/FastApiAssistantClientImpl.java` |
| `storage` | AWS S3 SDK | MinIO/S3 adapter. | Acceptable technical adapter. | `storage/ObjectStorageConfig.java`, `storage/S3ObjectStorageClient.java` |

## Boundary Strengths Already Present

- Controllers generally delegate to services rather than repositories: `AssetController`, `WorkspaceController`, `SearchController`, and `AssistantContextController` do not contain persistence logic.
- Workspace ownership policy is centralized through `WorkspaceService.resolveWorkspaceOrDefault`, `WorkspaceService.isOwnedByCurrentUser`, and `WorkspaceAccessPolicy`.
- Search and assistant retrieval reuse product-level services instead of reading Elasticsearch or transcript repositories from controller code.
- PostgreSQL ownership remains explicit: JPA entities/repositories own asset, workspace, processing, outbox, consumed-result, indexing-job, transcript snapshot, and user-account state.
- Kafka and Elasticsearch are accessed through adapter/service boundaries (`OutboxMessagePublisher`, `KafkaOutboxMessagePublisher`, `TranscriptSearchIndexClient`, listeners).
- Manual and automatic relays are scoped by event type, not broad arbitrary outbox scans.
- Assistant answer orchestration uses bounded context and the named FastAPI assistant contract; it still has no embedding, chat persistence, generic provider framework, or direct browser-to-FastAPI path.

## Boundary-Leak Risks

### Confirmed

- `asset`, `processing`, and `search` still form a concrete cycle:
  - `asset` creates processing jobs and calls search indexing request/sync services.
  - `processing` now applies asset state through an asset public command, but it still depends on the asset module.
  - `search` now reads canonical asset/transcript/searchability state through asset public contracts and still loads processing jobs for explicit indexing.
- `workspace` still depends on `asset` for workspace delete checks, but P3-BE2B
  moved the guard behind the public `AssetWorkspaceUsageService` query instead
  of `AssetRepository`.
- `outbox` is both platform relay state machine and product event factory; `OutboxEventFactory` imports `Asset`, `Workspace`, and `StoredObject`.
- `common/web/ApiExceptionHandler` imports exceptions from nearly every product/infrastructure package.
- Several public classes are likely internal implementation details only because there is no package visibility/module API convention yet, for example repositories, parsers, schedulers, smoke command runners, and many DTOs.

### Likely

- `AssetService` has multiple independent responsibilities: upload orchestration, direct FastAPI compatibility, object storage, asset listing/status, transcript reads/context, and fallback transcript capture.
- `TranscriptSearchIndexClient` combines adapter, query DSL construction, index mapping/bootstrap, bulk write, delete, title sync, and response validation.
- `ProcessingResultEventHandler` and `AssetSearchIndexingExecutor` still own important cross-module orchestration, but P3-BE2A moved their asset lifecycle/snapshot/searchability operations behind asset-owned application contracts.
- `common` is not neutral: it contains identity product behavior, web advice, configuration, and health.

### Needs Later Confirmation

- Whether explicit indexing should remain surfaced from `AssetController` or move behind a search-owned application API with an asset-facing facade.
- Whether `processing` should own `ProcessingJob` as a separate module or whether processing job state should be treated as part of asset lifecycle inside an `asset` module with processing as integration.
- Whether event payload records should live with product modules while `outbox` keeps only envelope/state/publisher mechanics.
- Whether Elasticsearch mapping/query code should be split from search application service before or after Spring Modulith verification.

## Candidate Module Boundaries

These candidate boundaries are review frames, not instructions to move packages in this phase.

### `workspace`

- Provided API: resolve current user's workspace/default workspace, workspace ownership check, create/list/update/delete workspace.
- Internal implementation: `Workspace`, `WorkspaceRepository`, name validation, default workspace creation.
- Required dependencies: identity current-user API.
- Dependencies to remove or narrow: P3-BE2B removed the direct `AssetRepository`
  delete guard; the remaining dependency is the intentional asset public
  workspace-usage query.
- Current package root: `workspace` is a plausible module root after the asset delete-check dependency is narrowed.
- Package-private practicality: controllers/DTOs stay public; repository/entity/service internals can later be package-private or under `internal` after tests adapt.

### `asset`

- Provided API: asset lookup with authorization, upload command, status query, transcript snapshot/context reader, asset title/delete commands, canonical transcript replacement.
- Internal implementation: `Asset`, `AssetTranscriptRowSnapshot`, repositories, snapshot sorting/replacement, object-reference metadata.
- Required dependencies: workspace public API, storage adapter, processing request API, outbox event intent API.
- Dependencies to remove or narrow: direct `ProcessingJobRepository`, FastAPI DTOs in asset persistence, direct search indexing request/sync dependencies.
- Current package root: plausible but currently too broad and cyclic.
- Package-private practicality: entity/repository classes are currently widely imported by search/processing/tests, so package-private visibility is not practical until APIs are extracted.

### `processing`

- Provided API: processing request trigger configuration, request relay scheduler, result listener/handler, result recovery.
- Internal implementation: `ProcessingJob`, consumed-result entity/repository, result envelope parser, artifact validator, listener configs.
- Required dependencies: outbox relay API, FastAPI artifact client, asset result-application API.
- Dependencies to remove or narrow: direct `AssetRepository` and direct asset status mutation.
- Current package root: plausible module root, with `request`, `result`, `listener`, `recovery`, and `smoke` as internals.
- Package-private practicality: likely good after listener/config/smoke public needs are isolated.

### `search`

- Provided API: search query, explicit indexing command, indexing request creation, indexing event handling, transcript index write/search adapter.
- Internal implementation: `AssetSearchIndexJob`, indexing job repository, fingerprinting, Elasticsearch client and document mapper.
- Required dependencies: asset visibility/snapshot API, outbox event intent API, platform Elasticsearch adapter.
- Dependencies to remove or narrow: direct `AssetRepository`, `AssetTranscriptRowSnapshotRepository`, `ProcessingJobRepository`, and direct asset status mutation.
- Current package root: plausible module root after public asset snapshot/status contracts are extracted.
- Package-private practicality: job repository/entity and ES client are good internal candidates later.

### `assistant`

- Provided API: `POST /api/assistant/context` context pack and `POST /api/assistant/answer` grounded answer.
- Internal implementation: request normalization, source/citation shaping, dedupe/bounds, Spring-owned source IDs, and final citation validation.
- Required dependencies: search public API, asset transcript-context public API, and the named `integration::assistant` FastAPI transport API.
- Dependencies to remove or narrow: remaining search/asset named interfaces should be made explicit in later phases.
- Current package root: currently the cleanest candidate module root.
- Package-private practicality: service internals can be hidden later; DTO/controller stay public API.

### `identity`

- Provided API: current product user ID, auth/session APIs, JWT-to-local-user provisioning.
- Internal implementation: `UserAccount`, repository, auth services, OIDC mapper/provisioning.
- Current location: under `common/identity`, which is not ideal for domain ownership.
- Required dependencies: workspace default-workspace provisioning public API.
- Dependencies to remove or narrow: direct `WorkspaceRepository` in transactional OIDC provisioning.
- Current package root: would likely need extraction from `common.identity` to either `identity` or a clearly named module in a later phase.

### `platform`

- Proposed contents: Kafka publisher/listener technical configuration, outbox state machine, storage adapter, FastAPI adapter, Elasticsearch adapter, neutral configuration, health.
- Current locations: `outbox`, `storage`, `integration`, `common/config`, `common/health`.
- Required dependencies: should generally be depended on by product modules, but should not depend on product entities.
- Dependencies to remove or narrow: `OutboxEventFactory` product payload construction and global API exception mapping.
- Current package root: no single root today; P3-BE1 can document these as allowed infrastructure modules or exclude some from default module verification.

### `common`

- Proposed contents: only neutral cross-cutting primitives and API error shapes.
- Current contents: identity/auth, web exception mapping, config, health.
- Recommendation: do not treat current `common` as a coherent domain module. Split or explicitly classify its subpackages in a later phase.

## Proposed Dependency Direction

Target direction for the modular monolith:

```text
controllers
-> module application services
-> module-owned repositories/entities
-> platform adapters only through narrow interfaces

assistant -> search public API + asset transcript/context public API
search -> asset visibility/snapshot public API + outbox event-intent API + Elasticsearch adapter
processing -> asset processing-result application API + FastAPI artifact adapter + outbox relay API
asset -> workspace public API + storage adapter + processing request API + outbox event-intent API
workspace -> identity current-user API
identity -> workspace default-workspace provisioning API
platform -> no product entities when practical
common -> neutral API/error primitives only
```

Concrete direction changes after P3-BE2A:

- completed in P3-BE2B: replace `workspace -> AssetRepository` with an asset public workspace-usage query;
- completed in P3-BE2A: replace `processing -> AssetRepository/Asset` with an asset application command for processing results;
- completed in P3-BE2A: replace affected `search -> AssetRepository/AssetTranscriptRowSnapshotRepository/Asset` flows with asset visibility, transcript snapshot, indexing source, and searchability command APIs;
- move product event payload construction out of platform outbox or expose it through product module event factories;
- split `common.identity` from neutral `common`;
- split `TranscriptSearchIndexClient` adapter/query/mapping responsibilities only if architecture verification proves this coupling obstructs module boundaries.

## Proposed Public API Vs Internal Ownership

Initial public APIs should be small Java service contracts or named interfaces, not new HTTP APIs:

| Module | Public API candidates | Internal candidates |
|---|---|---|
| `workspace` | workspace resolution, ownership check, default workspace provisioning | `WorkspaceRepository`, `Workspace`, validators, default ID creation |
| `asset` | authorized asset lookup, transcript snapshot/context reader, processing-result apply command, asset delete/title/upload commands | `AssetRepository`, `AssetTranscriptRowSnapshotRepository`, entities, snapshot replacement internals |
| `processing` | request relay command/scheduler API, result event handler entrypoint | parsers, consumed-event repository/entity, recovery/smoke commands, listener config |
| `search` | search query service, explicit index command, indexing-event handler, indexing request creation | job repository/entity, fingerprinting, ES document mapper/client |
| `assistant` | context pack builder and controller DTOs | validation/dedupe/source assembly helpers |
| `identity` | current product user, login/register/me, JWT provisioning facade | `UserAccountRepository`, OIDC mapper/executors |
| `platform/outbox` | append durable event, relay selected/due typed events, publisher interface | `OutboxEventRepository`, `OutboxEvent`, Kafka publisher implementation |

## Spring Modulith Adoption Assessment

1. The application root package `com.aiknowledgeworkspace.workspacecore` and its direct subpackages are syntactically compatible with Spring Modulith default module detection.
2. P3-BE1 confirmed default detection creates modules for `asset`, `assistant`, `common`, `integration`, `outbox`, `processing`, `search`, `storage`, and `workspace`.
3. A default `ApplicationModules.of(WorkspaceCoreApplication.class).verify()` is intentionally not used as a passing test today. P3-S5.B2A reduced cycle messages to zero, but reviewed non-cycle named-interface and exposure violations remain in the exact ratchet.
4. P3-BE1 chooses a focused verification baseline that documents current violations and gates unreviewed drift. It does not define allowed dependencies/named interfaces, temporarily exclude product modules, mark modules open, or reclassify technical packages.
5. Candidate first product module roots: `assistant`, `workspace`, `asset`, `processing`, `search`.
6. Candidate technical/platform roots: `outbox`, `storage`, `integration.fastapi`, `common.config`, `common.web`, `common.health`.
7. Maven review for P3-BE1 happened in `services/workspace-core/pom.xml`: Spring Modulith `1.2.5` is imported through the Spring Modulith BOM and only `spring-modulith-starter-test` is added with test scope.
8. Future strict verification target:

```java
@Test
void verifiesApplicationModules() {
    ApplicationModules.of(WorkspaceCoreApplication.class).verify();
}
```

That strict test remains a target, not a current passing test. It should be added only after the remaining non-cycle boundary leaks are removed or narrowed through intentional module APIs.

Spring Modulith alone can express and verify module dependencies once public APIs/named interfaces are clear. ArchUnit is not needed now. ArchUnit may complement Spring Modulith later for more specific rules, for example:

- controllers must not inject repositories;
- repositories/entities should not be imported outside their owning module;
- provider-specific FastAPI DTOs should not leak outside processing/asset transition code;
- `common` must not depend on product modules except through explicitly allowed error contracts.

## Staged Next-Step Plan

### P3-BE1: Minimum Architecture Verification Baseline

- Completed: add Spring Modulith test-only dependency support.
- Completed: add default-detection module-root assertion.
- Completed: record exact current `detectViolations()` output as a committed baseline ratchet.
- Completed: keep strict verification honest by not making `ApplicationModules.verify()` appear green through exclusions, open modules, or broad allow-lists.
- Completed: do not change product behavior, schema, API, events, or runtime controls.

### P3-BE2A: Asset Public Application Boundary

- Completed: add public asset read models and services for indexing-source reads,
  transcript context, searchability checks, processing-result application, and
  searchability transitions.
- Completed: migrate normal production `processing`, `search`, and `assistant`
  consumers away from selected direct asset persistence/entity internals.
- Completed: update the Modulith violation baseline after reviewing that module
  roots stayed stable and changed messages reflect the intended asset boundary.
- Still blocked: strict `ApplicationModules.verify()` because deferred edges
  remain.

### P3-BE2B: Workspace Asset-Usage Guard

- Completed: add `AssetWorkspaceUsageService.workspaceHasAssets(UUID)` as the
  minimal asset-owned public query for workspace delete checks.
- Completed: migrate `WorkspaceService.deleteWorkspace` away from direct
  `AssetRepository.countByWorkspace_Id` access while preserving default
  workspace, ownership/not-found, conflict, and successful delete behavior.
- Completed: update the Modulith baseline after reviewing that module roots and
  message counts stayed stable and the changed lines represent the intentional
  `workspace -> asset public API` dependency.
- Still blocked: strict `ApplicationModules.verify()` because deferred edges
  remain.

### P3-F2A.1: Assistant FastAPI Contract Export

- Completed: move the assistant FastAPI transport records and client into the
  named `integration::assistant` API.
- Completed: keep `FastApiAssistantClientImpl` and HTTP exception translation
  under `integration.fastapi.assistant.internal`.
- Completed: update the Modulith baseline after reviewing that the F2A
  assistant-to-non-exposed-integration messages disappeared and the count
  returned to `161`.
- Still blocked: strict `ApplicationModules.verify()` because deferred edges
  remain.

### Later P3-BE2 Steps

Potential first refactors, in order of least blast radius:

1. Split identity from neutral `common` conceptually or via named interface.
2. Move product event payload construction out of platform `outbox` or hide it behind product-facing factories.
3. Narrow `ApiExceptionHandler` coupling only after error contract ownership is agreed.
4. Decide whether `search -> ProcessingJobRepository` belongs behind a product transcript-availability API.

### Future

- Add focused ArchUnit rules only where Spring Modulith cannot express the desired constraint.
- Consider package-private/internal subpackages after public APIs are stable.
- Keep the backend a modular monolith; do not split microservices merely to satisfy package boundaries.

## Refactors Deliberately Postponed

- No package moves in P3-BE0.
- No dependency additions in P3-BE0.
- No schema, migration, API, or Kafka event changes.
- No search/indexing contract changes.
- No upload/processing trigger changes.
- No assistant LLM/provider/chat persistence work.
- No generic platform rewrite or microservice extraction.
