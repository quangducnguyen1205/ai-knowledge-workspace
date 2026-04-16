# Deployable-Demo Baseline

## Purpose

This note records the current supported run mode for the minimal usable web product after the authenticated golden path milestone.

It is not a production deployment plan.
It is a narrow statement of the current demo-support baseline that the team can reliably run, verify, and hand to collaborators.

## Canonical Supported Run Mode

The canonical supported run mode is:

- Repo A / FastAPI started through its own existing Docker Compose setup
- Repo B PostgreSQL and Elasticsearch started through Repo B Docker Compose
- Repo B Spring Boot (`workspace-core`) run locally on the host
- Repo FE frontend started through its own Docker Compose setup

This is best described as a Docker-first demo topology with one supported local Spring process, not as a single all-in-one Compose project.

## Supported Topology

- Browser -> frontend at `http://localhost:5173`
- Frontend proxy -> Spring Boot at `http://localhost:8081`
- Spring Boot -> PostgreSQL at Repo B configured host/port
- Spring Boot -> Elasticsearch at Repo B configured host/port
- Spring Boot -> FastAPI through `FASTAPI_BASE_URL`

FastAPI remains a required internal processing dependency for the current product baseline.

## Default Supported Startup Sequence

1. Start Repo A first and confirm FastAPI reachability.
2. Start Repo B infrastructure (`postgres`, `elasticsearch`) through `infra/docker-compose.dev.yml`.
3. Start Repo B Spring Boot locally with `.env` loaded.
4. Run the authenticated backend smoke helper against `http://localhost:8081`.
5. Start Repo FE through its Docker Compose path.
6. Verify the browser flow through `http://localhost:5173`.

This order keeps dependency readiness clearer and makes it easier to distinguish:

- environment readiness failure
- upstream FastAPI/integration failure
- frontend proxy/runtime issue
- real product bug

## What "Deployable-Demo Baseline" Means Here

At the current project maturity, "deployable-demo baseline" means:

- there is one documented, repeatable supported run path
- the full authenticated golden path can be rerun consistently
- the product topology and dependency expectations are explicit
- smoke verification and browser verification have a clear order

It does not mean:

- production deployment readiness
- cloud infrastructure support
- one-command platform packaging across all repos
- observability/platform maturity

## What Stays Intentionally Out Of Scope

- cloud deployment infrastructure
- Kubernetes, CI/CD, or hosting automation
- single-project cross-repo orchestration
- broader auth/platform work
- AI/chat/RAG expansion
