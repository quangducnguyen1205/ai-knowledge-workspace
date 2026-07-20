# Project3 Final Architecture

Status: authoritative description of the current Project3 architecture. Git history and the
submission documents retain validation history; this document describes the architecture that
new code must preserve.

## Architectural Style

Project3 is a DDD-oriented modular monolith with hexagonal boundaries inside meaningful Spring
modules. Spring Modulith discovers and verifies the module graph. The architecture has nine
Spring module roots and strict verification reports zero violations and zero dependency cycles.

The system deliberately combines three communication styles:

- direct Java calls through explicit module APIs for synchronous product decisions;
- Spring application events for future in-process asynchronous reactions that do not require a
  durable cross-process guarantee (no current durable workflow is implemented this way); and
- transactional outbox plus Kafka for durable processing and indexing workflows.

This is not a microservice decomposition. Spring remains the only public product backend.
FastAPI is an internal execution subsystem behind Spring-owned or consumer-owned ports.

## System Ownership

| Boundary | Owner | Rule |
|---|---|---|
| Browser-facing HTTP, authorization, workspace and asset lifecycle | Spring | The frontend calls Spring only. |
| Canonical transcript snapshot and product workflow state | Spring PostgreSQL | PostgreSQL is product truth. |
| Binary uploads and processing artifacts | MinIO | Product references remain in PostgreSQL; storage SDK types stay in adapters. |
| Processing execution and LLM provider calls | FastAPI | Internal transport DTOs never become Spring product contracts. |
| Durable cross-process transport | Transactional outboxes and Kafka | Kafka acknowledgement is not a product commit. |
| Search documents | Elasticsearch | Derived, rebuildable state; never product truth. |
| Cache and task infrastructure | Redis | Cache/Celery infrastructure only; never canonical product state. |

## Spring Modules

| Module | Owns | Intentional cross-module surface |
|---|---|---|
| `asset` | asset metadata/lifecycle, upload policy, canonical transcript snapshots, authorized asset reads | asset input contracts, transcript API, and state-owning adapters for consumer-owned ports |
| `assistant` | bounded context assembly, answer policy, citation validation | `assistant::port` provider and context capabilities |
| `common` | identity/auth integration, shared web error shape, neutral bootstrapping concerns | `common::api`, `common::workspace-provisioning`, `common::web` |
| `integration` | FastAPI HTTP configuration, wire DTOs, validation, transport-to-neutral mapping | no FastAPI wire package is a product module API |
| `outbox` | durable intent envelope, claim/publish/finalize/reconciliation mechanics | `outbox::application`, recovery configuration |
| `processing` | request intent, jobs, result parsing/inbox/idempotency/recovery, artifact orchestration | `processing::application`, `processing::artifact`, `processing::request-event` |
| `search` | indexing policy/jobs, query policy, Elasticsearch derived-state adapter | `search::application`, `search::query`, `search::configuration`, `search::indexing-request-event` |
| `storage` | object-storage capability and S3/MinIO implementation | `storage::application` |
| `workspace` | workspace model, ownership policy, default workspace and CRUD | `workspace::application` |

Root-level controllers, public HTTP DTOs, entities, and configuration entrypoints can remain
public for framework use. Java `public` visibility alone does not make a type a supported module
API. Named interfaces, explicit application contracts, and direct architecture rules define the
supported cross-module surface.

## Module API Versus Application Port

A module API is provider-owned. It is appropriate when another module synchronously invokes a
stable product capability, such as workspace resolution, object storage, processing request
creation, or search maintenance. A named interface exposes only the required immutable records
and operations; repositories and entities are not returned.

An application port is consumer-owned. It is appropriate when the consumer must state the exact
capability it needs without importing the provider implementation. Examples include processing
applying an asset result, search changing asset searchability, workspace checking asset usage,
assistant invoking an answer provider, and search writing/querying the derived index. The
state-owning or integration module supplies the adapter.

Input ports are introduced only when they give a real use-case entry contract. Controllers and
listeners are inbound adapters and call application use cases. Output ports describe required
capabilities; S3/MinIO, Elasticsearch, FastAPI HTTP, persistence, and Kafka implementations are
outbound adapters. One-method interfaces are not created mechanically, and domain/JPA models are
not duplicated unless their meanings genuinely differ.

## Current Dependency Corrections

The final convergence keeps the existing behavior while making these boundaries explicit:

- assistant answer orchestration depends on `AssistantAnswerProviderPort` and assistant-owned
  neutral records; the package-private FastAPI adapter owns all assistant wire DTO mapping;
- FastAPI properties and HTTP client configuration live under the integration boundary rather
  than `common`;
- search query, maintenance, and indexing execution depend on search-owned output ports;
  `TranscriptSearchIndexClient` owns Elasticsearch JSON, request, response, and exception
  translation;
- asset stores `workspace_id` as the existing scalar foreign-key value and obtains authorized
  workspace facts through the workspace API; it does not import the workspace JPA entity or
  repository;
- asset uses the storage application capability; obsolete root storage compatibility DTOs and
  clients are removed; and
- repositories and JPA entities remain inside their owning Spring module.

`TranscriptSearchIndexClient` intentionally remains one Elasticsearch adapter. Splitting its
query, write, bootstrap, delete, and title-update implementation would add churn without changing
the dependency direction; its consumers already see narrow application-owned ports.

## Package Convention

New modules, and existing modules that undergo a meaningful refactor, converge toward the
following shape where the responsibilities exist:

```text
<module>/
├── api/
└── internal/
    ├── application/
    │   ├── port/in/
    │   ├── port/out/
    │   ├── service/
    │   ├── command/
    │   ├── query/
    │   └── result/
    ├── domain/
    ├── adapter/
    │   ├── in/
    │   └── out/
    └── configuration/
```

Only folders justified by real code should be created. Existing packages such as
`application`, `adapter`, `infrastructure`, `integration`, and `transaction` remain valid when
they already communicate ownership clearly and pass the dependency rules. Forwarding facades,
empty layers, duplicate models, and cosmetic package moves are rejected.

## Dependency And Communication Rules

1. No module accesses another module's repository, JPA entity, adapter, internal service, wire
   DTO, SDK type, or provider exception.
2. Synchronous cross-module calls use a narrow named interface/module API or a consumer-owned
   port with a state-owning/provider adapter.
3. Domain and application decisions do not depend on HTTP controllers, JPA repositories, Kafka
   records, MinIO/AWS SDK types, Elasticsearch clients/JSON, or FastAPI wire DTOs.
4. Inbound adapters parse and validate transport contracts before calling application use cases.
   Outbound adapters map neutral application values to technology-specific contracts.
5. In-process events are not a substitute for a required synchronous transaction or durable
   workflow. Cross-process processing/indexing remains transactional-outbox plus Kafka.
6. PostgreSQL changes, canonical transcript replacement, inbox/outbox idempotency, and indexing
   begin/write/finalize semantics retain their characterized transaction boundaries.
7. Elasticsearch remains outside the database transactions where it is outside them today and
   remains rebuildable derived state.
8. Exact-ID/manual recovery paths remain explicit, secondary entrypoints and reuse the normal
   application use cases.

## Spring/FastAPI Boundary

The processing path is Spring durable request intent, Kafka request, FastAPI execution and durable
result intent, Kafka result, Spring result inbox, canonical transcript replacement, and automatic
indexing. Obsolete direct Spring processing behavior has been removed.

Product modules consume neutral asset/processing/assistant contracts. Only integration-internal
adapters know FastAPI URL paths, snake-case/camel-case wire fields, response validation rules,
timeouts, or integration exceptions. FastAPI does not own public product state or browser APIs.

## Architecture Enforcement

`BackendModularityBaselineTest` executes:

```java
ApplicationModules.of(WorkspaceCoreApplication.class).verify();
```

`ModuleBoundaryRulesTest` additionally protects seams that implicit root-package exposure alone
cannot express: FastAPI transport isolation, assistant/provider isolation, search/Elasticsearch
separation, listener and transaction boundaries, repository/entity ownership, workspace and
storage boundaries, and removal of obsolete facades/contracts.

The required architecture result is zero violations and zero cycles. No open modules, broad
allow-lists, exclusions, suppressions, or ratchet baselines may replace strict verification.

## Decisions Requiring Explicit Reapproval

Do not change the following without an explicit architecture decision and contract/transaction
characterization:

- Spring as the sole public backend or FastAPI as an internal subsystem;
- PostgreSQL truth, MinIO binary ownership, Redis cache-only use, or Elasticsearch derived-state
  status;
- public HTTP or Kafka contracts;
- schema/Flyway mappings or canonical transcript semantics;
- outbox, inbox, retry, recovery, acknowledgement, or idempotency semantics;
- processing-result transaction participation or indexing begin/write/finalize boundaries;
- authentication/profile behavior or retained recovery paths; or
- a new infrastructure framework, service split, or speculative abstraction layer.

The canonical validation command is:

```bash
mvn -q -f services/workspace-core/pom.xml test
```
