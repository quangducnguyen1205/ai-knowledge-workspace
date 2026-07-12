# Local Development

## Purpose

This repository runs as the new product core for AI Knowledge Workspace. The legacy FastAPI repository remains a separate dependency and should be treated as an external internal service through `FASTAPI_BASE_URL`.

For the current minimal usable web product, the canonical supported run mode is a Docker-first demo topology with one supported local Spring Boot process:

- Repo A / FastAPI through its own existing Docker Compose path
- Repo B PostgreSQL + Elasticsearch + MinIO + Kafka through Repo B Docker Compose
- Repo B Spring Boot (`workspace-core`) on the host
- Repo FE frontend through its own Docker Compose path

See [deployable-demo-baseline.md](../planning/deployable-demo-baseline.md) for the concise supported-baseline decision.

## Local Port Plan

Repo A already uses:

- FastAPI: `8000`
- PostgreSQL: `5432`
- Redis: `6379`

This repository uses different host ports to avoid conflicts:

- workspace-core: `8081`
- workspace-core PostgreSQL: `5434`
- workspace-core Elasticsearch: `9201`
- workspace-core MinIO API: `9000`
- workspace-core MinIO console: `9001`
- workspace-core Kafka: `9092`
- optional workspace-core Redis: `6380`

## Files

- `infra/docker-compose.dev.yml`: local infrastructure for this repository
- `.env.example`: example environment values for local development
- `services/workspace-core/src/main/resources/db/migration`: Flyway migrations for the product schema

## Integrated Product Profile And Rollback

The normal integrated local Spring path is the `project3` profile:

```bash
make infra-up
make run
```

`make run` loads `.env` and activates `project3`. The profile selects `kafka_request`, the Kafka publisher, the processing-request relay, the processing-result listener, automatic indexing request creation, the indexing relay, and the indexing listener as one coherent set. It keeps `legacy_session`, uses the plain FastAPI URL `http://127.0.0.1:8000`, and keeps the assistant read timeout at 75 seconds. Startup fails before product work is accepted if `kafka_request` is combined with an incomplete relay/listener configuration.

The FastAPI repository must be running its `project3` integrated topology before Spring accepts uploads. The frontend continues to call Spring only and does not select a processing mode.

The deprecated but functional rollback path is:

```bash
make run-compatibility
```

The `compatibility` profile selects `direct_upload`, disables the asynchronous request/result/indexing controls, preserves legacy session authentication, and keeps explicit indexing available. It emits a non-blocking startup warning and remains supported temporarily for rollback and recovery. `make run-standalone` is a deprecated alias for `make run-compatibility`. Generic source defaults also remain compatibility-safe when no profile is active; no executable path was deleted or disabled, and no removal date is assigned.

New Project3 integrations must use the asynchronous default path. The completed controlled local observation campaign and rollback drills support entering a documented deprecation period, but they do not establish production-scale stability. Removal requires a separate decision after the gates in the [deprecation registry](../architecture/deprecations.md) are met.

### Migration from the old normal local flow

Old compatibility flow:

```text
run compatibility/direct processing
-> wait for TRANSCRIPT_READY
-> click Index transcript
```

Current normal flow:

```text
make project3-up in the FastAPI repository
+ make run in the Spring repository
-> upload
-> automatic processing
-> automatic indexing
-> SEARCHABLE
```

The explicit indexing endpoint and `Index transcript` UI remain supported recovery actions. Manual/one-shot relays, exact-ID recovery, and legacy session authentication are outside this deprecation scope.

Current environment-backed upload limit defaults:

- `WORKSPACE_CORE_MAX_FILE_SIZE=200MB`
- `WORKSPACE_CORE_MAX_REQUEST_SIZE=200MB`

Current object-storage defaults:

- `WORKSPACE_CORE_OBJECT_STORAGE_ENDPOINT=http://localhost:9000`
- `WORKSPACE_CORE_OBJECT_STORAGE_BUCKET=workspace-media`
- `WORKSPACE_CORE_OBJECT_STORAGE_ACCESS_KEY=minioadmin`
- `WORKSPACE_CORE_OBJECT_STORAGE_SECRET_KEY=minioadmin`
- `WORKSPACE_CORE_OBJECT_STORAGE_PATH_STYLE_ACCESS=true`

Spring uses the AWS SDK v2 S3 client against MinIO's S3-compatible API. Keep path-style access enabled for the local MinIO compose service.

Current product-auth defaults:

- `WORKSPACE_CORE_SECURITY_AUTHENTICATION_MODE=legacy_session`
- `WORKSPACE_CORE_SECURITY_OIDC_ISSUER_URI=`
- `WORKSPACE_CORE_SECURITY_OIDC_JWK_SET_URI=`
- `WORKSPACE_CORE_SECURITY_OIDC_AUDIENCE=`

`legacy_session` is the default and preserves the existing register/login/session path. `keycloak_jwt` is an opt-in foundation mode: Spring validates bearer JWTs as an OAuth2 resource server, maps provider plus OIDC `sub` to a local `UserAccount`, and keeps PostgreSQL workspace/asset ownership as the product authorization source. Set `WORKSPACE_CORE_SECURITY_OIDC_ISSUER_URI` when enabling JWT mode; `WORKSPACE_CORE_SECURITY_OIDC_JWK_SET_URI` can point at an explicit JWKS endpoint while issuer validation still uses the issuer URI. Audience validation is applied only when `WORKSPACE_CORE_SECURITY_OIDC_AUDIENCE` is set. Keycloak runtime setup is not part of the default local run.

Optional local Keycloak topology is profile-gated and is not part of the normal infrastructure startup:

- `KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.6.3`
- `KEYCLOAK_PORT=8180`
- `KEYCLOAK_REALM=workspace-dev`
- `KEYCLOAK_FRONTEND_CLIENT_ID=workspace-web`
- `KEYCLOAK_RESOURCE_AUDIENCE=workspace-core`

When explicitly started with the Docker Compose `keycloak` profile, Keycloak uses a dedicated `keycloak-postgres` database and the `workspace_core_keycloak_postgres_data` volume. It does not reuse Product PostgreSQL. The tracked realm import contains no users, passwords, client secrets, tokens, roles, or groups; temporary admin credentials come only from local environment variables. Keycloak import skips an already-existing realm, so deleting/recreating local realm data is an explicit operator action rather than startup behavior. P3-C2B `[ĐÃ SMOKE THỰC TẾ]` verified the backend path with real Authorization Code + PKCE tokens, no direct grant, Spring issuer/signature/audience validation, first-login local user/default-workspace provisioning, repeated-login idempotency, workspace ownership isolation, and rejection of legacy login/session-only identity in JWT mode. A controlled OIDC run can opt Spring into JWT mode with:

```bash
WORKSPACE_CORE_SECURITY_AUTHENTICATION_MODE=keycloak_jwt
WORKSPACE_CORE_SECURITY_OIDC_ISSUER_URI=http://localhost:8180/realms/workspace-dev
WORKSPACE_CORE_SECURITY_OIDC_AUDIENCE=workspace-core
```

Do not enable that mode for the default local path until a controlled Keycloak/OIDC smoke is the goal. P3-C3 `[ĐÃ XÁC MINH TỪ CODE]` adds the React/Vite opt-in bearer-token foundation: `VITE_AUTHENTICATION_MODE=legacy_session` remains the frontend default; `keycloak_jwt` requires public Keycloak client settings for `workspace-web`, uses Authorization Code + PKCE, keeps the access token in memory only, sends Spring API calls with `Authorization: Bearer <access-token>`, and treats `GET /api/me` as the Spring-owned product-user authority. The frontend must not use Keycloak roles or raw JWT claims for workspace or asset authorization. Browser Keycloak smoke, token refresh, silent SSO, global logout propagation, account management, default auth cutover, legacy-session removal, and collaboration/membership/RBAC remain future work.

Generic standalone and `compatibility` processing defaults:

- `WORKSPACE_CORE_PROCESSING_TRIGGER_MODE=direct_upload`
- `WORKSPACE_CORE_PROCESSING_REQUEST_RELAY_ENABLED=false`
- `WORKSPACE_CORE_PROCESSING_REQUEST_RELAY_FIXED_DELAY=10s`
- `WORKSPACE_CORE_PROCESSING_REQUEST_RELAY_BATCH_SIZE=10`

The normal integrated `project3` profile overrides these standalone defaults coherently. The automatic request relay remains scoped to `kafka_request` and requires the Kafka publisher. It relays a bounded batch of due `asset.processing.requested` rows and never relays result, indexing, or arbitrary outbox events.

P3-D2 `[ĐÃ SMOKE THỰC TẾ]` verified this as a normal opt-in runtime path with `WORKSPACE_CORE_PROCESSING_TRIGGER_MODE=kafka_request`, `WORKSPACE_CORE_PROCESSING_REQUEST_RELAY_ENABLED=true`, and the Spring result listener enabled before FastAPI result publication. The run used one Spring upload, no manual Spring request relay command, FastAPI/Celery processing from MinIO, one FastAPI result relay, and automatic Spring result consumption through to `TRANSCRIPT_READY`/`SUCCEEDED`. Search indexing stayed disabled.

P3-D4 `[ĐÃ SMOKE THỰC TẾ]` verified the fully automatic local runtime path after one-time Docker bootstrap: Spring automatic request relay published the selected durable request row, the FastAPI overlay automatic `result-relay` process published the selected durable result outbox row, and the Spring automatic result listener applied the result. No manual Spring request relay, manual result-file handler, manual recovery command, FastAPI one-shot result relay, or direct Kafka injection was used. Selected Spring/FastAPI/Redis/MinIO data was deleted afterward while Kafka history, consumer groups, Docker images, volumes, networks, and build cache were intentionally retained.

P3-E2 `[ĐÃ SMOKE THỰC TẾ]` validated the automatic processing-to-search composition before the profile cutover. P3-S3.A1 now packages that already-proven property set as the normal integrated `project3` profile. Retry topics, DLQ, stale-row automation, reindex, rebuild, and reconcile workflows remain future work.

Manual processing smoke controls:

- `WORKSPACE_CORE_PROCESSING_SMOKE_COMMAND=none`
- `WORKSPACE_CORE_PROCESSING_SMOKE_REQUEST_OUTBOX_EVENT_ID=`
- `WORKSPACE_CORE_PROCESSING_SMOKE_RESULT_EVENT_FILE=`

Supported one-shot smoke commands are `relay_request_outbox_once` and `handle_result_file_once`. They are disabled by default, run once, then close the Spring application context. They do not add an HTTP endpoint, scheduler, or Kafka listener. `relay_request_outbox_once` requires `WORKSPACE_CORE_PROCESSING_SMOKE_REQUEST_OUTBOX_EVENT_ID` and relays only that selected `asset.processing.requested` outbox event, never all due rows.

Manual operator recovery controls:

- `WORKSPACE_CORE_PROCESSING_RECOVERY_COMMAND=none`
- `WORKSPACE_CORE_PROCESSING_RECOVERY_RESULT_EVENT_ID=`
- `WORKSPACE_CORE_PROCESSING_RECOVERY_OUTBOX_EVENT_ID=`
- `WORKSPACE_CORE_PROCESSING_RECOVERY_MINIMUM_PUBLISHING_AGE=5m`

Supported recovery commands are `retry_failed_result_event_once` and `requeue_stuck_outbox_event_once`. They are disabled by default, run once, then close the Spring application context. They do not add an HTTP endpoint, scheduler, retry topic, DLQ, or broad recovery scan. `retry_failed_result_event_once` requires `WORKSPACE_CORE_PROCESSING_RECOVERY_RESULT_EVENT_ID` and retries only that selected durable `FAILED` consumed result event using the retained safe result envelope. `requeue_stuck_outbox_event_once` requires `WORKSPACE_CORE_PROCESSING_RECOVERY_OUTBOX_EVENT_ID`, requires the selected outbox row to still be `PUBLISHING`, and requires it to be older than `WORKSPACE_CORE_PROCESSING_RECOVERY_MINIMUM_PUBLISHING_AGE`. Empty and negative minimum ages are rejected; `0s` is allowed only for explicit controlled local smoke.

Generic standalone Kafka defaults:

- `KAFKA_IMAGE=apache/kafka:4.0.2`
- `KAFKA_HEAP_OPTS='-Xms256m -Xmx512m'`
- `KAFKA_MEMORY_RESERVATION=512m`
- `KAFKA_MEMORY_LIMIT=1g`
- `KAFKA_RESTART_POLICY=unless-stopped`
- `WORKSPACE_CORE_KAFKA_PORT=9092`
- `WORKSPACE_CORE_KAFKA_BOOTSTRAP_SERVERS=localhost:9092`
- `WORKSPACE_CORE_KAFKA_PROCESSING_REQUESTED_TOPIC=asset.processing.requested.v1`
- `WORKSPACE_CORE_KAFKA_PROCESSING_RESULT_TOPIC=asset.processing.result.v1`
- `WORKSPACE_CORE_KAFKA_INDEXING_REQUESTED_TOPIC=asset.indexing.requested.v1`
- `WORKSPACE_CORE_KAFKA_SEND_TIMEOUT=10s`
- `WORKSPACE_CORE_KAFKA_ENABLED=false`
- `WORKSPACE_CORE_KAFKA_LOGGING_PLACEHOLDER_ENABLED=false`

Repo B Docker Compose starts a single-node KRaft Kafka broker and a short-lived topic bootstrap helper for `asset.processing.requested.v1`, `asset.processing.result.v1`, and `asset.indexing.requested.v1`. Each topic uses one partition and replication factor one for local development.

### Local Kafka resource and restart baseline

The local Kafka service uses a bounded JVM heap (`-Xms256m -Xmx512m`), a 512 MiB container memory reservation, a 1 GiB container memory limit, and `unless-stopped` restart behavior. These are conservative Docker Desktop development defaults, not production sizing, and they do not guarantee that every OOM condition is impossible. Override `KAFKA_HEAP_OPTS`, `KAFKA_MEMORY_RESERVATION`, `KAFKA_MEMORY_LIMIT`, or `KAFKA_RESTART_POLICY` in the private `.env` when a different local machine budget is required; do not edit the tracked Compose file for machine-specific sizing.

Kafka continues to write KRaft data to the named `workspace_core_kafka_data` volume at `/tmp/kraft-combined-logs`. An unexpected process/container exit can restart automatically, while an intentional stop remains stopped. Spring and FastAPI Kafka clients are expected to reconnect after the broker becomes healthy. Events that already exhausted application outbox retry attempts remain `FAILED`; resource hardening does not repair those rows, and P3-S4.B2 owns that recovery problem.

Validate the effective configuration without starting services:

```bash
make kafka-config-check
```

The target renders Compose JSON and verifies the image, non-`no` restart policy, positive memory budget, heap/container relationship, persistent volume mount, and health check. To inspect only selected effective fields safely, render with `docker compose --env-file .env -f infra/docker-compose.dev.yml config --format json` and filter the `services.kafka` image, restart, memory, heap, volume target, and health-check presence; do not print the complete environment.

#### Kafka exited unexpectedly

1. Inspect the Kafka container exit code and Docker `OOMKilled` state.
2. Confirm the effective heap, memory reservation, memory limit, and restart-policy overrides.
3. Run `make kafka-config-check`.
4. Restore Kafka with `make infra-up` after confirming the configuration.
5. Inspect application outbox failure state through supported diagnostics.
6. Do not manually mutate outbox rows; exhausted retry recovery belongs to P3-S4.B2.

Generic standalone derived search indexing defaults:

- `WORKSPACE_CORE_SEARCH_INDEXING_AUTO_REQUEST_ENABLED=false`
- `WORKSPACE_CORE_SEARCH_INDEXING_RELAY_ENABLED=false`
- `WORKSPACE_CORE_SEARCH_INDEXING_RELAY_FIXED_DELAY=10s`
- `WORKSPACE_CORE_SEARCH_INDEXING_RELAY_BATCH_SIZE=10`
- `WORKSPACE_CORE_SEARCH_SMOKE_COMMAND=none`
- `WORKSPACE_CORE_SEARCH_SMOKE_INDEXING_OUTBOX_EVENT_ID=`
- `WORKSPACE_CORE_KAFKA_INDEXING_LISTENER_ENABLED=false`
- `WORKSPACE_CORE_KAFKA_INDEXING_CONSUMER_GROUP=workspace-search-indexer-v1`
- `WORKSPACE_CORE_KAFKA_INDEXING_AUTO_OFFSET_RESET=latest`

Explicit `POST /api/assets/{assetId}/index` remains supported as a fallback and uses the same indexing core as the automatic path. A repeated request for the same indexed snapshot is a successful no-op. In the integrated `project3` profile, a stable Spring-owned transcript snapshot normally creates an indexing job and metadata-only `asset.indexing.requested` outbox event automatically. PostgreSQL prevents duplicate active jobs for the same asset/fingerprint, and indexing completion rechecks the current fingerprint before marking the asset `SEARCHABLE`.

`relay_indexing_outbox_once` is a disabled-by-default smoke command that requires `WORKSPACE_CORE_SEARCH_SMOKE_INDEXING_OUTBOX_EVENT_ID` and relays only that selected `asset.indexing.requested` outbox row. P3-E1 `[ĐÃ XÁC MINH TỪ CODE]` adds an automatic indexing request relay with separate controls: it requires `WORKSPACE_CORE_SEARCH_INDEXING_RELAY_ENABLED=true` and `WORKSPACE_CORE_KAFKA_ENABLED=true`, relays only due `asset.indexing.requested` rows in bounded batches, and does not enable auto-request creation or the indexing listener. The indexing listener is also disabled by default. P3-B2 runtime-smoked this path by starting the listener before relaying one selected indexing event, writing derived Elasticsearch documents from Spring PostgreSQL snapshot rows, and proving that PostgreSQL asset state gates stale Elasticsearch documents. P3-B2.1 then runtime-smoked a fresh local Elasticsearch environment with no `asset-transcript-rows` index: the Spring indexing write path created the derived index lazily, indexed the selected asset documents, and completed search successfully without manual index pre-creation. Operator reindex, workspace rebuild, reconcile workflows, automatic stale-row recovery, retry topics, and DLQ remain future work.

The `project3` profile owns the complete automatic property set; operators should not assemble the six controls manually. P3-E2 `[ĐÃ SMOKE THỰC TẾ]` verified the underlying combination with Kafka, FastAPI/Celery/MinIO, Elasticsearch, and Spring host runtime without manual relay or recovery commands.

P3-F1 `[ĐÃ XÁC MINH TỪ CODE]` adds `POST /api/assistant/context` as a local retrieval-only assistant context pack endpoint. Send JSON with `workspaceId`, `query`, optional `assetId`, optional `maxSources`, and optional `contextWindow`. Defaults are `maxSources=5` and `contextWindow=1`; bounds are `maxSources 1..10`, `contextWindow 0..5`, and query length at most `500` characters. The endpoint returns cited source context only and reuses existing search/context authorization; it does not call FastAPI, invoke an LLM provider, generate an answer, persist chat history, or add embeddings.

P3-F2A `[ĐÃ XÁC MINH TỪ CODE]` adds `POST /api/assistant/answer` as the first non-streaming grounded answer code path. Send JSON with `workspaceId`, `question`, optional `assetId`, optional `maxSources`, and optional `contextWindow`. Spring reuses the same bounded assistant context flow, sends only Spring-issued source IDs and bounded source text to FastAPI, validates returned `citedSourceIds`, and returns `answer`, `citations`, and `insufficientContext`.

P3-F2B.1 `[ĐÃ SMOKE THỰC TẾ]` verified one controlled local Spring -> FastAPI -> native Ollama -> Spring grounded-answer run. The local runtime used Ollama `0.31.1` with `qwen3:1.7b`. The public answer request returned HTTP 200 with a nonblank answer, `insufficientContext=false`, and valid citations after Spring-side citation validation. Spring remained owner of authorization, bounded context selection, source IDs, and final citation validation; FastAPI remained an internal adapter; and the browser path did not call FastAPI or Ollama directly. The observed single request latency was about 12.3 seconds and is not a benchmark or production-readiness claim.

When using `POST /api/assistant/context` as a diagnostic preflight for `POST /api/assistant/answer`, use the exact same retrieval query text as the answer request if comparing source IDs externally. A short anchor query and a full answer question may retrieve different valid bounded source packs. Spring-side citation validation against the exact answer context is the authoritative product check.

Current Spring-to-FastAPI assistant adapter defaults:

- `FASTAPI_ASSISTANT_ANSWER_PATH=/internal/assistant/answer`
- `FASTAPI_ASSISTANT_READ_TIMEOUT=75s`

Generic FastAPI assistant-side defaults remain intentionally disabled. The integrated FastAPI `project3` topology overrides them with the runtime-proven values:

- `ASSISTANT_LLM_ENABLED=false`
- `ASSISTANT_OLLAMA_BASE_URL=http://host.docker.internal:11434`
- `ASSISTANT_OLLAMA_MODEL=qwen3:1.7b`
- `ASSISTANT_OLLAMA_TIMEOUT_SECONDS=15`

Integrated values are `ASSISTANT_LLM_ENABLED=true`, `ASSISTANT_OLLAMA_MODEL=qwen3:4b`, `ASSISTANT_OLLAMA_TIMEOUT_SECONDS=60`, and `ASSISTANT_OLLAMA_NUM_PREDICT=256`. These settings do not change the prompt, provider schema, citation aliases, or Spring-facing contract.

Ollama runs natively on the macOS host for the verified local smoke; FastAPI reaches it from Docker through `host.docker.internal`. This setup does not add an Ollama Docker service, model volume, streaming mode, chat persistence, embeddings, external provider, Kafka/outbox assistant flow, retry, DLQ, reindex, rebuild, or reconciliation workflow.

Current outbox-relay defaults:

- `WORKSPACE_CORE_OUTBOX_RELAY_ENABLED=false`
- `WORKSPACE_CORE_OUTBOX_RELAY_BATCH_SIZE=20`
- `WORKSPACE_CORE_OUTBOX_RELAY_MAX_ATTEMPTS=5`
- `WORKSPACE_CORE_OUTBOX_RELAY_RETRY_DELAY=30s`

The relay foundation is disabled by default. Phase 3C adds a Kafka publisher adapter, but no scheduler. Manual relay publishing requires both `WORKSPACE_CORE_KAFKA_ENABLED=true` and `WORKSPACE_CORE_OUTBOX_RELAY_ENABLED=true`, plus an explicit caller of the relay service.

Current schema-management defaults:

- `WORKSPACE_CORE_JPA_DDL_AUTO=validate`
- `WORKSPACE_CORE_FLYWAY_ENABLED=true`
- `WORKSPACE_CORE_FLYWAY_BASELINE_ON_MIGRATE=false`

Current outbox behavior:

- `direct_upload` remains the generic standalone and explicit rollback behavior. It stores FastAPI direct-upload task/video IDs and creates no processing-request outbox row.
- `kafka_request` is the normal integrated `project3` behavior. It atomically writes `Asset`, `ProcessingJob`, and one versioned `asset.processing.requested` outbox row in Product PostgreSQL, skips FastAPI direct upload, and leaves direct-upload identifiers null.
- The outbox row is durable publication intent for the Kafka processing lifecycle.
- Phase 3C adds local Kafka infrastructure and a Spring Kafka publisher adapter behind the relay boundary.
- Kafka publishing exists only when explicitly enabled. Scheduled relay execution is not implemented; for local smoke, `WORKSPACE_CORE_PROCESSING_SMOKE_COMMAND=relay_request_outbox_once` plus `WORKSPACE_CORE_PROCESSING_SMOKE_REQUEST_OUTBOX_EVENT_ID=<outbox-event-id>` can invoke the relay for exactly one selected request event.
- Exact-ID manual requeue exists for a selected request outbox row stuck in `PUBLISHING` after process interruption. It moves only that selected stale row back to `PENDING` and does not publish it automatically. Broad stale-row scans and scheduled recovery are still not implemented.
- Transition warning: the Spring request outbox relay remains disabled by default. Do not enable/request-relay ordinary `direct_upload` uploads; use `kafka_request` for request-path validation so the same asset is not processed twice. The manual smoke command remains scoped by event ID and will not relay arbitrary due outbox rows; the automatic relay remains scoped to due `asset.processing.requested` rows only.
- Phase 3D-H adds a disabled-by-default Spring Kafka result listener for `asset.processing.result.v1`. Enable it only for a controlled local run with `WORKSPACE_CORE_KAFKA_PROCESSING_RESULT_LISTENER_ENABLED=true`.
- The result listener defaults to consumer group `workspace-processing-result-v1` and `latest` offset reset. Start the listener before publishing result events in a local controlled run; it will not silently replay historical topic data for a new group by default.
- `transcript.ready` handling requires an internal FastAPI artifact endpoint: `GET /internal/processing-requests/{processingRequestId}/transcript-rows`.
- In result events, `processingRequestId` and `causationEventId` are the original Spring `asset.processing.requested` event ID. Spring stores that value on `ProcessingJob.processingRequestEventId`; `fastapiTaskId` remains the transitional direct-upload/FastAPI task ID.
- For local smoke, capture one result envelope to a temporary file and run `WORKSPACE_CORE_PROCESSING_SMOKE_COMMAND=handle_result_file_once` with `WORKSPACE_CORE_PROCESSING_SMOKE_RESULT_EVENT_FILE` pointing at that file. This calls the existing manual handler once; it does not install an automatic listener.
- Listener acknowledgement policy: `APPLIED`, duplicate already-applied, durable `FAILED`, and known malformed/unsupported result records are acknowledged with `MANUAL_IMMEDIATE`, so the commit happens immediately on the consumer thread. Unexpected runtime or infrastructure failures are rethrown and left unacknowledged for redelivery. This reduces unnecessary redelivery of earlier successfully handled records from the same poll, but delivery remains at-least-once. Durable `FAILED` rows require exact-ID manual recovery; there is still no retry topic, DLQ, broad failed-row scan, or automated failed-event recovery.
- Delivery is at-least-once; future consumers must be idempotent.

## Startup Sequence

### 1. Start Repo A First

Start the existing FastAPI stack from Repo A using its own Docker Compose setup.

Basic reachability check:

```bash
curl http://localhost:8000/openapi.json
```

If Repo A disables the OpenAPI endpoint locally, replace that check with any known working FastAPI endpoint from that repository.

### 2. Prepare Repo B Environment

From the root of `ai-knowledge-workspace`:

```bash
cp .env.example .env
```

### 3. Start Repo B Infrastructure

From the root of `ai-knowledge-workspace`:

```bash
docker compose --env-file .env -f infra/docker-compose.dev.yml up -d
```

Basic checks:

```bash
docker compose --env-file .env -f infra/docker-compose.dev.yml ps
docker compose --env-file .env -f infra/docker-compose.dev.yml exec postgres pg_isready -U workspace_core -d workspace_core
curl http://localhost:9201/_cluster/health
curl http://localhost:9000/minio/health/live
docker compose --env-file .env -f infra/docker-compose.dev.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list
docker compose --env-file .env -f infra/docker-compose.dev.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --describe --topic asset.processing.requested.v1
docker compose --env-file .env -f infra/docker-compose.dev.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --describe --topic asset.processing.result.v1
docker compose --env-file .env -f infra/docker-compose.dev.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --describe --topic asset.indexing.requested.v1
```

Optional Kafka-only CLI smoke, independent of the product outbox table:

```bash
printf "phase3c-smoke-key:phase3c-smoke-value\n" | \
  docker compose --env-file .env -f infra/docker-compose.dev.yml exec -T kafka \
    /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server kafka:29092 \
    --topic asset.processing.requested.v1 \
    --property parse.key=true \
    --property key.separator=:

docker compose --env-file .env -f infra/docker-compose.dev.yml exec -T kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 \
  --topic asset.processing.requested.v1 \
  --from-beginning \
  --timeout-ms 10000 \
  --property print.key=true \
  --property key.separator=:
```

The compose file also runs a short-lived `minio-create-bucket` helper that creates the configured raw-media bucket if it does not already exist.
It also runs `kafka-create-topics`, which creates `asset.processing.requested.v1`, `asset.processing.result.v1`, and `asset.indexing.requested.v1` if they do not already exist.

### 4. Start Spring Boot Second

Load the environment file and run `workspace-core` locally:

```bash
set -a
source .env
set +a
cd services/workspace-core
mvn spring-boot:run
```

If you prefer, run the same module from your IDE with the values from `.env`.

Flyway startup behavior:

- On a fresh Repo B PostgreSQL database, Flyway applies the product schema migration before Hibernate validates entities.
- On an older local database that was previously created by `hibernate.ddl-auto=update`, startup may fail because the schema is non-empty but has no Flyway history table.
- For disposable local data, recreating the Repo B PostgreSQL and MinIO volumes is the preferred path; Flyway then applies the clean Project3 schema from scratch and MinIO starts with a clean media bucket.
- For local demo data you must keep, manually migrate rows so workspaces have `owner_id`, assets have `workspace_id`, and assets have object-storage metadata, then set `WORKSPACE_CORE_FLYWAY_BASELINE_ON_MIGRATE=true` once after confirming the schema already matches the current entities.
- Use `WORKSPACE_CORE_JPA_DDL_AUTO=update` only as a temporary local troubleshooting fallback, not as the normal Project3 schema path.

### 5. Verify Connectivity

Check the local Spring Boot health endpoint:

```bash
curl http://localhost:8081/health
```

Check that Spring is still pointing at Repo A:

```bash
echo $FASTAPI_BASE_URL
curl "$FASTAPI_BASE_URL/openapi.json"
```

### 6. Verify Backend Before Browser

Before opening the frontend, treat the backend smoke helper as the default verification step for the supported demo baseline:

```bash
make smoke MEDIA_FILE=/absolute/path/to/lecture-video.mp4
```

Only move on to browser verification through the frontend after the backend smoke path passes.

### 7. Start The Frontend Last

From the frontend repo:

```bash
docker compose up --build
```

Expected frontend URL:

```bash
http://localhost:5173
```

The frontend proxies `/api` requests to the host Spring backend at `http://localhost:8081`.

## Thin Slice Smoke Helper

Once Repo A, PostgreSQL, Elasticsearch, MinIO, and Spring Boot are all running, you can exercise the current happy path with:

```bash
./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

By default, the helper now uses the authenticated product path:

- `POST /api/auth/register` or `POST /api/auth/login`
- `GET /api/me`
- workspace -> upload -> status -> transcript -> index -> search -> context
- raw media storage in MinIO before the transitional FastAPI processing trigger
- PostgreSQL outbox event creation for the future async processing request

This makes the authenticated backend path the default smoke verification route instead of the older local/dev shortcut.

Optional arguments:

```bash
./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4 "binary search tree"
```

Optional environment overrides:

- `WORKSPACE_CORE_BASE_URL`
- `SMOKE_POLL_INTERVAL_SECONDS`
- `SMOKE_POLL_TIMEOUT_SECONDS`
- `SMOKE_WORKSPACE_NAME`
- `SMOKE_VERIFY_CONTEXT`
- `SMOKE_CONTEXT_WINDOW`
- `SMOKE_AUTH_EMAIL`
- `SMOKE_AUTH_PASSWORD`
- `SMOKE_USE_LEGACY_AUTH_FALLBACK`
- `SMOKE_LEGACY_USER_ID`

Optional non-default workspace example:

```bash
SMOKE_WORKSPACE_NAME="Algorithms" ./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

Optional search-to-context follow-up example:

```bash
SMOKE_VERIFY_CONTEXT=1 ./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

Explicit authenticated smoke credentials example:

```bash
SMOKE_AUTH_EMAIL="smoke-user@example.com" \
SMOKE_AUTH_PASSWORD="password123" \
./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

On `localhost`, the helper can still use convenience smoke credentials if you omit them. For non-local `WORKSPACE_CORE_BASE_URL` targets, set `SMOKE_AUTH_EMAIL` and `SMOKE_AUTH_PASSWORD` explicitly so the script does not rely on predictable defaults outside local dev.

Explicit legacy local/dev fallback example:

```bash
CURRENT_USER_DEV_FALLBACK_ENABLED=true \
SMOKE_USE_LEGACY_AUTH_FALLBACK=1 \
SMOKE_LEGACY_USER_ID="smoke-dev-user" \
./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

The helper runs the current Spring-owned flow only:

- backend health check
- authenticated session setup in the default path
- optional workspace create/read
- workspace-aware asset listing
- upload
- status polling
- transcript fetch
- indexing
- search
- optional transcript context retrieval for the top search hit

It prints the created `assetId`, processing progress, transcript row count, indexed document count, and search result count.
When context verification is enabled, it also prints the chosen hit row and the returned transcript window.
If `SMOKE_WORKSPACE_NAME` is omitted, it exercises the default-workspace path.
If `SMOKE_WORKSPACE_NAME` is set, it also verifies a non-default workspace path end to end.
If register returns `EMAIL_ALREADY_REGISTERED`, the helper automatically falls back to login with the same credentials so reruns stay repeatable.

Failure classification hints:

- If the helper cannot reach `/health`, treat that first as a Spring/environment issue.
- If the helper fails at register/login or `/api/me`, treat that first as an auth/session setup issue.
- If upload or non-terminal status refresh fails with `FASTAPI_CONNECTIVITY_ERROR`, treat that first as a FastAPI readiness/integration issue.
- If backend smoke passes but the browser path through `http://localhost:5173` still fails, treat that first as a FE proxy/runtime integration issue rather than a backend-core bug.

## Local Verification Shortcuts

Backend tests use in-process stubs and mocked external boundaries. They do not require Repo A / FastAPI, Repo FE, PostgreSQL, Elasticsearch, or MinIO to be running.

From the repo root:

```bash
make help
make test-workspace-core
make smoke MEDIA_FILE=/absolute/path/to/lecture-video.mp4
make smoke-workspace MEDIA_FILE=/absolute/path/to/lecture-video.mp4 WORKSPACE_NAME="Algorithms"
```

Authenticated smoke through `make`:

```bash
make smoke \
  MEDIA_FILE=/absolute/path/to/lecture-video.mp4 \
  SMOKE_AUTH_EMAIL="smoke-user@example.com" \
  SMOKE_AUTH_PASSWORD="password123"
```

Those explicit auth overrides are required for non-local smoke targets. They stay optional only for the default localhost path.

`MEDIA_FILE` is intentionally required for `make smoke` and `make smoke-workspace` so the repo does not hardcode one contributor's local file path.
The smoke targets still enable the optional search-to-context check by default unless `SMOKE_VERIFY_CONTEXT` is overridden.

## Notes

- Do not try to run Repo A and Repo B inside the same Compose project for the first milestone.
- `FASTAPI_BASE_URL` is the integration boundary. Keep it explicit.
- Redis is intentionally optional for now.
- The current Spring Boot code uses PostgreSQL for persisted asset state and outbox publication intent, MinIO for raw media bytes, FastAPI for the transitional processing trigger, and Elasticsearch for indexing and search.
- PostgreSQL schema changes are expected to come through Flyway migrations.
- Multipart upload limits are environment-configurable through `WORKSPACE_CORE_MAX_FILE_SIZE` and `WORKSPACE_CORE_MAX_REQUEST_SIZE`.
