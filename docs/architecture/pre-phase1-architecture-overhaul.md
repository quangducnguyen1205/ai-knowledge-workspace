# Pre-Phase-1 Architecture Overhaul

Status: architecture closure record for the Spring product core. It covers the first dependency-
inversion pass and the final structural-convergence pass. `Project3` remains the historical stable
baseline. This document does not claim manual end-to-end runtime acceptance and does not introduce
timestamp-aware transcripts.

## Decision and evidence boundary

The target is a DDD-oriented modular monolith with Ports and Adapters inside meaningful modules,
Spring Modulith verification, and event-driven integration where reliability requires it. The
following are deliberately out of scope: microservices, full CQRS, tenant abstractions, generic
repository/use-case hierarchies, duplicate domain/persistence models by default, frontend/FastAPI
changes, timestamp fields, YouTube, and DevOps expansion.

The current dependency direction is:

```text
HTTP / Kafka / scheduler / operator adapter
                  |
                  v
        input use case or stable module API
                  |
                  v
        application orchestration and policy
                  |
                  v
       application-owned output capability
                  |
                  v
 persistence / storage / search / messaging / provider adapter
                  |
                  v
 package-private Spring Data repository / SDK / protocol client
```

PostgreSQL owns product truth and Elasticsearch remains derived. No JPA entity or persistence
adapter is an intentional cross-module or HTTP contract.

## First-pass changes retained

- Removed the Spring direct-upload/status compatibility route, `direct_upload`/compatibility
  profiles, and obsolete FastAPI task/video identifiers from product state.
- Replaced application dependencies on public Spring Data repositories with application-owned
  stores and package-private repositories/adapters.
- Made asset queries side-effect free and stopped returning JPA entities from HTTP controllers.
- Moved HTTP mapping out of search and assistant application services.
- Consolidated the local-development Flyway chain into one clean V1 baseline.
- Preserved artifact retrieval inside the processing-result transaction, upload compensation,
  outbox claim/publish/finalize, and indexing begin/write/finalize.

## Final package and module convergence

The default physical convention is used only where a class exists:

```text
<module>/
  api/                                  intentional provider-owned Modulith API only
  domain/
  application/
    port/in/
    port/out/
    service/
    command|query|result|model/         only when semantically useful
  adapter/
    in/web|messaging|scheduling|operator|module/
    out/persistence|storage|search|messaging|provider/
```

Every direct module base package is empty. Stable cross-module contracts are exposed only through
these named interfaces:

| Module | Named interface | Intentional contents |
|---|---|---|
| `common` | `api` | safe HTTP error envelope helpers only |
| `identity` | `api` | `CurrentUserContext`; server-owned identity lookup |
| `identity` | `workspace-provisioning` | consumer-owned default-workspace capability |
| `assistant` | `outbound` | provider/search/transcript capabilities implemented by other modules |
| `outbox` | `api` | enqueue, relay, recovery and their value contracts |
| `processing` | `api` | request, asset and artifact cross-module contracts plus V1 request contract |
| `search` | `api` | explicit/automatic indexing and derived-state maintenance contracts |
| `search` | `asset-ports` | consumer-owned asset capabilities implemented by `asset` |
| `storage` | `api` | object storage behavior and value contracts |
| `workspace` | `api` | owner access and workspace asset-usage capability |

All other packages are Spring Modulith-internal even when a Java type remains `public` for JPA,
Jackson, Spring proxying, or focused tests.

### Module map

| Module | Inbound adapters | Input/API | Application/domain | Output ports | Outbound adapters | Transaction owner |
|---|---|---|---|---|---|---|
| `asset` | `AssetController`; processing/search/workspace/assistant module adapters | upload, command and query use cases | upload, authorized query, mutation, transcript snapshot services; `Asset` | `AssetStore`, `CanonicalTranscriptStore` plus stable module capabilities | separate asset and transcript persistence adapters | upload transaction; mutation transaction; transcript replacement service |
| `processing` | result Kafka listener; request relay scheduler; recovery/smoke operators | request/result use cases | request correlation, parser/validator, inbox/idempotency, result application and recovery | job store, inbox store, event factory; asset/artifact capabilities | separate job/inbox persistence adapters; request codec; FastAPI artifact adapter | request service; one result-application transaction retained |
| `search` | search controller; indexing Kafka listener; relay scheduler; smoke operator | query, indexing, maintenance APIs | query policy, fingerprinting, job lifecycle and execution | job store, query/write/maintenance ports, event factory, asset ports | job persistence; event codec; cohesive Elasticsearch adapter | indexing request; begin and finalize transactions |
| `assistant` | answer/context controllers | answer and context use cases | source selection, provider request, alias/citation validation and canonical resolution | provider, search and transcript-context ports | FastAPI provider plus owning-module adapters | no product write transaction |
| `workspace` | workspace controller; identity provisioning adapter | cohesive CRUD use case and access API | `WorkspaceService`, owner policy, `Workspace` | `WorkspaceStore`, asset-usage capability | workspace persistence adapter | `WorkspaceService` read/write methods |
| `outbox` | recovery scheduler and callers through API | writer, relay, failure/manual recovery | claim/publish/finalize, classification, bounded recovery | event store, publisher, failure classifier | persistence and Kafka/logging/failing publishers | claim, finalize and failure transactions are separate from broker I/O |
| `storage` | callers through stable API; web error advice | `ObjectStorageUseCase` | object-key and storage policy | SDK/protocol boundary | `S3ObjectStorageAdapter` | none; external I/O only |
| `integration` | none | implements other modules' ports | transport mapping only | technical FastAPI clients | assistant and processing provider adapters | none owned |
| `identity` | auth controller and security filters/configuration | auth use case and current-user API | credential/OIDC policy and provisioning | user store; workspace provisioning | user persistence and security adapters | auth/OIDC creation executors |
| `common` | health and generic exception advice | safe web API only | feature-neutral helpers | none | web adapters | none |

## Structural decisions

| Area | Before | Final decision | Evidence/rationale |
|---|---|---|---|
| Asset persistence | One adapter implemented asset and canonical transcript stores | **SPLIT** into `AssetPersistenceAdapter` and `CanonicalTranscriptPersistenceAdapter` | Different repositories and lifecycles; transaction atomicity is supplied by the application transaction, not adapter co-location. |
| Processing persistence | One adapter implemented job and consumed-result inbox stores | **SPLIT** into `ProcessingJobPersistenceAdapter` and `ProcessingResultInboxPersistenceAdapter` | Job lifecycle and inbox idempotency are separate responsibilities. |
| Event serialization | Application services imported adapter codecs | **INVERT** through `ProcessingRequestEventFactory` and `IndexingRequestEventFactory` | Application code now owns the capability and codecs remain messaging adapters. |
| Processing result listener | Listener called a concrete handler | **INVERT** through `ProcessingResultUseCase` | Kafka ingress depends on a stable input behavior. |
| Indexing listener | Listener depended on orchestration implementation | **INVERT** through `AssetIndexingUseCase` | Messaging adapter no longer chooses transaction implementation. |
| Artifact validation | Gateway adapter fetched and validated product policy | **MOVE POLICY INWARD** | Adapter maps HTTP rows; application validator owns product invariants, while retrieval stays in the same transaction. |
| Identity/common | Identity lived below broad `common` and generic advice mapped identity exceptions | **EXTRACT** top-level `identity`; add identity-owned advice | `common` is feature-neutral and Spring Modulith no longer reports an inward feature dependency. |
| Product-profile validator | Root bean depended on non-exported configuration classes from processing/search/outbox | **REMOVE CROSS-MODULE TYPES** | Root validator reads explicit public property keys. Obsolete `ProcessingAsyncConfiguration` and `SearchAsyncConfiguration` wrappers were deleted. |
| Elasticsearch | One technical class implements query/write/maintenance ports | **KEEP COHESIVE** | It shares one client, index/mapping policy and error translation; splitting would duplicate infrastructure without improving dependency direction. |
| Workspace | Cohesive CRUD service | **KEEP** | Owner authorization and transaction boundaries are coherent; command/query splitting would be ceremonial. |
| Outbox | Seven-dependency relay state machine | **KEEP** | Dependencies represent event store, broker, failure classification, clock/properties and transaction separation; they are one reliability state machine. |

## Naming convergence

Names describe behavior, not HTTP verbs or pattern fashion.

| Old symbol | Final symbol | Actual behavior | Decision reason |
|---|---|---|---|
| `AssistantAnswerCommand` | `AssistantAnswerQuery` | side-effect-free provider-backed answer request | POST does not make it a state-changing command |
| `AssistantAnswerCommandUseCase` | `AssistantAnswerUseCase` | answer behavior with no product mutation | remove false CQS promise |
| `AssistantAnswerService` | `AssistantAnswerApplicationService` | source/provider/citation orchestration | implementation role is explicit |
| `AssistantContextService` | `AssistantContextApplicationService` | context retrieval policy | distinguish input use case from implementation |
| `KafkaProcessingRequestCommand` | `ProcessingRequestCommand` | application request intent | Kafka is an adapter detail |
| `ProcessingRequestApplication` | `ProcessingRequestUseCase` | stable processing request API | use-case suffix matches role |
| `ProcessingResultEventHandler` | `ProcessingResultApplicationService` | parses and applies result behavior | listener is the message handler; service owns application orchestration |
| `AssetSearchMaintenance` | `AssetSearchMaintenanceUseCase` | stable derived-index maintenance behavior | explicit inbound module API |
| `ExplicitIndexingApplication` | `ExplicitIndexingUseCase` | explicit indexing behavior | contract rather than implementation |
| `IndexingRequestApplication` | `IndexingRequestUseCase` | durable indexing intent | contract rather than implementation |
| `AssetSearchMaintenanceService` | `AssetSearchMaintenanceApplicationService` | API-to-output-port orchestration | implementation role is explicit |
| `SearchService` | `SearchApplicationService` | authorization-aware search orchestration | avoids generic service ambiguity |
| `TranscriptSearchIndexClient` | `ElasticsearchTranscriptAdapter` | implements search query/write/maintenance ports | it is not merely a protocol client |
| `ObjectStorageApplication` | `ObjectStorageUseCase` | stable storage behavior | contract role is explicit |
| `S3ObjectStorageClient` | `S3ObjectStorageAdapter` | maps application storage behavior to SDK | adapter, not raw client |
| `WorkspaceAccessApplication` | `WorkspaceAccessUseCase` | stable owner access behavior | contract role is explicit |
| `AuthService` | `AuthApplicationService` | credential registration/login/current-user orchestration | separates web/session mapping from application policy |

`WorkspaceService`, `OutboxRelayService`, technical `FastApi*Client` interfaces and accurate policy
names are intentionally unchanged. Broad repository-wide suffix churn was rejected.

## Forensic over-engineering inventory

### Interfaces: complete production inventory

There are **62 interface declarations** including eight package-private Spring Data declarations,
one sealed sum type, nested technical seams and functional/polymorphic models. Every remaining
interface has a current production caller. The two dead configuration wrappers were classes, not
interfaces, and were deleted.

| Group | Symbols (complete within group) | Implementations / callers | Boundary and decision |
|---|---|---|---|
| Raw JPA repositories (8) | `AssetJpaRepository`, `CanonicalTranscriptJpaRepository`, `UserAccountJpaRepository`, `OutboxEventJpaRepository`, `ProcessingJobJpaRepository`, `ProcessingResultEventJpaRepository`, `AssetSearchIndexJobJpaRepository`, `WorkspaceJpaRepository` | one Spring proxy each; called only by owning persistence adapter | Framework boundary; package-private; **KEEP** |
| Asset input (3) | `AssetUploadUseCase`, `AssetCommandUseCase`, `AssetQueryUseCase` | one application implementation each; HTTP caller | Stable inbound behavior; **KEEP** |
| Assistant input (2) | `AssistantAnswerUseCase`, `AssistantContextQueryUseCase` | one implementation each; HTTP caller | Separates web from application; **KEEP** |
| Identity input/API (2) | `AuthUseCase`, `CurrentUserContext` | one implementation each; controller/product modules | Auth and server-owned identity boundary; **KEEP** |
| Outbox input/API (5) | `OutboxWriter`, `OutboxRelay`, `OutboxFailureRecovery`, `OutboxManualRecovery`, sealed `RelaySelection` | one service per behavior; multiple module/scheduler/operator callers; two permitted selection variants | Reliability/module API and explicit sum type; **KEEP** |
| Processing input/API (4) | `ProcessingRequestUseCase`, `ProcessingResultUseCase`, `ProcessingResultAssetPort`, `TranscriptArtifactGateway` | one implementation each across processing/asset/integration | Message ingress and cross-module/external boundaries; **KEEP** |
| Search input/API (5) | `SearchQueryUseCase`, `AssetIndexingUseCase`, `ExplicitIndexingUseCase`, `IndexingRequestUseCase`, `AssetSearchMaintenanceUseCase` | one implementation each; web/listener/asset callers | Query, command and derived-state ownership; **KEEP** |
| Storage/workspace input/API (4) | `ObjectStorageUseCase`, `WorkspaceUseCase`, `WorkspaceAccessUseCase`, `WorkspaceAssetUsagePort` | one implementation each; multiple module/web callers | External storage and owner/module boundaries; **KEEP** |
| Persistence/output stores (8) | `AssetStore`, `CanonicalTranscriptStore`, `UserAccountStore`, `OutboxEventStore`, `ProcessingJobStore`, `ProcessingResultEventStore`, `SearchIndexJobStore`, `WorkspaceStore` | one adapter each; application callers | Hides Spring Data and persistence queries; **KEEP** |
| Assistant output (3) | `AssistantAnswerProviderPort`, `AssistantSearchPort`, `AssistantTranscriptContextPort` | one provider/owning-module adapter each | Provider and cross-module capability; **KEEP** |
| Outbox output (3) | `OutboxMessagePublisher`, `OutboxPublicationFailureClassifier`, nested `KafkaSender` | multiple publisher adapters / one classifier / one Kafka technical implementation | Broker, failure and deterministic test seams; **KEEP** |
| Processing output (3) | `ProcessingRequestEventFactory`, `ProcessingJobStore`, `ProcessingResultEventStore` | codec or persistence adapters | The two stores are counted above; factory protects wire serialization; **KEEP** |
| Search output (7) | `TranscriptSearchQueryPort`, `TranscriptSearchMaintenancePort`, `IndexingAssetPort`, `SearchAssetQueryPort`, `SearchIndexJobStore`, `TranscriptIndexWriter`, `IndexingRequestEventFactory` | Elasticsearch, asset, persistence and codec adapters | External/derived state and consumer-owned module capabilities; stores counted above; **KEEP** |
| Identity workspace/output (3) | `DefaultWorkspaceProvisioner`, `OidcUserCreationExecutor`, `DefaultWorkspaceCreationExecutor` | one adapter/executor each | Cross-module provisioning and transaction-proxy seams; **KEEP** |
| Technical provider clients (2) | `FastApiAssistantClient`, `FastApiProcessingClient` | one HTTP implementation each | Protocol seam used by provider adapters/tests; **KEEP** |
| Boundary-neutral polymorphism (3) | `AssetUploadContent`, `ProcessingResultPayload`, `TranscriptFingerprintRow` | production stream lambda; two result payload records; two fingerprint row records | Avoids HTTP/provider coupling or unsafe branching; **KEEP** |

Repeated store names in the input/output grouping above are intentional cross-classification; the
unique declaration total is 62. No interface remains solely to mirror one method without a module,
I/O, transaction, reliability, or polymorphism boundary.

### Application services and dependency counts

There are **30 `@Service` classes**. Counts below are constructor dependencies, not a quality score.

| Module | Services (`dependency count`) | Decision |
|---|---|---|
| asset | `AssetCommandApplicationService(4)`, `AssetMutationTransaction(3)`, `AssetQueryApplicationService(4)`, `AssetSearchabilityService(1)`, `AssetTranscriptQueryService(3)`, `AssetTranscriptSnapshotService(3)`, `AssetUploadTransaction(2)`, `AssetWorkspaceUsageService(1)`, `UploadAssetApplicationService(4)` | **KEEP**; responsibilities are command/query/upload/transcript/transaction seams, not one god service |
| assistant | `AssistantAnswerApplicationService(2)`, `AssistantContextApplicationService(2)` | **KEEP**; different answer-provider and retrieval-pack policies |
| identity | `AuthApplicationService(2)`, `OidcUserProvisioningService(2)`; inbound security adapter `CurrentUserService(4)` | **KEEP**; credential, OIDC and request identity concerns remain distinct |
| outbox | `OutboxManualRecoveryService(2)`, `OutboxRecoveryService(4)`, `OutboxRelayService(7)` | **KEEP**; distinct operator, bounded recovery and broker state-machine behavior |
| processing | `ApplyProcessingResultApplicationService(5)`, `ProcessingRecoveryService(2)`, `ProcessingRequestApplicationService(3)`, `ProcessingResultApplicationService(3)` | **KEEP**; transaction, recovery, request and ingress roles are separate |
| search | `AssetIndexingApplicationService(3)`, `AssetSearchIndexRequestService(5)`, `AssetSearchMaintenanceApplicationService(1)`, `ExecuteIndexJobApplicationService(3)`, `IndexingAttemptTransactionService(4)`, `SearchApplicationService(3)`, `TranscriptIndexingService(4)` | **KEEP**; request, execute, transaction, query and maintenance seams preserve I/O ordering |
| workspace | `WorkspaceAccessPolicy(1)`, `WorkspaceService(6)` | **KEEP**; cohesive owner-aware CRUD and reusable policy |

The one-dependency search-maintenance implementation is a deliberate module API boundary hiding
the Elasticsearch adapter. The dead `ProcessingAsyncConfiguration` and `SearchAsyncConfiguration`
pass-through components were **DELETE**. The unused `WorkspaceQueryApplication` and
`ProcessingJobUpdateCommand`/`updateJob` path were also **DELETE**.

### Catch-block inventory

There are **77 production catch blocks**. The following table accounts for every block by file and
line set; line numbers are for this closure tree.

| Classification | Files and catch lines | Count | Decision |
|---|---|---:|---|
| `REQUIRED_TRANSLATION` | `S3ObjectStorageAdapter:64,77`; `WorkspaceService:229`; `TranscriptIndexingService:43`; `IndexingAttemptTransactionService:139`; `SearchApplicationService:99`; `AssetIndexingEventParser:81,90,115`; `TranscriptSnapshotFingerprintService:30`; `ApplyProcessingResultApplicationService:123,131`; `ProcessingResultEventParser:89,171,221,241`; `IndexingRequestedEventCodec:55`; `UploadAssetApplicationService:86`; `SupportedUploadMediaPolicy:65`; `ElasticsearchTranscriptAdapter:196,233,238,247,263,268,277,417,548,553,559`; `KafkaOutboxMessagePublisher:57,60,89,107`; `SearchAssetPortAdapter:50,59,68,78`; `ProcessingResultAssetPortAdapter:34,43`; `ProcessingSmokeCommandRunner:113`; `TranscriptArtifactGatewayAdapter:28`; `FastApiProcessingClientImpl:37,39,44`; `ProcessingRequestedEventCodec:74`; `FastApiProperties:78`; `AssistantAnswerApplicationService:79,141`; `FastApiAssistantClientImpl:67,69,74`; `CurrentUserService:137`; `UserAccountPersistenceAdapter:44,53`; `OidcUserProvisioningService:68`; `AuthApplicationService:55,85` | 58 | **KEEP**; converts protocol/framework/owner errors into stable capability or product meanings |
| `REQUIRED_COMPENSATION` | `TranscriptIndexingService:62,65`; `IndexingAttemptTransactionService:70`; `ExecuteIndexJobApplicationService:49,87,102,133`; `ApplyProcessingResultApplicationService:74`; `UploadAssetApplicationService:63`; `OidcUserProvisioningService:47` | 10 | **KEEP**; preserves derived-state rollback, durable diagnostics, upload cleanup trigger, result failure state or concurrent OIDC recovery |
| `REQUIRED_FAILURE_CLASSIFICATION` | `OutboxRelayService:147,164`; `AssetIndexingKafkaListener:41,50`; `ProcessingResultKafkaListener:41,50` | 6 | **KEEP**; distinguishes ineligible/durable rejection from retryable publication/listener failure and controls acknowledgment |
| `REQUIRED_CLEANUP` | `KafkaOutboxMessagePublisher:70`; `UploadAssetApplicationService:94` | 2 | **KEEP**; restores interrupt/cleanup contract or records best-effort compensation failure |
| `SILENT_SWALLOW` (intentional scheduler isolation) | `OutboxRecoveryScheduler:33` | 1 | **KEEP WITH LOG**; one failed reconciliation must not terminate future scheduled runs; warning emits only safe category |

No remaining catch is `DUPLICATE_LOG_AND_RETHROW`, `CATCH_AND_WRAP_WITHOUT_VALUE`, or
`OBSOLETE_AFTER_RESPONSIBILITY_SPLIT`. Removed compatibility catches disappeared with their owning
direct-upload/status orchestration; errors now terminate at normal Kafka/outbox, provider, or web
adapter boundaries.

### Custom exceptions

There are **40 custom exception files**:

- Asset (8): `AssetListRequestException`, `AssetNotFoundException`, `InvalidAssetTitleException`,
  `InvalidTranscriptContextWindowException`, `InvalidUploadRequestException`,
  `ProcessingJobNotFoundException`, `TranscriptRowNotFoundException`, `TranscriptUnavailableException`.
- Assistant (2): `AssistantProviderUnavailableException`, `InvalidAssistantContextRequestException`.
- Identity (8): `AuthModeUnavailableException`, `AuthenticationRequiredException`,
  `EmailAlreadyRegisteredException`, `InvalidAuthRequestException`, `InvalidCredentialsException`,
  `InvalidCurrentUserIdException`, `InvalidJwtIdentityException`, `UserAccountConflictException`.
- FastAPI integration (3): `FastApiConnectivityException`, `FastApiIntegrationException`,
  `InvalidFastApiResponseException`.
- Outbox (2): `OutboxPublishException`, `PermanentOutboxPublishException`.
- Processing (4): `ProcessingAssetUnavailableException`, `TranscriptArtifactAccessException`,
  `ProcessingResultEventApplyException`, `ProcessingResultEventRejectedException`.
- Search (8): `InvalidSearchRequestException`, `SearchAssetNotFoundException`,
  `SearchProcessingJobNotFoundException`, `SearchTranscriptUnavailableException`,
  `SearchIndexConnectivityException`, `SearchIndexOperationException`,
  `SearchAssetUnavailableException`, `AssetIndexingEventRejectedException`.
- Storage/workspace (5): `ObjectStorageException`, `DefaultWorkspaceConflictException`,
  `InvalidWorkspaceNameException`, `WorkspaceDeleteConflictException`, `WorkspaceNotFoundException`.

All are **KEEP**: web-mapped product errors carry distinct stable status/code behavior; adapter
exceptions distinguish connectivity/permanent/retry or translate module ownership; result/event
rejections control Kafka acknowledgment. `UserAccountConflictException` is deliberately an output-
port error so the application does not import Spring's `DataIntegrityViolationException`.

### DTO/model chains

| Flow | Semantic chain | Decision |
|---|---|---|
| upload | `multipart + web request` -> `AssetUploadCommand/AssetUploadContent` -> `Asset` + `ProcessingRequestCommand` -> JPA-owned models -> V1 outbox payload -> `AssetUploadResponse` | Boundary duplication is required; HTTP and stream types do not enter application policy |
| processing result | Kafka JSON -> parser envelope/payload -> validated transcript rows -> canonical snapshot persistence -> indexing request rows/payload | Product validator owns invariants; provider wire row is mapped once and does not leak inward |
| search | HTTP parameters -> `SearchQuery` -> `SearchPage/SearchHit` -> `SearchResponse/SearchResultResponse` | Application results differ from Elasticsearch documents and HTTP response; **KEEP** |
| assistant | HTTP request -> `AssistantAnswerQuery`/context query -> provider request/response -> validated result/citations -> HTTP response | Provider aliases and canonical citations have different trust boundaries; **KEEP** |
| auth | HTTP register/login records -> application commands -> `AuthenticatedUser` -> HTTP response/session | Session and HTTP DTOs remain in controller; **KEEP** |

No DTO was removed solely because fields match. Reuse is allowed within one semantic layer only.

### Configuration and compatibility inventory

There are **16 live `@ConfigurationProperties` classes**:

| Owner | Properties and current caller |
|---|---|
| identity | `CurrentUserProperties` (request identity/session adapter), `WorkspaceSecurityProperties` (auth mode/security configuration) |
| integration | `FastApiProperties` (HTTP client/provider configuration) |
| outbox | `WorkspaceKafkaProperties` (publisher/listeners), `OutboxRecoveryProperties` (recovery), `OutboxRelayProperties` (relay) |
| processing | `ProcessingRecoveryProperties` (operator), `ProcessingSmokeProperties` (smoke operator), `ProcessingRequestRelayProperties` (scheduler) |
| search | `SearchSmokeProperties` (operator), `IndexingRequestRelayProperties` (scheduler), `ElasticsearchProperties` (adapter), `SearchIndexingProperties` (indexing request policy) |
| storage/workspace | `ObjectStorageProperties` (S3 adapter), `WorkspaceProperties` (workspace policy) |

The `project3` bootstrap validator reads seven explicit keys without importing module-internal
property classes. Searches of production/configuration contain no direct-upload profile/flag,
FastAPI task/video field, `run-compatibility`, or `run-standalone`. The migration test deliberately
mentions obsolete columns to prevent their return.

### Visibility inventory

There are **288 top-level public production declarations**. Classification:

- **54 intentional Spring Modulith API declarations** in the ten named-interface packages above.
- **2 bootstrap/framework entries**: `WorkspaceCoreApplication` and
  `CoherentAsyncProductProfileValidator`.
- **232 Java-public but Modulith-internal declarations** under domain/application/adapter packages.
  They include JPA/Jackson models, configuration properties, web controllers/DTOs, Spring beans and
  application values. They are not module APIs; all direct module bases are empty.
- **8 raw Spring Data declarations are package-private**, as are persistence adapters and many
  listener/configuration implementations.

Making all 232 internal declarations package-private would add package-alignment test churn without
changing the enforced module surface. That modifier-only campaign is **REJECT**. Accidental module
exposure is prevented by physical package placement, named interfaces and strict Modulith checks.

### Architecture-rule inventory

| Rule | Classification | Decision |
|---|---|---|
| detected module roots + strict `ApplicationModules.verify()` | zero cycles and intentional cross-module APIs | **KEEP** |
| application cannot depend on adapter/infrastructure | inward dependency invariant | **KEEP/GENERALIZE** |
| domain cannot depend on application/adapter | domain direction invariant | **ADD** |
| application cannot depend on Spring Data/web/HTTP | framework boundary invariant | **KEEP** |
| inbound adapters cannot access Spring repositories | input boundary invariant | **GENERALIZE** to controllers/listeners/schedulers/operators |
| message listeners cannot depend on concrete `@Service` | stable message input boundary | **ADD** |
| controllers cannot depend on JPA entities | HTTP contract invariant | **KEEP** |
| raw repositories package-private | persistence encapsulation | **KEEP** |
| repositories/entities cannot cross modules | data ownership invariant | **KEEP** |
| common/outbox feature neutral | shared/reliability ownership | **GENERALIZE** with exact root packages |
| FastAPI provider types remain integration-owned | transport isolation | **GENERALIZE** for new package |
| non-search code cannot depend on search-engine adapter | derived-state ownership | **GENERALIZE** for new package |
| indexing transaction cannot call external index writer | transaction topology | **KEEP** |
| direct module bases are empty | accidental API surface | **ADD** without exact file-tree freeze |
| obsolete compatibility/facade types absent | prevents deleted paths returning | **UPDATE** to current symbol set |

These rules complement Spring Modulith. They do not assert the complete repository tree or demand
one implementation class per interface.

## Transaction map and retained debt

| Flow | Sequence | Failure semantics |
|---|---|---|
| Asset upload | validate -> object storage outside DB -> one DB transaction writes asset + job + request outbox | DB failure triggers best-effort object deletion; cleanup failure is logged; original failure propagates |
| Asset title | authorize/load -> update derived title when searchable -> DB mutation transaction | search failure prevents product success and DB title change |
| Asset delete | authorize/load -> Elasticsearch cleanup -> object deletion -> DB delete transaction | any required cleanup failure prevents reported success and DB deletion; partial external cleanup remains retry-by-client debt |
| Processing result | listener -> parse -> one DB transaction -> artifact HTTP -> validate -> canonical replacement + asset/job + inbox | known failures become durable bounded failure; unexpected failure rolls back for redelivery; external retrieval remains intentionally inside transaction |
| Index request | one DB transaction writes job + indexing outbox | active fingerprint uniqueness suppresses duplicates |
| Index execute | begin transaction -> Elasticsearch write outside DB -> finalize transaction | stale fingerprint/finalize rejection prevents incorrect searchable state; diagnostic failures do not hide primary write failure |
| Outbox relay | claim transaction -> Kafka outside DB -> mark-published or failure transaction | at-least-once semantics, bounded retry/recovery and explicit operator controls retained |
| Workspace/auth | method-level DB transaction with server-owned user/owner identity | owner mismatch maps to not-found; DB uniqueness becomes stable conflict |

Retained debt is narrow and explicit: processing artifact HTTP remains inside its result transaction;
asset deletion has no durable multi-resource deletion state machine; Elasticsearch adapter is large
but cohesive. None is changed in this structural pass.

## Test-suite regression review

The original `origin/main` run executed **504 tests** with zero failures/errors. The first-pass tree
executed **365 tests**. The final source currently declares **372 `@Test` methods**; the authoritative
final Surefire count is recorded in the validation section after the clean full run.

| Removed or merged original test | Original invariant | Surviving/reworked protection | Coverage strength | Decision |
|---|---|---|---|---|
| `AssetApplicationServicesTest` | asset query/mutation behavior | focused query, command, transcript and transaction tests | stronger responsibility-local assertions | **MERGE** |
| `AssetDeletionServiceTest` + `AssetTitleUpdateServiceTest` | ordering and title policy | `AssetCommandApplicationServiceTest`, including search/storage partial failures | equal/stronger | **MERGE + RESTORE variants** |
| `AssetPersistenceServiceTest` | asset/transcript persistence | adapter/repository tests plus `CanonicalTranscriptStoreTest` | stronger boundary/atomic replacement coverage | **REWRITE** |
| original `UploadAssetApplicationServiceTest` path | upload compensation | focused package-converged test of storage/DB failure ordering | equal | **MOVE** |
| `AuthServiceTest` | register/login policy | `AuthApplicationServiceTest` plus controller session tests | stronger layer separation | **REWRITE** |
| `DirectProcessingCompatibilityGatewayAdapterTest` | removed direct-upload behavior | no replacement; normal request/result contract tests | obsolete invariant | **DELETE** |
| `OutboxPersistenceServiceTest` | pass-through wrapper | `OutboxEventRepositoryTest` and adapter-backed relay/recovery tests | stronger behavior at real seam | **DELETE WRAPPER TEST** |
| `OutboxRecoveryMigrationTest` | historical migration chain | `CleanBaselineMigrationTest` plus recovery persistence/service tests | correct for clean V1 policy | **REPLACE** |
| `OutboxRelayServiceTest` | claim/publish/retry/type/filter state machine | expanded `KafkaOutboxRelayServiceTest`: success, retry, terminal failure, future skip, type/batch, candidate isolation, explicit mismatch | restored essential state-machine coverage | **REWRITE/RESTORE** |
| `DirectUploadDeprecationReporterTest` + `ProcessingPropertiesTest` | compatibility warning/flag | architecture absence rule + clean migration/config search | obsolete product behavior | **DELETE** |
| `WorkspaceQueryApplicationAdapterTest` | pass-through adapter | `WorkspaceServiceTest`, controller and owner policy tests | equal without wrapper ceremony | **DELETE** |

High-risk surviving suites cover outbox failure classification/recovery, processing inbox duplicate
terminal results, canonical atomic replacement, indexing begin/write/finalize and stale fingerprint,
owner authorization, HTTP error/correlation shape, search scope/relevance, assistant source/citation
validation, Kafka V1 payload compatibility, migration constraints and strict module verification.

## Automated validation and manual boundary

Final closure validation uses:

```bash
mvn -q -f services/workspace-core/pom.xml test
docker compose --env-file .env -f infra/docker-compose.dev.yml config
git diff --check
```

Targeted architecture, bootstrap-profile, asset-deletion and outbox-relay tests are run before the
full suite. `CleanBaselineMigrationTest` supplies an empty in-memory PostgreSQL-compatible Flyway/JPA
validation. No persistent database or Docker volume is reset by this task.

Final automated result:

- targeted architecture, profile, clean migration, persistence and transaction group: **passed**;
- canonical full Maven command: **84 suites / 372 tests / 0 failures / 0 errors / 0 skipped**;
- clean V1 Flyway migration: **passed** against empty in-memory H2 in PostgreSQL mode, including JPA
  mapping/constraint checks and obsolete-column rejection;
- Compose parse: **passed**; project name resolved to `infra` and exactly the four product volume
  keys below were present;
- `git diff --check`: **passed**.

This state is `AUTOMATED CODE/TEST READY`. Manual upload -> processing -> transcript -> indexing ->
search -> assistant -> deletion acceptance remains `MANUAL RUNTIME ACCEPTANCE PENDING`.

## Manual project-scoped reset and acceptance checklist

Compose project directory `infra` resolves these four product volumes:

- `infra_workspace_core_postgres_data`
- `infra_workspace_core_elasticsearch_data`
- `infra_workspace_core_minio_data`
- `infra_workspace_core_kafka_data`

Do **not** remove `infra_workspace_core_keycloak_postgres_data`. Before deleting anything, the user
must stop the project and resolve exact names:

```bash
docker compose --env-file .env -f infra/docker-compose.dev.yml down
docker volume ls --format '{{.Name}}' \
  | grep -E '^infra_workspace_core_(postgres|elasticsearch|minio|kafka)_data$'
```

After confirming exactly four matches, remove those four explicit volumes (never use prune):

```bash
docker volume rm \
  infra_workspace_core_postgres_data \
  infra_workspace_core_elasticsearch_data \
  infra_workspace_core_minio_data \
  infra_workspace_core_kafka_data
```

Then recreate services and validate, recording the first failed boundary rather than masking it:

1. Start PostgreSQL, Elasticsearch, MinIO and Kafka with their helper services.
2. Start FastAPI and Spring with the normal Project3 profile; verify health and one Flyway V1.
3. Authenticate/register and obtain the server-owned default workspace.
4. Upload one supported media asset; verify object, asset, processing job and request outbox.
5. Verify Kafka request -> FastAPI processing -> result event -> Spring inbox.
6. Verify canonical transcript replacement and terminal asset/job state.
7. Verify indexing outbox -> Elasticsearch -> searchable state.
8. Verify owner-scoped search and assistant answer/citation resolution.
9. Delete the asset; verify Elasticsearch rows and MinIO object are gone before product deletion
   reports success.
10. Classify failure as configuration, authentication/authorization, storage, Kafka/outbox,
    FastAPI/artifact, inbox/idempotency, PostgreSQL transaction, Elasticsearch/finalize, provider,
    or deletion cleanup. Preserve logs/correlation/event IDs for the failed boundary.

## Rollback and Phase-1 gate

Code rollback uses Git history/reflog or the recorded external safety bundle. Runtime rollback after
the clean V1 decision means code/history rollback followed by project-scoped data recreation; old
local databases are not a supported migration artifact. The immutable `project3-submission-v1` tag
must not move.

The architecture gate is closed only when strict Modulith/ArchUnit and the full suite pass with the
validated tree exactly reconstructed above original `origin/main`. Manual runtime acceptance is a
separate gate and must not be inferred from automated tests.
