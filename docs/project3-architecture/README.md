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
- `docs/architecture/phase1-implemented-product-flow.md`
- `docs/api/API.md`
- `docs/data/Database.md`
- `docs/runbooks/local-dev.md`
- `docs/planning/deployable-demo-baseline.md`

Historical/reference docs:

- Most files under `docs/planning/`, except `deployable-demo-baseline.md`.
- Sprint, phase-closure, and transition notes are useful context, but not the current runtime contract.

Backend code confirms that Project2 is already a Spring Boot product backend with workspace, asset, transcript, explicit indexing, search, auth/session, PostgreSQL, Elasticsearch, and a FastAPI service boundary.

Frontend repository inspected: `/Users/nqd2005/Projects/ai-knowledge-workspace-fe`. It confirms a React/Vite product UI that calls the Spring Boot API boundary and does not call FastAPI, LLM providers, or infrastructure directly.

FastAPI repository note: the requested `/Users/nqd2005/Projects/DemoFastAPI` path was not present locally during inspection. The inspected processing reference was `/Users/nqd2005/Projects/DemoFirstBackend`, which identifies itself as the AI Knowledge Workspace processing service and uses FastAPI, Celery, PostgreSQL, Redis, ffmpeg, and Whisper.

The Viettel project/image was used only as a visual and architectural learning reference, not as a source for Project3 business labels.

## Core Ownership Rules

Spring Boot owns product state and product-facing behavior:

- public product APIs;
- JWT validation and authorization enforcement;
- workspace/tenant scope;
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

- users, organizations, workspaces, and memberships;
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

- validates JWT and workspace/tenant scope;
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
2. Frontend authenticates with Keycloak using OIDC Authorization Code + PKCE.
3. Frontend calls `/api/**` through Nginx with the access token.
4. Spring Boot validates JWTs using Keycloak issuer/JWKS.
5. Spring Boot enforces workspace/resource authorization from Product PostgreSQL state.

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
16. Spring Boot creates/publishes `index.requested` after product state is durable.
17. A Spring-owned indexing consumer writes searchable transcript rows into Elasticsearch.

Spring Boot does not stream media bytes to FastAPI in the main processing path. MinIO object keys and internal service credentials keep the media path explicit and scalable.

### 3. Search

1. Search API remains frontend -> Nginx -> Spring Boot -> Elasticsearch -> Spring Boot.
2. Spring Boot enforces authorization and workspace/tenant scope before returning search results.
3. Transcript context is read from Product PostgreSQL snapshots when product truth is required.

Elasticsearch is derived. Product PostgreSQL remains the truth for transcript snapshots and product status.

### 4. AI Assistant / LLM

1. User asks the assistant through React/Vite.
2. Frontend calls Spring Boot through Nginx.
3. Spring Boot AI Assistant API / Context Orchestrator validates JWT and workspace/tenant scope.
4. Spring Boot retrieves authorized context from Product PostgreSQL and Elasticsearch.
5. Spring Boot calls the internal FastAPI LLM Adapter / Prompt Executor.
6. FastAPI constructs/executes the prompt and calls the configured LLM Provider / Local Model Runtime.
7. The LLM response returns to FastAPI and then to Spring Boot.
8. Spring Boot returns the answer to the frontend.
9. Spring Boot optionally stores conversation/audit/history in Product PostgreSQL.

Possible providers include Ollama/local model, OpenAI/OpenRouter adapter, or other LLM providers. They are implementation choices behind the adapter, not product-state owners.

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
