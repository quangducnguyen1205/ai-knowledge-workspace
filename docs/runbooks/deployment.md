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
| Spring | liveness/readiness probes below; `GET /api/build-info` reports the running revision |
| FastAPI | its own compose health configuration in `DemoFastAPI` |
| Frontend | `GET http://localhost:5173` returns `200` |

`kafka-create-topics` and `minio-create-bucket` are one-shot jobs. They exit `0` after doing their
work; an `Exited (0)` status for those two is the healthy steady state.

### Spring liveness, readiness, and capability health

Spring exposes the standard actuator health surface, anonymously and on the application port.
Only the `health` endpoint is exposed; every other management endpoint (`env`, `configprops`,
`beans`, …) is not reachable. Responses carry component names and statuses only — never
connection details or exception text.

| Probe | Path | Meaning of `DOWN` / non-200 |
|---|---|---|
| Liveness | `GET /actuator/health/liveness` | The process itself is broken — restarting it may be appropriate. Never depends on any remote service; a PostgreSQL outage does **not** fail liveness |
| Readiness | `GET /actuator/health/readiness` | Do not send product traffic. Includes exactly `readinessState` + `db`: without canonical PostgreSQL state no authenticated product request can be served |
| Aggregate | `GET /actuator/health` | Diagnostic view — worst status across all components, including capabilities. `503` here with readiness still `200` means a capability is degraded, not that the instance must leave traffic |
| Compatibility alias | `GET /health` | Same verdict as readiness, in the legacy shape `{"status","service"}` used by the smoke tooling. No longer a static `UP`: it returns `503`/`DOWN` when readiness fails |

Capability components in the aggregate view: `elasticsearch` (search — `_cluster/health`
green/yellow is up, red or unreachable is down) and `fastapi` (processing/assistant — its
unauthenticated `GET /health`, probed without the internal bearer token). A degraded capability
means that feature is unavailable while workspace, library, and authentication continue to work,
so these components are deliberately excluded from readiness. Kafka is not probed: publication
goes through the durable outbox and retries when the broker returns, so broker health belongs to
the later Kafka-operability task. MinIO likewise stays unprobed rather than gaining a
write-or-list probe that no traffic decision would consume.

## Async workflow metrics

Spring registers Micrometer gauges for the asynchronous state machines it owns, so an operator can
answer "how much work is waiting, how much failed, is anything stuck, and how old is the oldest
stuck item" without opening PostgreSQL. Every value is a `COUNT`/`MIN` over canonical rows, so it
is correct immediately after a restart rather than counting from zero.

| Metric | Tag | Meaning |
|---|---|---|
| `project3.outbox.events` | `status` = `pending` · `publishing` · `failed` | Outbox events in an actionable status. `PUBLISHED` is excluded — it is retained history, not work |
| `project3.outbox.stuck` | `status=publishing` | Events that have held a publication claim past the stuck threshold |
| `project3.outbox.stuck.age.oldest` | `status=publishing` | Seconds the oldest such event has held its claim; `0` when nothing is stuck |
| `project3.search.index.jobs` | `status` = `pending` · `indexing` · `failed` | Indexing jobs in an actionable status. `INDEXED` and `SUPERSEDED` are excluded as terminal history |
| `project3.search.index.stuck` | `status=indexing` | Jobs that have held an indexing claim past the stuck threshold |
| `project3.search.index.stuck.age.oldest` | `status=indexing` | Seconds the oldest such job has held its claim |
| `project3.processing.jobs` | `status=pending` | Jobs still waiting for a result from the processor |
| `project3.processing.wait.age.oldest` | `status=pending` | Seconds the longest-waiting job has waited |

**Stuck means a specific thing.** An outbox event is stuck when it is still `PUBLISHING` **and** was
claimed at least **5 minutes** ago: the relay claims, publishes within the Kafka send timeout, and
moves on, so a claim held for minutes means the claiming process died mid-publish. That threshold
mirrors `workspace.processing.recovery.minimum-publishing-age`, so a non-zero count is also the
number of events the manual PUBLISHING requeue command would accept. An indexing job is stuck when
it is still `INDEXING` after **5 minutes**, which exceeds any healthy attempt (bounded by the
Elasticsearch connect and read timeouts) — nothing reclaims it, so the asset stays unsearchable
until someone acts. Both thresholds are constants in code: the recovery command owns the tunable
copy, and a wedge detector has no deployment reason to vary.

**Backlog is not a wedge.** A high `pending` count while Kafka is down is work queued correctly;
the outbox is doing its job. Read `stuck` for corruption and `pending` for pressure.

Processing has **no** stuck gauge on purpose. A `PENDING` job is waiting on the external processor,
and Spring cannot distinguish a long transcription from a dead worker — it holds no lease or
heartbeat, only the eventual result event. Backlog and oldest-wait are reported; classifying the
wedge needs the processor and is deferred.

**Identifiers never become tags.** `status` is the only label, and its values are enum constants.
Asset, event, job, workspace, and user ids belong in the correlated logs, which answer "what
happened to this specific job"; metrics answer "is this class of jobs healthy". A test over the
whole meter registry enforces this.

These meters are **registered but not exported.** Actuator still exposes only `health`, so there is
no `/actuator/metrics` and no `/actuator/prometheus` — nothing consumes them over HTTP yet, and the
probe surface is unauthenticated. Choosing an exporter is a later task; the metric model is what
this one establishes.

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

Identity comes only from legitimate authentication, in every runtime and profile: the session
established by `POST /api/auth/register` / `POST /api/auth/login` in the default `legacy_session`
mode, or a validated bearer token in `keycloak_jwt` mode. An anonymous request — with or without a
caller-supplied user-id header — is rejected with `401 AUTHENTICATION_REQUIRED`.

The former development identity fallback (`X-Current-User-Id` header, `POST /api/auth/session`,
and the `local-dev-user` default identity behind `CURRENT_USER_DEV_FALLBACK_ENABLED`) has been
removed, along with the `production-like` profile and startup validator that existed only to keep
it out of production-like runs. Passing `production-like` in `SPRING_PROFILES_ACTIVE` is now a
harmless no-op. For local development, register or log in a local user — the smoke helper does
this automatically on localhost.

Error bodies never contain an identity: an unauthenticated request returns
`{"code":"AUTHENTICATION_REQUIRED","message":"Authentication is required"}`.

## Logs and failure diagnosis

| Symptom | Where to look | Usual cause |
|---|---|---|
| Spring exits at startup | Spring console output | PostgreSQL unreachable, or Flyway validation against an unsupported schema |
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
