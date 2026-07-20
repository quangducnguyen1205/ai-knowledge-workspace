# Project3 — AI Knowledge Workspace Baseline

Status: concise current baseline after the pre-Phase-1 Spring architecture overhaul. Historical
submission evidence remains in Git history and the immutable `project3-submission-v1` tag.

## Product and repository roles

| Repository | Role | Overhaul scope |
|---|---|---|
| `ai-knowledge-workspace` | Spring public product core, PostgreSQL truth, authorization, lifecycle, transcript, search and assistant policy | modified |
| `DemoFastAPI` | internal processing, artifact and provider execution | inspected, not modified |
| `ai-knowledge-workspace-fe` | browser presentation and Spring API client | inspected, not modified |

Spring remains a DDD-oriented modular monolith with workspace, asset, processing, search,
assistant, outbox, storage, integration and common modules. Spring Modulith and direct architecture
rules require zero dependency violations and zero cycles.

## Ownership

- Spring/PostgreSQL owns users, workspaces, assets, processing/indexing product state, canonical
  transcript snapshots, durable intent and processing-result inbox state.
- MinIO owns binary objects and processing artifacts referenced by product/integration metadata.
- Kafka transports at-least-once versioned processing/indexing events.
- FastAPI owns processing/provider execution, not public product state.
- Elasticsearch owns rebuildable derived transcript documents only.
- Redis/Celery is execution infrastructure, not product truth.

## Normal integrated flow

```text
Browser -> Spring upload -> MinIO
                       -> PostgreSQL asset/job/request outbox
                       -> Kafka -> FastAPI/Celery
                       <- Kafka result <- FastAPI result outbox
                       -> Spring inbox + canonical transcript transaction
                       -> indexing outbox -> Kafka -> Elasticsearch
Browser -> Spring search/assistant/citation APIs
```

Spring uses one Kafka/outbox upload-processing path. The former direct FastAPI processing/profile
rollback, status polling and transcript capture fallback are removed. Supported operator recovery
remains exact-ID/scoped and reuses normal application use cases.

## Architecture result

- Controllers depend on cohesive application input contracts and return web response records.
- Application code does not import infrastructure, Spring Data or web transport types.
- Persistence capabilities are application-owned `*Store` ports.
- Spring Data repositories and persistence adapters are package-private.
- Workspace remains cohesive CRUD; asset/search/assistant use fit-for-purpose command/query
  boundaries; there is no full CQRS or separate read database.
- Processing-result artifact retrieval retains its existing database transaction participation.
- Indexing retains database begin, Elasticsearch write outside transaction, database finalize.

The complete evidence and decisions are in
[`pre-phase1-architecture-overhaul.md`](../architecture/pre-phase1-architecture-overhaul.md).

## Schema result

Spring Flyway is consolidated into one clean V1 baseline. Existing local data compatibility is
not supported. Obsolete external-task correlation columns are absent; processing result
correlation uses the non-null unique processing request event ID. Timestamp-aware transcript
fields are not part of this baseline.

## Retained contracts and behavior

- public endpoint paths and JSON field names;
- Kafka topic names, keys and version-1 payload contracts;
- owner-scoped not-found behavior;
- outbox/inbox idempotency and bounded recovery;
- atomic canonical transcript replacement;
- Elasticsearch derived-state semantics;
- Spring-owned assistant source and citation validation;
- explicit indexing and legacy-session authentication.

## Validation

```bash
mvn -q -f services/workspace-core/pom.xml test
```

The suite covers HTTP contracts, module/dependency rules, clean Flyway bootstrap and JPA
validation, transaction boundaries, request/result/indexing contracts, idempotency, recovery,
ownership, canonical transcript lifecycle, search consistency and citation validation.

Runtime and browser evidence is bounded by actual local service/provider availability. Static
success is never reported as proof that an unavailable provider or media flow ran.

## Known deferred work

- Timestamp-aware transcript propagation is the next product phase and has not started here.
- Internal FastAPI service authentication/network policy remains deployment hardening.
- Neither service has a full Kafka retry-topic/DLQ topology.
- Processing artifact HTTP retrieval inside the result transaction requires a separate proven
  consistency/idempotency design before it can move.
- Authentication cutover and collaboration/RBAC remain separate product decisions.
