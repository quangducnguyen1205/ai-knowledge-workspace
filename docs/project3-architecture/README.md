# Project3 Architecture Decision

Status: final planning draft for Project3. No implementation code has been changed.

## Decision

Project3 should use a **Spring Boot modular monolith / product core plus real platform infrastructure**.

The primary stack is:

```text
React / Vite frontend
-> Nginx reverse proxy / API boundary
-> Spring Boot Product Core
-> Product PostgreSQL, Redis, MinIO, Kafka, Elasticsearch
-> internal FastAPI + Celery + LLM adapter
-> replaceable LLM Provider / Local Model Runtime
-> Prometheus, Grafana, Loki
-> Docker Compose local platform
```

This is intentionally not a full microservice architecture. Spring Boot remains the product-facing backend and product-state owner. FastAPI is an internal AI/media/LLM execution dependency. Kafka integrates Spring Boot and FastAPI through durable cross-service events for media processing. Celery is only the FastAPI-side internal task execution layer.

Project3 now has two first-class AI Knowledge Workspace capabilities:

- asynchronous media/transcript processing;
- interactive AI assistant over workspace knowledge.

## Current-State Evidence

Backend repository inspected: `/Users/nqd2005/Projects/ai-knowledge-workspace`.

Current source-of-truth docs:

- `docs/README.md`
- `docs/architecture/00-reviewer-overview.md`
- `docs/architecture/05-end-to-end-diagram-pack.md`
- `docs/architecture/01-system-context.md`
- `docs/architecture/02-service-boundaries.md`
- `docs/architecture/backend-modularity-baseline.md`
- `docs/architecture/phase1-implemented-product-flow.md`
- `docs/api/API.md`
- `docs/data/Database.md`
- `docs/runbooks/local-dev.md`
- `docs/planning/deployable-demo-baseline.md`

Historical/reference docs:

- Most files under `docs/planning/`, except `deployable-demo-baseline.md`.
- Sprint, phase-closure, and transition notes are useful context, but not the current runtime contract.

Backend code confirms that Project2 is already a Spring Boot product backend with workspace, asset, transcript, explicit indexing, search, auth/session, PostgreSQL, Elasticsearch, and a FastAPI service boundary.

P3-BE0 `[ĐÃ XÁC MINH TỪ CODE]` records the current Spring modular-monolith
package map and dependency risks before refactor work. The baseline keeps the
implementation unchanged and identifies `workspace`, `asset`, `processing`,
`search`, `assistant`, identity/authentication, platform/infrastructure, and
common technical concerns as candidate module boundaries for later verification.

P3-BE1 `[ĐÃ XÁC MINH TỪ CODE]` adds a Spring Modulith test-only verification
baseline using default direct-package detection. The committed violation
baseline is a ratchet: it catches new accidental module leaks while the known
asset/processing/search/workspace cycles and common/outbox ownership issues are
removed gradually. Strict `ApplicationModules.verify()` is intentionally not
green yet, and no Spring Modulith runtime feature or production behavior was
added.

P3-BE2A `[ĐÃ XÁC MINH TỪ CODE]` adds the first production-code modularity
refactor: a public asset application boundary for processing-result application,
canonical transcript/indexing-source reads, transcript context, and
searchability state transitions. Processing, search, and assistant now consume
those asset contracts instead of selected direct asset persistence/entity
internals. The refactor preserves REST/event/schema/runtime behavior and keeps
the Modulith baseline as an honest ratchet; strict verification is still blocked
by deferred edges such as asset-to-processing/search cycles, outbox product-event
construction, common web exception coupling, and search's processing repository
dependency.

P3-BE2B `[ĐÃ XÁC MINH TỪ CODE]` narrows the workspace-to-asset edge for
workspace deletion: Workspace now consumes the asset-owned
`AssetWorkspaceUsageService.workspaceHasAssets` query instead of
`AssetRepository.countByWorkspace_Id`. The module-level relationship remains
intentional, but the persistence-internal leak is gone; the Modulith baseline
changed intentionally and remains a ratchet, not a strict-green verification.

Frontend repository inspected: `/Users/nqd2005/Projects/ai-knowledge-workspace-fe`. It confirms a React/Vite product UI that calls the Spring Boot API boundary and does not call FastAPI, LLM providers, or infrastructure directly.

FastAPI repository reference: `/Users/nqd2005/Projects/DemoFastAPI` is the current internal processing repository reference. P3-BE1 only corrected this stale repository note after confirming the path exists; it did not perform a new FastAPI code inspection or add a new runtime claim.

The Viettel project/image was used only as a visual and architectural learning reference, not as a source for Project3 business labels.

## Core Ownership Rules

Spring Boot owns product state and product-facing behavior:

- public product APIs;
- JWT validation and authorization enforcement;
- user/workspace scope;
- asset metadata;
- processing job records and outbox;
- transcript snapshots;
- indexing decisions;
- search API authorization and response shaping;
- AI Assistant API / Context Orchestrator;
- optional assistant conversation/audit/history persistence.

FastAPI owns internal AI execution mechanics only:

- consuming processing request events;
- enqueueing Celery media-processing tasks;
- running media/transcription processing through workers;
- publishing processing result events;
- LLM Adapter / Prompt Executor;
- prompt construction/execution against a configured model provider.

FastAPI must not own product state, public product APIs, workspace authorization, transcript snapshots, search behavior, or assistant authorization decisions.

The LLM provider is replaceable infrastructure. It may be local or external, but it is not a product-state owner.

## Product Source Of Truth

**Product PostgreSQL - system of record** is the only product source of truth.

It stores:

- users and individually owned workspaces;
- assets and asset metadata;
- processing jobs and outbox rows;
- transcript snapshots;
- assistant conversations/audit/history if retained;
- product status and audit-oriented records.

Other stores have narrower responsibilities:

- **Elasticsearch - derived search index** stores searchable transcript-row documents.
- **MinIO - S3-compatible object storage** stores raw media and optional derived artifact bytes.
- **Redis - cache / rate limit / short-lived state** stores support data only.
- **Kafka - cross-service async events** transports integration events.
- **Processing DB - internal scratch/task state, not product truth** stores FastAPI-side task state only if needed.
- **LLM Provider - external/local model dependency, not product state owner** generates assistant output but does not own workspace data or authorization.

## Assistant / LLM Boundary

The AI assistant belongs in Project3 core because the product is an AI Knowledge Workspace, not only a transcript/search demo.

Spring Boot owns the product-facing assistant API:

- validates JWT and user/workspace scope;
- retrieves authorized context from Product PostgreSQL and Elasticsearch;
- constructs a controlled internal request for FastAPI;
- calls the internal FastAPI LLM Adapter / Prompt Executor;
- returns assistant responses to the frontend;
- optionally stores assistant conversation/audit/history in Product PostgreSQL.

FastAPI owns Python-side prompt execution:

- prompt assembly helpers and model-provider adapters;
- local or external LLM invocation;
- response normalization;
- provider-specific retry/error handling.

Frontend must not call LLM providers directly. That would bypass product authorization, workspace scope, auditability, prompt policy, and provider isolation.

P3-F1 `[ĐÃ XÁC MINH TỪ CODE]` implements only the first Spring-owned retrieval boundary: `POST /api/assistant/context` returns a deterministic, bounded context pack with source citations after existing workspace/asset/searchability checks. It does not call the FastAPI LLM adapter, invoke any provider, generate an answer, create embeddings, persist chat history, or add token accounting. Later LLM orchestration must consume context through this Spring-owned policy boundary rather than bypassing product authorization.

P3-F2A `[ĐÃ XÁC MINH TỪ CODE]` adds the first real grounded answer foundation without broadening the architecture. `POST /api/assistant/answer` stays in Spring, reuses the existing assistant context flow, sends only Spring-approved bounded sources to FastAPI, and validates returned cited source IDs before responding to the browser. FastAPI exposes one internal `POST /internal/assistant/answer` adapter and one disabled-by-default Ollama path.

P3-F2B.1 `[ĐÃ SMOKE THỰC TẾ]` verifies that foundation through one controlled local Spring -> FastAPI -> native Ollama -> Spring run. The local runtime used Ollama `0.31.1` and `qwen3:1.7b`; Spring still owned authorization, bounded context selection, source identity, and final citation validation; and the browser-shaped public response returned HTTP 200 with a nonblank answer, `insufficientContext=false`, and valid citations. FastAPI remained an internal adapter and the browser did not call FastAPI or Ollama. The observed one-request latency was about 12.3 seconds and is not a benchmark, load-capacity claim, production-readiness claim, or semantic answer-quality evaluation. No streaming, history, persistence, embeddings, external provider, Kafka/outbox assistant flow, retry topic, DLQ, reindex, rebuild, or reconciliation workflow is added.

## Kafka And Celery

Kafka and Celery both support asynchronous work, but they sit at different boundaries.

Kafka is the durable cross-service event backbone:

- Spring Boot publishes `asset.processing.requested`.
- FastAPI consumes that request event.
- FastAPI publishes `transcript.ready` or `asset.processing.failed`.
- Spring Boot consumes the result event.
- Spring Boot may publish `index.requested` after product state is saved.

Celery is the internal execution queue inside the FastAPI processing boundary:

- FastAPI enqueues an internal task.
- Celery Broker stores the task request.
- Celery Worker runs ffmpeg / Whisper processing.
- Celery/FastAPI may write scratch task state to the Processing DB.

Kafka connects Spring Boot and FastAPI for media-processing lifecycle events. Celery runs processing tasks inside FastAPI. They are not interchangeable in this design.

The interactive assistant flow does not need Kafka by default because the user is waiting for a response. Spring can synchronously call the internal FastAPI LLM adapter while still enforcing all product authorization.

## Main Flows

### 1. Login / Auth

1. Browser opens the React/Vite frontend.
2. Current implementation default: Spring uses `legacy_session` mode, so existing Project 2 register/login/session APIs remain the product path.
3. Opt-in foundation: with `WORKSPACE_CORE_SECURITY_AUTHENTICATION_MODE=keycloak_jwt`, the frontend will authenticate with Keycloak using OIDC Authorization Code + PKCE and call `/api/**` with an access token.
4. Spring Boot validates JWTs using Keycloak issuer/JWKS, maps provider plus OIDC `sub` to a local `UserAccount`, and creates a default workspace on first valid JWT request.
5. Spring Boot enforces workspace/resource authorization from Product PostgreSQL state, not from Keycloak roles.

P3-C1 implements the Spring-side JWT/resource-server and local identity mapping foundation. Keycloak Docker realm/client setup is added by P3-C2A; frontend bearer-token integration remains future work.

P3-C2A adds a profile-gated local Keycloak topology. Running `docker compose --profile keycloak ...` adds `keycloak-postgres` and `keycloak` on host port `8180`; normal infrastructure commands without the profile do not start Keycloak. The tracked local realm import creates `workspace-dev` with public client `workspace-web`, PKCE `S256`, localhost-only frontend redirects/origins, and an access-token audience mapper for `workspace-core`. It intentionally contains no users, passwords, client secrets, tokens, realm/client roles, groups, or authorization policies.

P3-C2B `[ĐÃ SMOKE THỰC TẾ]` verifies the backend OIDC path with real local Keycloak runtime: Authorization Code + PKCE issued signed tokens for the public `workspace-web` client, direct grant was not used, Spring accepted the real issuer/signature/audience, first JWT use provisioned one local `UserAccount` and default workspace, repeated use resolved the same user, another JWT subject was blocked from that user's workspace, and `keycloak_jwt` mode rejected legacy login/session-only identity. `legacy_session` remains the default.

P3-C3 `[ĐÃ XÁC MINH TỪ CODE]` adds the React/Vite opt-in Keycloak bearer-token foundation. The frontend default remains `VITE_AUTHENTICATION_MODE=legacy_session`, preserving Project 2 register/login/session behavior. When explicitly set to `keycloak_jwt`, the frontend requires public local Keycloak settings for the `workspace-web` client, starts Authorization Code + PKCE, keeps the access token in memory only, sends Spring product APIs with `Authorization: Bearer <access-token>`, and bootstraps visible identity from Spring `GET /api/me`. Keycloak is still identity provider only: workspace and asset authorization remains Spring/PostgreSQL-owned, and the frontend must not infer permissions from Keycloak roles or JWT claims. Real browser Keycloak smoke, token refresh, silent SSO, global logout propagation, account management, default-mode cutover, legacy-session removal, and collaboration/membership/RBAC remain future work.

P3-C4 `[ĐÃ SMOKE THỰC TẾ]` verifies the local browser path for the opt-in frontend Keycloak foundation. Legacy session auth stayed the default and the password login/register entry remained visually available. In `keycloak_jwt` mode, the browser used Authorization Code + PKCE with the public `workspace-web` client, returned through the frontend callback, called Spring `/api/me` with bearer auth, rendered the Spring-owned product user/default workspace, and frontend logout cleared local in-memory auth state only. Browser storage inspection found no access token, ID token, or refresh token persistence; session-scoped OIDC transaction state may exist only for redirect completion. This does not claim token refresh, silent SSO, global Keycloak logout, account management, production deployment, or auth-default cutover.

### 2. Upload, Processing, And Transcript Indexing

1. User uploads media through React/Vite.
2. Frontend calls Spring Boot APIs through Nginx.
3. Spring Boot validates JWT and workspace scope.
4. Spring Boot stores asset metadata in Product PostgreSQL.
5. Spring Boot stores the media object in MinIO through its Storage Adapter.
6. Spring Boot creates a processing job and outbox event in Product PostgreSQL.
7. Spring Boot publishes `asset.processing.requested` to Kafka with `assetId`, `objectKey`, and `correlationId`.
8. FastAPI consumes `asset.processing.requested` from Kafka.
9. FastAPI enqueues an internal Celery task.
10. Celery Worker reads the media object from MinIO using internal service credentials.
11. Celery Worker runs ffmpeg / Whisper processing.
12. Celery/FastAPI stores only temporary task state in the Processing DB if needed.
13. FastAPI publishes `transcript.ready` or `asset.processing.failed` back to Kafka.
14. Spring Boot consumes the result event.
15. Spring Boot saves the transcript snapshot/result and job status in Product PostgreSQL.
16. Spring Boot may create an `asset.indexing.requested` outbox event after product state is durable.
17. A Spring-owned indexing consumer writes searchable transcript rows into Elasticsearch.

Spring Boot does not stream media bytes to FastAPI in the main processing path. MinIO object keys and internal service credentials keep the media path explicit and scalable.

Current implementation note: Phase 3I keeps the Phase 3C Kafka publisher foundation, the Phase 3D-H disabled-by-default Spring Kafka listener for `asset.processing.result.v1`, and the explicit upload processing trigger. `WORKSPACE_CORE_PROCESSING_TRIGGER_MODE=direct_upload` is the default product behavior: Spring still calls FastAPI direct upload, stores the direct FastAPI task/video IDs, does not create a Kafka request outbox row, and leaves `ProcessingJob.processingRequestEventId` null. `WORKSPACE_CORE_PROCESSING_TRIGGER_MODE=kafka_request` is an explicit transition mode: Spring does not call FastAPI direct upload, persists `Asset`, `ProcessingJob`, and one `asset.processing.requested` outbox event atomically, and stores that outbox event ID on `ProcessingJob.processingRequestEventId`. P3-D1 adds a disabled-by-default automatic request relay for this path only: with `WORKSPACE_CORE_PROCESSING_REQUEST_RELAY_ENABLED=true` and Kafka enabled, Spring can periodically relay due `asset.processing.requested` rows in bounded batches through the existing outbox state machine. P3-D2 `[ĐÃ SMOKE THỰC TẾ]` verifies the normal opt-in async path: upload in `kafka_request`, automatic Spring request relay, FastAPI consumer, Celery/Whisper processing from MinIO, FastAPI result relay, and Spring automatic result listener through to `Asset=TRANSCRIPT_READY` and `ProcessingJob=SUCCEEDED`. P3-D4 `[ĐÃ SMOKE THỰC TẾ]` verifies the fully automatic result-publication variant: the FastAPI overlay `result-relay` service ran `processing_outbox_auto_relay`, published the durable processing result without a manual one-shot relay, and Spring applied it through the automatic listener. Search/indexing stayed disabled, no manual request/result controls were invoked, and selected smoke data was cleaned while Kafka history and Docker cache were retained. The two request paths remain mutually exclusive per upload to prevent duplicate processing before cutover.

Spring validates `transcript.ready` v1 and `asset.processing.failed` v1 envelopes, records consumed-event idempotency in PostgreSQL by `eventId`, and can apply product-state transitions through either the one-shot local handler or the disabled-by-default listener. Result events correlate to the original Spring `asset.processing.requested` event ID: `payload.processingRequestId` must equal `causationEventId`, and Spring matches the job by asset ID plus `ProcessingJob.processingRequestEventId`. `ProcessingJob.fastapiTaskId` remains the transitional direct-upload/FastAPI task identifier and is not used for Kafka result correlation. For `transcript.ready`, Spring retrieves transcript artifact rows from FastAPI by `processingRequestId`, validates the complete row set, and only then replaces its product-owned transcript snapshot and marks the asset ready. The listener defaults to disabled, consumer group `workspace-processing-result-v1`, offset reset `latest`, and `MANUAL_IMMEDIATE` acknowledgement; start it before publishing result events in controlled local runs. Phase 3I adds explicit operator recovery controls for one selected durable `FAILED` result event or one selected stale `PUBLISHING` request outbox event. These commands are exact-ID scoped and disabled by default. No retry topic, DLQ, FastAPI repository change, scheduled recovery, Kafka transactions, Schema Registry, Avro, or Protobuf are implemented yet. Delivery remains at-least-once, so Spring dedupes result events by `eventId` and consumers must remain idempotent.

Phase P3-B1 adds the derived search indexing foundation. PostgreSQL-owned transcript snapshots remain canonical. `asset_search_index_jobs` tracks one asset/snapshot indexing request with a deterministic snapshot fingerprint, and `asset.indexing.requested` v1 carries bounded metadata only: asset ID, indexing job ID, and snapshot fingerprint. PostgreSQL rejects duplicate active jobs for the same asset/fingerprint, same-fingerprint explicit indexing is an idempotent no-op after a successful index, and indexing finalization rechecks the current snapshot fingerprint before marking an asset `SEARCHABLE`. Automatic request creation is opt-in and disabled by default with `WORKSPACE_CORE_SEARCH_INDEXING_AUTO_REQUEST_ENABLED=false`; explicit Project 2 indexing remains supported through the same indexing core. P3-E1 `[ĐÃ XÁC MINH TỪ CODE]` adds a separate disabled-by-default automatic relay for due `asset.indexing.requested` outbox rows only; it reuses durable outbox claim/publish/retry transitions and does not enable request creation or the indexing listener. A controlled P3-B2 local smoke verified the disabled-by-default indexing listener with Kafka and Elasticsearch: one selected indexing outbox event was relayed, the listener wrote derived documents, marked the asset `SEARCHABLE`, and search gating excluded stale Elasticsearch documents after PostgreSQL product state changed. P3-E1 itself does not claim a new Elasticsearch runtime smoke.

P3-E2 `[ĐÃ SMOKE THỰC TẾ]` verifies the full opt-in automatic path after one standard upload: `kafka_request`, Spring automatic processing request relay, FastAPI consumer/Celery/MinIO processing, FastAPI automatic result relay, Spring automatic result listener, transcript snapshot persistence, automatic indexing request creation, Spring automatic indexing request relay, Spring indexing listener, Elasticsearch derived documents, and `Asset=SEARCHABLE`. Workspace search, asset-scoped search, and transcript context returned the selected asset, and PostgreSQL product state hid the selected asset from search after a temporary `SEARCHABLE -> TRANSCRIPT_READY` update while the Elasticsearch document remained. `direct_upload` remains the default and was not exercised; no manual relay, result-file handler, recovery command, reindex, rebuild, or reconcile workflow was used.

### 3. Search

1. Search API remains frontend -> Nginx -> Spring Boot -> Elasticsearch -> Spring Boot.
2. Spring Boot enforces authorization and user/workspace scope before returning search results.
3. Transcript context is read from Product PostgreSQL snapshots when product truth is required.
4. Spring gates search by Product PostgreSQL asset state so stale Elasticsearch documents do not make non-searchable assets visible.

Elasticsearch is derived. Product PostgreSQL remains the truth for transcript snapshots and product status.

### 4. AI Assistant / LLM

1. User asks the assistant through React/Vite.
2. Frontend calls Spring Boot through Nginx.
3. Spring Boot AI Assistant API / Context Orchestrator validates JWT and user/workspace scope.
4. Spring Boot retrieves authorized context from Product PostgreSQL and Elasticsearch.
5. Spring Boot passes only the bounded source entries and Spring-issued source IDs to the internal FastAPI assistant adapter.
6. FastAPI calls the configured Ollama runtime only when the assistant adapter is explicitly enabled.
7. FastAPI returns normalized `answer`, `citedSourceIds`, and `insufficientContext`.
8. Spring validates citations against the exact supplied source IDs and returns the browser response.

This foundation intentionally has one provider path: FastAPI -> Ollama. OpenAI/OpenRouter, provider factories, model switching, streaming, conversation history, persistence, embeddings, Kafka/outbox, retries, retry topics, and DLQ handling remain out of scope.

P3-F2B.1 confirms the local runtime chain with native host Ollama `0.31.1` and `qwen3:1.7b`. Citation validity is enforced by Spring against the exact bounded source IDs supplied to FastAPI for the answer request. Diagnostic calls to `POST /api/assistant/context` are request-specific: if an external harness compares source IDs with `POST /api/assistant/answer`, it must use the exact same retrieval query text as the answer request. A short anchor query and a full answer question can legitimately retrieve different bounded source packs.

### 5. Observability

1. Spring Boot, FastAPI, workers, Nginx, Kafka, PostgreSQL, Redis, MinIO, Elasticsearch, and LLM adapter paths emit metrics/logs where practical.
2. Prometheus scrapes metrics.
3. Loki stores local logs.
4. Grafana provides dashboards and log exploration.

OpenTelemetry can be added later when distributed tracing becomes part of deployment or thesis work.

## Why Debezium / CDC Is Not Core

Project3 has one Product PostgreSQL source of truth and explicit product-owned write paths. The core architecture does not need Debezium, CDC, or a sync database.

Indexing is handled through Spring-owned product decisions after state is saved. Media processing is integrated through Kafka events. Assistant context is retrieved by Spring through authorized reads from Product PostgreSQL and Elasticsearch.

Debezium/CDC may become a good graduation-thesis extension later for experiments around CDC-driven indexing, audit streams, data pipelines, or outbox publication. It is intentionally excluded from the Project3 core diagram to keep the architecture explainable and controlled.

## Why This Fits Project3

This architecture is ambitious enough for a backend/platform portfolio because it uses popular technologies in realistic roles:

- Spring Boot for product backend engineering;
- Keycloak for OIDC/JWT;
- PostgreSQL for durable product truth;
- Redis for cache and short-lived support state;
- MinIO for object storage;
- Kafka for durable async integration;
- FastAPI + Celery for AI/media processing;
- FastAPI LLM adapter for Python-side prompt/model execution;
- replaceable LLM Provider / Local Model Runtime for assistant capability;
- Elasticsearch for search/context retrieval;
- Prometheus/Grafana/Loki for operations.

It is still controlled because Spring Boot remains the central modular monolith. The same foundation can later grow into graduation-thesis work around deployment, scaling, load testing, observability hardening, CI/CD, LLM evaluation, and productionization.

## Diagram Files

Final public architecture artifacts:

- `project3-architecture.drawio`: editable draw.io source.
- `project3-architecture.png`: exported architecture image.

The diagram uses embedded vector-style technology badges instead of downloaded logo assets, so the artifact set stays small and portable.
