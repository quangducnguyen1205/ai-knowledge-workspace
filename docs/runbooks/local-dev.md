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
- workspace-core PostgreSQL: `5433`
- workspace-core Elasticsearch: `9201`
- optional workspace-core Redis: `6380`

## Files

- `infra/docker-compose.dev.yml`: local infrastructure for this repository
- `.env.example`: example environment values for local development

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

## Notes

- Do not try to run Repo A and Repo B inside the same Compose project for the first milestone.
- `FASTAPI_BASE_URL` is the integration boundary. Keep it explicit.
- Redis is intentionally optional for now.
- The current Spring Boot code uses PostgreSQL for persisted asset state, FastAPI for processing, and Elasticsearch for indexing and search.
