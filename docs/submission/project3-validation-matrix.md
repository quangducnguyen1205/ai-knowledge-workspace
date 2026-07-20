# Project3 Validation Matrix

Status: current validation expectations for the pre-Phase-1 Spring baseline. Historical submission
runs and browser campaigns remain available in Git history; this matrix does not reinterpret them
as evidence for changed code.

## Evidence classes

| Class | Meaning |
|---|---|
| `CODE` | direct source/configuration/schema evidence |
| `TEST` | executable automated test evidence |
| `RUNTIME` | observed running service/data flow evidence |
| `BLOCKED` | required runtime dependency or fixture unavailable |
| `INFERENCE` | reasoned conclusion, not an executed fact |

## Spring validation

| Area | Evidence | Required result |
|---|---|---|
| compile | Maven compile/test compile | success |
| module graph | `BackendModularityBaselineTest` / Spring Modulith | zero violations and cycles |
| dependency direction | `ModuleBoundaryRulesTest` | application isolated from infrastructure/web/Spring Data; adapters/repositories internal |
| clean schema | `CleanBaselineMigrationTest` | one V1 from empty database; JPA validates; removed columns absent |
| public HTTP | controller contract tests | paths, status codes, JSON fields and safe errors unchanged |
| ownership | workspace/asset/search/assistant tests | current-user owner scope and not-found behavior |
| upload transaction | upload/boundary tests | object upload then atomic asset/job/request-outbox write; compensation on DB failure |
| processing | parser/codec/listener/application tests | topic/envelope/correlation stable; canonical replacement atomic |
| inbox idempotency | result-handler tests | duplicate no-op; known failure durable; unexpected failure rollback/redelivery |
| indexing | request/relay/executor/persistence tests | active-fingerprint uniqueness and begin/write/finalize semantics |
| outbox | relay/recovery/Kafka tests | at-least-once, typed bounded retry and exact-ID recovery |
| assistant | context/answer/controller tests | bounded sources and Spring-owned citation validation |

Canonical command:

```bash
mvn -q -f services/workspace-core/pom.xml test
```

## Runtime validation

| Flow | Required observation | Classification rule |
|---|---|---|
| Compose | rendered configuration names only Project3 resources | `RUNTIME` only after output is recorded |
| clean PostgreSQL | Flyway applies V1 to empty project database; Hibernate validates | `RUNTIME` after real PostgreSQL startup |
| identity/workspace | local authentication resolves user/default workspace | `RUNTIME` after HTTP/database observation |
| upload/request | HTTP 202 and one correlated request outbox/Kafka event | `RUNTIME` after real media upload |
| result/transcript | APPLIED inbox, SUCCEEDED job, canonical rows | `RUNTIME` after FastAPI processing/result |
| duplicate result | second same event ID causes no second product effect | `RUNTIME` after controlled duplicate |
| automatic indexing/search | current fingerprint indexed and owner-scoped query returns it | `RUNTIME` after Elasticsearch observation |
| assistant/citation | provider response is validated and citation resolves | `BLOCKED` when provider is unavailable |
| deletion | required index/object cleanup precedes DB deletion | `RUNTIME` after project-object observation |

Unit/integration tests do not automatically upgrade a row to `RUNTIME`. Unavailable media,
provider, Docker daemon, credentials or related service must be reported as `BLOCKED`.

## Cross-repository boundary

The overhaul does not change FastAPI or frontend repositories. Kafka request/result contracts and
Spring public HTTP paths/fields are retained. Source inspection can prove contract shape; only an
executed integrated run proves current runtime interoperability.

## Recorded overhaul run

| Evidence | Result | Class |
|---|---|---|
| Compose configuration parse and exact `infra` inventory | passed | `RUNTIME` |
| empty PostgreSQL 15.18 tmpfs: one V1, Hibernate validation, Spring startup | passed | `RUNTIME` |
| legacy-session registration and default workspace HTTP/persistence | passed | `RUNTIME` |
| persistent Project3 volume reset | denied by execution approval; no volume changed | `BLOCKED` |
| media upload through Kafka/FastAPI, duplicate result, indexing/search | not executed without approved clean integration state and media fixture | `BLOCKED` |
| assistant/provider and deletion cleanup | not executed without provider/full integration runtime | `BLOCKED` |
