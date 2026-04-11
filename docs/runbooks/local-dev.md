# Local Development

## Purpose

This repository runs as the new product core for AI Knowledge Workspace. The legacy FastAPI repository remains a separate dependency and should be treated as an external internal service through `FASTAPI_BASE_URL`.

## Local Port Plan

Repo A already uses:

- FastAPI: `8000`
- PostgreSQL: `5432`
- Redis: `6379`

This repository uses different host ports to avoid conflicts:

- workspace-core: `8081`
- workspace-core PostgreSQL: `5434`
- workspace-core Elasticsearch: `9201`
- optional workspace-core Redis: `6380`

## Files

- `infra/docker-compose.dev.yml`: local infrastructure for this repository
- `.env.example`: example environment values for local development

Current environment-backed upload limit defaults:

- `WORKSPACE_CORE_MAX_FILE_SIZE=200MB`
- `WORKSPACE_CORE_MAX_REQUEST_SIZE=200MB`

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
```

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

## Thin Slice Smoke Helper

Once Repo A, PostgreSQL, Elasticsearch, and Spring Boot are all running, you can exercise the current happy path with:

```bash
./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

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

Optional non-default workspace example:

```bash
SMOKE_WORKSPACE_NAME="Algorithms" ./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

Optional search-to-context follow-up example:

```bash
SMOKE_VERIFY_CONTEXT=1 ./infra/scripts/smoke-thin-slice.sh /absolute/path/to/lecture-video.mp4
```

The helper runs the current Spring-owned flow only:

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

## Local Verification Shortcuts

From the repo root:

```bash
make help
make test-workspace-core
make smoke MEDIA_FILE=/absolute/path/to/lecture-video.mp4
make smoke-workspace MEDIA_FILE=/absolute/path/to/lecture-video.mp4 WORKSPACE_NAME="Algorithms"
```

`MEDIA_FILE` is intentionally required for `make smoke` and `make smoke-workspace` so the repo does not hardcode one contributor's local file path.
The smoke targets still enable the optional search-to-context check by default unless `SMOKE_VERIFY_CONTEXT` is overridden.

## Notes

- Do not try to run Repo A and Repo B inside the same Compose project for the first milestone.
- `FASTAPI_BASE_URL` is the integration boundary. Keep it explicit.
- Redis is intentionally optional for now.
- The current Spring Boot code uses PostgreSQL for persisted asset state, FastAPI for processing, and Elasticsearch for indexing and search.
- Multipart upload limits are environment-configurable through `WORKSPACE_CORE_MAX_FILE_SIZE` and `WORKSPACE_CORE_MAX_REQUEST_SIZE`.
