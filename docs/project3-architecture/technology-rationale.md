# Project3 Technology Rationale

This document explains why each primary technology belongs in Project3, what role it plays, and what should not be overused.

## Selected Primary Stack

| Layer | Primary choice | Role in Project3 |
|---|---|---|
| Frontend | React, Vite, TypeScript, TanStack Query | Product UI for auth, workspaces, upload, transcript review, indexing, search, and assistant chat. |
| API boundary | Nginx | Local reverse proxy and browser-facing `/api/**` boundary. |
| Product backend | Spring Boot modular monolith | Product APIs, authorization, product state, orchestration, indexing, search contract, and assistant API. |
| Identity | Keycloak | OIDC login, JWT issuer, issuer/JWKS integration. |
| Source of truth | Product PostgreSQL | Durable product state: users, individually owned workspaces, assets, jobs, outbox, transcript snapshots, assistant history/audit if retained. |
| Cache / ephemeral state | Redis | Cache, rate limits, idempotency keys, and short-lived support state. |
| Object storage | MinIO | S3-compatible storage for raw media and optional derived artifact bytes. |
| Cross-service async | Kafka | Durable integration events between Spring Boot and FastAPI/indexing flow. |
| Internal processing | FastAPI + Celery | FastAPI consumes/publishes media events; Celery executes ffmpeg/Whisper tasks. |
| Internal LLM execution | FastAPI LLM Adapter / Prompt Executor | Python-side prompt construction, provider calls, and response normalization. |
| LLM dependency | LLM Provider / Local Model Runtime | Replaceable external/local model: Ollama/local model, OpenAI/OpenRouter adapter, or other providers. |
| Processing scratch store | Processing DB | Internal FastAPI task/scratch state only, not product truth. |
| Search | Elasticsearch | Derived transcript-row search index and assistant context retrieval source. |
| Observability | Prometheus, Grafana, Loki | Metrics, dashboards, and log exploration. |
| Local platform | Docker Compose | Reproducible local platform for a student/portfolio project. |

## Architecture Shape

Project3 should be a production-oriented modular monolith, not a full microservice rewrite.

Spring Boot remains the product core because it owns the domain model, product APIs, authorization, product state, indexing decisions, assistant API, and tests. FastAPI remains a focused internal processing/LLM execution service because media processing and model-provider integration have different runtime needs from the Java product backend. Infrastructure components support specific responsibilities; they do not become new product owners.

This gives the project serious platform depth without making the system hard to explain or operate on a student laptop.

## Frontend: React / Vite

React/Vite is already present and fits the product.

Use it for:

- authenticated product shell;
- workspace selection;
- asset upload;
- transcript review;
- explicit indexing;
- search and transcript-context UX;
- assistant chat UI.

Do not let the frontend call FastAPI, LLM providers, PostgreSQL, Redis, MinIO, Kafka, Elasticsearch, Prometheus, Grafana, or Loki directly. The frontend should call the browser-facing API boundary and participate in the public OIDC login flow only.

## API Boundary: Nginx

Nginx is enough for Project3's local API boundary.

Use it for:

- one browser-facing local entry point;
- static frontend serving or proxying;
- routing `/api/**` to Spring Boot;
- optional local routing for Keycloak paths.

Do not put business rules, authorization decisions, assistant orchestration, or workflow orchestration in Nginx.

## Product Backend: Spring Boot Modular Monolith

Spring Boot is the center of the architecture.

Use Spring Boot for:

- public product APIs;
- JWT validation with Keycloak issuer/JWKS;
- resource ownership and user/workspace scope;
- asset lifecycle and processing-job state;
- metadata persistence;
- MinIO object-storage integration;
- Kafka outbox publication and Spring-owned consumers;
- transcript snapshot persistence;
- indexing decisions;
- search response shaping;
- AI Assistant API / Context Orchestrator;
- assistant conversation/audit/history persistence if retained;
- product tests.

Project3 should improve the backend with clearer module boundaries, Flyway migrations, Spring Security Resource Server, transactional outbox, MinIO adapter, selective Redis support, Kafka integration, assistant orchestration, and observability instrumentation.

Do not split Spring Boot into many services just to look modern. The stronger design is one well-structured product core with real platform infrastructure around it.

## AI Assistant API / Context Orchestrator

The assistant is part of Project3 core because AI Knowledge Workspace should demonstrate interactive AI over workspace knowledge, not only background transcription and search.

Use Spring Boot for:

- product-facing assistant endpoints;
- JWT validation and user/workspace authorization;
- deciding which assets/transcripts/search results can be used as context;
- retrieving context from Product PostgreSQL and Elasticsearch;
- calling the internal FastAPI LLM Adapter / Prompt Executor;
- shaping the final response to the frontend;
- storing conversation/audit/history in Product PostgreSQL if the product keeps it.

Do not let FastAPI or an external LLM provider decide product authorization. Do not let the browser call model providers directly.

## Identity: Keycloak

Keycloak is a strong fit because it is widely used, free enough for local development, and relevant to backend/platform learning.

Use Keycloak for:

- OIDC Authorization Code + PKCE login;
- JWT issuing;
- issuer/JWKS validation from Spring;
- coarse roles/scopes.

Spring still owns workspace ownership, asset authorization, assistant-context authorization, resource-level rules, and product-specific permission checks. Do not push all product authorization into Keycloak.

## Product PostgreSQL

Product PostgreSQL is the system of record.

Use it for:

- user/profile projections if needed;
- users and individually owned workspaces;
- asset metadata;
- processing job state;
- event outbox rows;
- transcript snapshots;
- indexing job state;
- assistant conversations/audit/history if retained;
- product status and audit records.

Use Flyway migrations rather than relying on `hibernate.ddl-auto=update` as Project3 matures.

Do not store raw media bytes in PostgreSQL. Do not treat Elasticsearch, MinIO, Redis, Kafka, FastAPI's Processing DB, or LLM providers as product source of truth.

## Redis

Redis is the primary Project3 cache and ephemeral-state choice.

Use it for:

- cache-aside reads after a real access pattern exists;
- rate-limit counters;
- idempotency keys for upload/index/assistant commands;
- short-lived polling/support state;
- optional lightweight locks with clear expiry.

Do not use Redis as durable product state. Do not cache authorization-sensitive data without user/workspace-safe keys and expiry.

Optional note: Valkey can be mentioned later as a Redis-compatible alternative if licensing or deployment policy becomes important, but Redis remains the Project3 primary choice.

## MinIO

MinIO gives Project3 a realistic object-storage boundary.

Use it for:

- uploaded raw media files;
- extracted audio artifacts if retained;
- transcript exports or derived processing objects if needed;
- local S3-compatible development.

PostgreSQL stores metadata and permissions. MinIO stores object bytes. Spring mediates product access.

The Spring adapter uses the AWS SDK v2 S3 client against MinIO's S3-compatible API. This keeps the Java boundary portable to other S3-compatible stores without using MinIO-specific product APIs.

For processing, Spring publishes the object key in the Kafka request event. FastAPI/Celery reads the media object from MinIO using internal service credentials. This avoids streaming large media bytes through Spring Boot into FastAPI for the normal processing path.

Current implementation note: Phase 2 stores uploaded raw media in MinIO and persists the object reference in PostgreSQL. Phase 3A adds the PostgreSQL outbox row for `asset.processing.requested` with `event_version = 1`. Phase 3B adds an internal outbox relay state-machine foundation, while the existing direct FastAPI upload remains as a transitional processing trigger until the Kafka/FastAPI async lifecycle is implemented.

Do not expose MinIO directly to the browser until a presigned URL model and authorization story are designed.

## Kafka

Kafka is the cross-service async event backbone.

Use Kafka for:

- `asset.processing.requested`;
- `transcript.ready`;
- `asset.processing.failed`;
- `index.requested` after transcript state is committed;
- retry and dead-letter topics for async jobs;
- later load/scale testing around worker throughput and lag.

The recommended Spring pattern is:

```text
Spring transaction
-> write product state + outbox row in Product PostgreSQL
-> publisher sends Kafka event
-> cross-service consumer handles async work
```

Current implementation note: Phase 3B implements the product state + outbox row portion and a small relay foundation. It defines the first processing event contract as `event_version = 1`, stores durable publication intent in PostgreSQL, and can move due outbox rows through attempt/status metadata via an `OutboxMessagePublisher` abstraction. It does not add a Kafka broker dependency, Kafka producer, scheduled relay execution, consumer, dead-letter routing, stuck-`PUBLISHING` recovery, or FastAPI event consumption yet.

The default logging publisher is intentionally a local placeholder. If someone manually enables and invokes the relay before a real broker publisher exists, the relay can mark rows `PUBLISHED` inside PostgreSQL, but that is not Kafka delivery and should not be treated as cross-service publication.

`event_version = 1` is a lightweight payload-contract version for `asset.processing.requested`, not a database-row version. It prepares the contract for future evolution, such as adding language, priority, or processing options, without introducing Schema Registry, Avro, Protobuf, or a broader event framework before the project needs one.

Kafka should not replace normal product APIs.

Do not use Kafka for login, workspace CRUD, simple metadata reads, synchronous search queries, every database update, or the normal interactive assistant request path.

## FastAPI + Celery

FastAPI already exists and should remain focused on internal AI/media execution.

Use FastAPI for:

- consuming `asset.processing.requested` from Kafka;
- validating processing payloads such as `assetId`, `objectKey`, and `correlationId`;
- enqueueing internal Celery tasks;
- publishing `transcript.ready` or `asset.processing.failed` back to Kafka;
- exposing an internal LLM Adapter / Prompt Executor endpoint to Spring Boot;
- processing-side health checks.

Use Celery for:

- internal worker task execution;
- ffmpeg audio extraction;
- Whisper transcription;
- worker concurrency control;
- retries that are local to processing execution.

Kafka and Celery are not interchangeable:

- Kafka is durable cross-service integration between Spring Boot and FastAPI.
- Celery is FastAPI's internal task queue and execution model.

Spring owns final product transcript snapshots and assistant authorization. FastAPI owns processing and prompt-execution mechanics.

Do not make FastAPI the public product backend, auth service, workspace owner, product database writer, assistant API owner, or search API.

## LLM Adapter / Prompt Executor

The LLM adapter belongs inside the internal FastAPI side because Python is a practical environment for prompt tooling, model SDKs, local model runtimes, and AI experimentation.

Use it for:

- prompt construction and execution;
- provider adapter abstraction;
- calling Ollama/local models, OpenAI/OpenRouter adapters, or other providers;
- response normalization;
- provider-specific retries and timeout handling;
- optional safety/policy hooks before returning to Spring.

Do not let it read arbitrary workspace data on its own. Spring should pass authorized context or approved context references.

## LLM Provider / Local Model Runtime

The LLM provider is a replaceable dependency, not product infrastructure that owns state.

Possible choices:

- Ollama / local model for local-first demos and free experimentation;
- OpenAI / OpenRouter adapter for stronger hosted model quality when credentials are available;
- other LLM providers behind the same internal adapter.

Do not hardwire the product to one provider in the architecture. Keep provider credentials server-side and out of the browser.

## Processing DB

The Processing DB is not product storage.

Use it only for:

- internal task status;
- scratch processing state;
- temporary processing metadata;
- debugging processing failures.

The final product transcript snapshot must be written through the Spring Boot-owned flow into Product PostgreSQL.

## Elasticsearch

Elasticsearch remains the primary Project3 search/indexing choice.

Use it for:

- derived transcript-row documents;
- text search over transcript rows and asset titles;
- workspace and asset filters;
- assistant context retrieval candidates;
- search relevance tuning later;
- explainable search infrastructure in a portfolio demo.

Indexing should happen after product state is saved:

```text
Spring consumes transcript.ready
-> Spring writes transcript/status to Product PostgreSQL
-> Spring publishes index.requested
-> Spring indexing consumer writes derived rows to Elasticsearch
```

Spring remains responsible for user/workspace/asset scope, deciding what can be indexed, shaping the public search response, and falling back to PostgreSQL transcript snapshots for context.

Do not let Elasticsearch become the source of truth or authorization authority.

Optional note: OpenSearch is a reasonable future alternative if license or deployment constraints require it, but Elasticsearch remains the primary Project3 choice because of popularity and learning relevance.

## Observability: Prometheus / Grafana / Loki

Observability belongs in Project3 because it prepares the project for deployment, performance testing, and production-hardening thesis work.

Use:

- Spring Boot Actuator and Micrometer;
- Prometheus for metrics scraping;
- Grafana for dashboards;
- Loki for local log aggregation;
- request/correlation IDs in logs;
- assistant latency/error metrics;
- processing lag and worker metrics;
- optional OpenTelemetry Collector later when trace propagation becomes useful.

Do not spend the first Project3 phase building production alerting, SLOs, incident workflows, or a large tracing platform before the runtime flow is stable.

## Docker Compose

Docker Compose is the right local platform.

Use it for:

- PostgreSQL;
- Redis;
- MinIO;
- Kafka;
- Elasticsearch;
- Keycloak;
- Prometheus/Grafana/Loki;
- Nginx;
- Spring Boot and FastAPI when ready;
- optional local model runtime such as Ollama through a profile.

Use profiles for heavy or optional components so the project stays runnable on a student laptop.

Do not make Kubernetes a Project3 baseline requirement. Kubernetes can become a later thesis/deployment topic.

## Why Debezium / CDC Is Not Core

Do not add Debezium, CDC, or a sync database as a core Project3 component.

Project3 has one Product PostgreSQL source of truth, and Spring Boot owns the write paths that matter:

- product metadata writes;
- transcript snapshot writes;
- outbox/event creation;
- indexing decisions;
- assistant conversation/audit writes if retained.

For Project3, explicit Spring-owned outbox/events and Spring-owned indexing are easier to explain, test, and operate locally than a CDC pipeline.

Debezium/CDC is still a credible future graduation-thesis extension for CDC-driven indexing, audit streams, data pipelines, or outbox publication experiments. It is intentionally excluded from the core architecture.

## Practical Free-Use And Licensing Notes

This stack is practical for local student development.

- PostgreSQL uses the PostgreSQL License: <https://www.postgresql.org/about/licence/>.
- Redis 8 offers RSALv2, SSPLv1, or AGPLv3; Redis 7.2 and earlier remain BSD-3-Clause. Redis remains practical for this local Project3 scope: <https://redis.io/legal/licenses/>.
- Elasticsearch uses Elastic's licensing model. It is practical for local learning, with more care needed for managed-service/commercial redistribution scenarios: <https://www.elastic.co/licensing/elastic-license>.
- MinIO is AGPLv3. Running it unmodified locally is practical; modifying/distributing/hosting it needs more care: <https://raw.githubusercontent.com/minio/minio/master/LICENSE>.
- Kafka and Keycloak use Apache 2.0: <https://raw.githubusercontent.com/apache/kafka/trunk/LICENSE>, <https://raw.githubusercontent.com/keycloak/keycloak/main/LICENSE.txt>.
- Prometheus uses Apache 2.0: <https://raw.githubusercontent.com/prometheus/prometheus/main/LICENSE>.
- Grafana and Loki are AGPLv3 for core OSS projects: <https://grafana.com/licensing/>.
- LLM provider licensing and pricing depend on the selected model/provider. Ollama/local models are practical for local demos when the chosen model license permits the intended use. Hosted providers require API keys, quota control, and cost awareness.

These notes are practical engineering guidance, not legal advice.

## Why This Fits Project3

This architecture is strong for a backend/platform portfolio because it uses popular technologies in their natural roles while keeping one clear product owner:

- Spring Boot for product backend engineering;
- PostgreSQL for durable consistency;
- Redis for performance and short-lived state;
- MinIO for object storage;
- Kafka for event-driven integration;
- FastAPI + Celery for AI/media processing;
- FastAPI LLM adapter for model execution;
- replaceable LLM provider for assistant capability;
- Elasticsearch for search and assistant context retrieval;
- Keycloak for OIDC/JWT;
- Prometheus/Grafana/Loki for operations.

It also matches the Viettel/backend learning direction while staying controlled: one Spring Boot product core, one internal processing/LLM execution service, replaceable model providers, and infrastructure that solves concrete product/platform problems.

## Graduation-Thesis Growth Path

This foundation can later support thesis work without changing the product architecture:

- deployment topology and environment promotion;
- CI/CD and release automation;
- load testing upload, processing, indexing, search, and assistant paths;
- Kafka lag and worker throughput experiments;
- LLM latency, cost, model-quality, and context-window experiments;
- PostgreSQL and Elasticsearch performance tuning;
- Redis cache effectiveness and rate-limit behavior;
- observability hardening with dashboards, traces, logs, and alerts;
- resilience testing around failed processing, re-indexing, and provider failures;
- optional Debezium/CDC experiments after the explicit outbox/event baseline is stable.

## What Project3 Should Not Add Yet

- Full microservice split.
- Kubernetes baseline.
- Debezium/CDC as a core component.
- Sync database architecture.
- gRPC.
- Service discovery.
- Full collaboration/team permission system.
- Broad autonomous-agent/RAG platform before the assistant scope is controlled.
- Kafka for normal CRUD.
- Paid cloud services as a baseline requirement.

These can become later extensions once the modular monolith, media pipeline, assistant path, and platform baseline are strong.
