# Deployment And Operations Runbook

Reproducible local production-like deployment of the complete Project3 topology, plus the
operational policy that keeps it running on a developer machine.

This is a **single-machine Compose topology**. It is not high availability, it is not a cloud
deployment, and it has never served production traffic.

## Target topology

```text
browser → frontend (5173) → Spring product core (8081)
                             ├── PostgreSQL (5434)    product truth
                             ├── Elasticsearch (9201) derived index
                             ├── Kafka (9092)         integration transport
                             ├── MinIO (9000)         media objects
                             └── FastAPI (8000)       internal processor → Celery, provider
```

FastAPI is internal. The browser never calls it.

## Prerequisites

- Docker with at least **6 GB** of memory available to the engine. The dependency stack is bounded
  to roughly 4 GB (see the resource policy below) and the JVM plus the frontend need headroom.
- Java 21 and Maven for the product core.
- Node 20 (or the frontend container) for the browser client.

## Startup

```bash
# 1. one-time: create a private .env from the template and edit the placeholders
cp .env.example .env

# 2. start every dependency and block until each health check reports healthy
make stack-up

# 3. start the product core with the running revision stamped into build-info
make run

# 4. start the browser client
docker compose -f ../ai-knowledge-workspace-fe/docker-compose.yml up -d

# 5. start the internal processor (separate repository)
cd ../DemoFastAPI && make up
```

`make stack-up` runs `infra-up` then `infra-wait`. `infra-wait` polls the Compose health status of
every service every two seconds and returns as soon as all of them are healthy; on timeout (90
polls, about three minutes) it prints which service is still unhealthy and the full `ps` output.
**There is no blind fixed startup delay** — startup ordering is readiness-based, both in the
Makefile's health poll and in `depends_on: condition: service_healthy`. The two-second interval is
the polling cadence, not a wait for a fixed duration: a stack that is healthy immediately proceeds
on the first poll.

## Shutdown

```bash
make stack-down                     # stops containers; named volumes are never removed
docker compose -f ../ai-knowledge-workspace-fe/docker-compose.yml stop
cd ../DemoFastAPI && make down
```

`make stack-down` uses `docker compose stop`. It deliberately does **not** use `down`, so networks
and named volumes survive. No target in this repository deletes persistent data. Removing volumes
is a manual, explicit act; see the reset rules in [`local-dev.md`](local-dev.md).

## Environment template and secrets

`.env.example` is the authoritative template and the complete inventory of the environment inputs
this repository supports — the variable list is not duplicated here or anywhere else. It contains
only local-safe placeholders: the default MinIO and PostgreSQL credentials are the well-known local
development values and the Keycloak entries are explicitly marked `change-me-local-only`. `.env`
itself is not committed.

Read it with two conventions in mind. An **active** key is a supported input shown at its current
effective default, so copying the file changes nothing. A **commented-out** key is an optional
override knob; its comment says what it does and what happens when it is left absent. A variable
that appears in neither form is not a supported input — the `WORKSPACE_CORE_IT_POSTGRES_*`
integration-test variables below are the example, set by one test command rather than by an
operator.

No secret is required to run the test suites.

## Persistent volumes

Compose prefixes every declared volume with the project name, which is the directory holding the
Compose file. Both forms are listed because the declared name is what you read in the YAML and the
prefixed name is what `docker volume ls` prints.

| Declared in Compose | On disk | Holds |
|---|---|---|
| `workspace_core_postgres_data` | `infra_workspace_core_postgres_data` | product truth — the only volume whose loss loses data |
| `workspace_core_elasticsearch_data` | `infra_workspace_core_elasticsearch_data` | derived index, rebuildable from PostgreSQL |
| `workspace_core_minio_data` | `infra_workspace_core_minio_data` | uploaded media objects |
| `workspace_core_kafka_data` | `infra_workspace_core_kafka_data` | KRaft log |
| `workspace_core_keycloak_postgres_data` | `infra_workspace_core_keycloak_postgres_data` | optional Keycloak state |
| `postgres_data` (FastAPI repository) | `demofastapi_postgres_data` | FastAPI processing state |

## Resource policy

Bounded for a developer machine, overridable from `.env`:

| Service | Limit | Reservation | Heap |
|---|---|---|---|
| PostgreSQL | `POSTGRES_MEMORY_LIMIT=1g` | — | — |
| Elasticsearch | `ELASTICSEARCH_MEMORY_LIMIT=1500m` | `1g` | `-Xms512m -Xmx512m` |
| Kafka | `KAFKA_MEMORY_LIMIT=1g` | `512m` | `-Xms256m -Xmx512m` |
| MinIO | `MINIO_MEMORY_LIMIT=512m` | — | — |

### The Elasticsearch `Exited (137)` risk

During Phase 7 the Elasticsearch container was observed exiting with code `137` and
`OOMKilled=true` while a Whisper transcription workload saturated Docker Desktop's memory. Two
bounded changes address it, and neither claims a memory leak — there is no evidence of one, and the
JVM heap was already capped:

1. **A container memory limit above heap plus headroom.** The JVM heap is 512 MB; the container
   limit is 1500 MB so off-heap allocations and the page cache have room. A limit set at the heap
   size would make the OOM kill more likely, not less.
2. **`restart: unless-stopped`.** If the host still runs out of memory, the node comes back instead
   of leaving search silently unavailable until someone notices.

The documented minimum is therefore **6 GB available to the Docker engine**. Running memory-heavy
transcription concurrently with the full stack on less than that is the condition under which the
kill was originally observed.

## Health checks

| Service | Check |
|---|---|
| PostgreSQL | `pg_isready` (Compose health check) |
| Elasticsearch | `GET /_cluster/health` must report `green` or `yellow`, 30 s start period |
| Kafka | `kafka-topics.sh --list` against the internal listener |
| MinIO | `GET /minio/health/live` |
| Spring | `GET /api/build-info` answers once the context is up; `GET /api/me` returns `401` or `200` |
| FastAPI | its own compose health configuration in `DemoFastAPI` |
| Frontend | `GET http://localhost:5173` returns `200` |

`kafka-create-topics` and `minio-create-bucket` are one-shot jobs. They exit `0` after doing their
work; an `Exited (0)` status for those two is the healthy steady state.

## Build identity

```bash
curl -s http://localhost:8081/api/build-info
{"application":"workspace-core","version":"0.0.1-SNAPSHOT",
 "gitCommit":"f16d8c14451755181f7ed774d6262743e7a6773c","buildTime":"2026-07-30T09:18:23.766Z"}
```

The Maven build writes `META-INF/build-info.properties`; `make run` and `make build-identity` stamp
`-Dbuild.git.commit=$(git rev-parse HEAD)` into it. The endpoint exposes exactly four fields and
degrades to nulls when the build did not supply them — it never returns a repository path, an
environment variable, a username or a dependency list.

The frontend build revision is injected at build time from `VITE_APP_REVISION` and shown in
**Settings → Diagnostics**. Under the Vite dev server no revision is injected and the surface shows
`unknown`, which is the tested safe-degradation path.

## Authentication modes

| Runtime | `current-user.dev-fallback-enabled` | Anonymous request |
|---|---|---|
| Local development (default) | `true` | resolved to `local-dev-user` |
| `production-like` profile | forced `false` | `401 AUTHENTICATION_REQUIRED` |

`application-production-like.yml` sets the flag to `false`, and
`ProductionLikeAuthenticationProfileValidator` **refuses to start** if the profile is active while
the flag has been re-enabled through the environment. The invariant cannot be lost silently.

```bash
# production-like run
SPRING_PROFILE="project3,production-like" make run
```

Error bodies never contain an identity: an unauthenticated request returns
`{"code":"AUTHENTICATION_REQUIRED","message":"Authentication is required"}`, and the startup
failure message names the property to change, not the user it would have resolved.

## Logs and failure diagnosis

| Symptom | Where to look | Usual cause |
|---|---|---|
| Spring exits at startup | Spring console output | PostgreSQL unreachable, or Flyway validation against an unsupported schema |
| `Refusing to start: the production-like profile …` | Spring console output | `CURRENT_USER_DEV_FALLBACK_ENABLED=true` with the production-like profile |
| Search returns `503 SEARCH_SERVICE_UNAVAILABLE` | `docker compose logs elasticsearch` | node down, or still recovering shards |
| Elasticsearch `Exited (137)` | `docker inspect infra-elasticsearch-1` | host memory exhaustion; see the resource policy |
| Asset title change returns `503` | Spring log, `code=SEARCH_SERVICE_UNAVAILABLE` | Asset has no indexed documents yet, or Kafka is down |
| Upload delete returns `503` | Spring log | MinIO not running |
| Nothing is processed | `docker compose logs kafka`, FastAPI consumer log | Kafka down, or the FastAPI consumer is not running |

Container logs: `make infra-logs`, or `docker compose logs <service>`. Spring logs to the console
of whichever shell ran `make run`.

## Validation commands

```bash
mvn -f services/workspace-core/pom.xml test        # unit, architecture, migration
make kafka-config-check                            # static Kafka runtime policy, local .env
docker compose --env-file .env -f infra/docker-compose.dev.yml config --quiet
git diff --check
```

The same two deployment gateways, in their clean-checkout form. A clean checkout has `.env.example`
but no `.env`, so both read the committed template and neither creates a file:

```bash
make kafka-config-check ENV_FILE=.env.example
docker compose --env-file .env.example -f infra/docker-compose.dev.yml config --quiet
```

Integration profiles that need Docker are separate and explicit:

```bash
mvn -f services/workspace-core/pom.xml -Psearch-quality-it verify
mvn -f services/workspace-core/pom.xml -Pcanonical-context-it verify
mvn -f services/workspace-core/pom.xml -Psaved-moment-it verify
WORKSPACE_CORE_IT_POSTGRES_URL=jdbc:postgresql://localhost:5434/postgres \
WORKSPACE_CORE_IT_POSTGRES_USER=workspace_core \
WORKSPACE_CORE_IT_POSTGRES_PASSWORD=workspace_core \
mvn -f services/workspace-core/pom.xml test -Dtest=AssetPlaybackProgressConcurrencyPostgresTest
```
