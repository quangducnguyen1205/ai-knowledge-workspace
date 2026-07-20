# Local Development Runbook

Status: current Spring product-core workflow after the pre-Phase-1 architecture overhaul.

## Prerequisites

- Java 21 and Maven;
- Docker Compose for project-specific PostgreSQL, Elasticsearch, MinIO and Kafka;
- a checked-out `.env` created from `.env.example`;
- the related FastAPI/frontend repositories only when running the integrated flow.

Do not use Docker prune. All reset commands must name only the Compose project/resources for this
repository.

## Static validation

```bash
make compile
make test
make kafka-config-check
docker compose --env-file .env -f infra/docker-compose.dev.yml config
```

`make test` runs the canonical Spring suite. It does not require starting the application or
changing a real database.

## Infrastructure

```bash
make infra-up
make infra-logs
make infra-down
```

| Service | Default endpoint | Project volume |
|---|---|---|
| PostgreSQL | `localhost:5434` / `workspace_core` | `workspace_core_postgres_data` |
| Elasticsearch | `localhost:9201` | `workspace_core_elasticsearch_data` |
| MinIO | `localhost:9000` | `workspace_core_minio_data` |
| Kafka | `localhost:9092` | `workspace_core_kafka_data` |

Keycloak and its database are opt-in through the `keycloak` Compose profile. Legacy session
authentication remains the normal local mode and does not require Keycloak.

Compose project prefixes depend on the working directory or explicit `--project-name`; inspect
`docker compose ... config`, `docker compose ... ps` and `docker volume ls` before deleting state.

## Clean database requirement

The current Spring schema is a consolidated single Flyway baseline. An older database with V2+
history is not supported. After inventorying the exact Compose project, recreate only its
PostgreSQL state. Starting Spring against the empty database must show Flyway applying version 1
and Hibernate validation succeeding.

If a complete Project3 integration reset is required, inventory and reset only:

- Spring `workspace_core` PostgreSQL state;
- Elasticsearch index `asset-transcript-rows` (or the configured project index);
- MinIO bucket `workspace-media` (or the configured project bucket);
- Kafka topics `asset.processing.requested.v1`, `asset.processing.result.v1`, and
  `asset.indexing.requested.v1`;
- related FastAPI/Redis state only when its repository runbook confirms the exact project scope.

Do not infer resource ownership from a generic name; query the running configuration first.

## Run Spring

```bash
make run
```

`make run-project3` is an equivalent explicit alias. Both activate the `project3` profile. There
is no Spring direct-upload or compatibility profile.

The coherent profile enables request relay, Kafka publication/result listening, automatic
indexing request/relay/listening, bounded outbox recovery and legacy-session local authentication.
The profile validator fails startup if one of these controls is accidentally disabled.

## Normal processing flow

```text
multipart upload to Spring
-> object stored in MinIO
-> one DB transaction writes asset + processing job + processing-request outbox
-> Spring relay publishes Kafka request
-> FastAPI/Celery processes and publishes durable Kafka result
-> Spring inbox applies canonical transcript atomically
-> indexing outbox/relay/listener writes Elasticsearch
-> asset becomes SEARCHABLE
```

Spring does not call a direct FastAPI upload/status endpoint. GET status and transcript operations
are side-effect free and read product state only.

## Smoke flow

With an authorized real media file and the required related runtime available:

```bash
make smoke MEDIA_FILE=/absolute/path/to/media.mp4 SEARCH_QUERY="binary search tree"

make smoke-workspace \
  MEDIA_FILE=/absolute/path/to/media.mp4 \
  WORKSPACE_NAME="Algorithms" \
  SEARCH_QUERY="binary search tree"
```

The helper validates upload, lifecycle, transcript context and search. Assistant/provider
validation is conditional on provider availability. Missing dependencies or fixtures must be
reported as blocked, not passed.

## Authentication and recovery

Local legacy-session mode can use the configured session/header/fallback behavior. Non-local
targets require real credentials. The development fallback must remain disabled outside an
explicit local environment. Keycloak JWT mode is an optional, separate validation path.

Supported operator controls remain narrow: relay one selected processing request, handle one
selected result envelope for smoke evidence, retry one selected durable failed result, requeue one
selected stale publishing outbox event, and explicitly index one authorized transcript-ready
asset. There is no generic all-event worker, broad failed-row scan, retry topic or Kafka DLQ.

## Expected runtime checks

1. Flyway applies the single baseline and Hibernate validates it.
2. Authentication resolves a server-side current user and creates/loads the default workspace.
3. Upload returns HTTP 202 with `assetId`, `processingJobId`, `assetStatus` and `workspaceId`.
4. Exactly one processing request correlation is used by the job and result.
5. Duplicate result delivery is an inbox no-op.
6. Canonical transcript rows exist before automatic indexing completes.
7. Search remains owner/workspace scoped and gated by searchable product state.
8. Deletion does not report success when required external cleanup fails.

Record actual commands, resource names and blockers for every destructive or provider-dependent
validation run.
