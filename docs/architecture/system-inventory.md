# Project3 System Inventory

Source-of-truth inventory of the three repositories at the Phase 10 closure commit. Where an older
document disagrees with this file, this file is correct.

## Components and ownership

| Component | Repository | Owns | Public surface |
|---|---|---|---|
| Browser client | `ai-knowledge-workspace-fe` | Presentation, routing, upload interaction, search UI, saved moments, continue watching, citation navigation | Browser only |
| Spring product core (`workspace-core`) | `ai-knowledge-workspace` | Public HTTP API, authorization, Workspace/Asset state, canonical transcript, processing and indexing policy, search policy, assistant policy | `http://localhost:8081/api/**` |
| FastAPI processor | `DemoFastAPI` | Kafka request intake, Celery execution, media/artifact handling, durable result delivery, provider adapter | Internal only, never called by the browser |
| PostgreSQL 15 | infra Compose | **Product truth** | `localhost:5434` |
| Elasticsearch 8.11.1 | infra Compose | **Derived, rebuildable** transcript-row index | `localhost:9201` |
| Kafka 4.0.2 (KRaft) | infra Compose | At-least-once integration transport | `localhost:9092` |
| MinIO | infra Compose | Uploaded media objects and artifacts | `localhost:9000`, console `9001` |
| Keycloak 26.6.3 | infra Compose, `keycloak` profile | Optional OIDC issuer for the deferred `keycloak_jwt` mode | `localhost:8180` |
| Ollama | external, optional | Assistant answer generation through FastAPI | not started by this repository |

## Boundaries

**Public.** Only Spring is browser-facing. The browser talks to Spring and to nothing else; the
Vite dev server proxies `/api` to Spring so the browser stays same-origin. Verified in Phase 7, 8,
9 and 10 by capturing every browser request: the only non-Spring host observed is
`www.youtube.com`, loaded by the YouTube IFrame player for a YouTube-source Asset.

**Internal.** FastAPI is reachable only from Spring and from the Compose network. It owns no
public product state, no Workspace authorization and no browser-facing API.

## Synchronous flows

```text
browser → Spring → PostgreSQL          workspaces, assets, transcript, saved moments, playback progress
browser → Spring → Elasticsearch       search retrieval
browser → Spring → PostgreSQL          canonical context hydration of the selected hits
browser → Spring → MinIO               authorized Upload media GET/HEAD with Range
browser → Spring → FastAPI → provider  assistant answer (optional)
```

## Asynchronous flow

```text
Spring product transaction → outbox row
→ Spring relay → Kafka asset.processing.requested.v1 / .v2
→ FastAPI consumer → Celery → media/transcription
→ FastAPI result outbox → Kafka asset.processing.result.v1
→ Spring inbox (idempotent) → canonical transcript replaced atomically
→ Spring indexing outbox → Kafka asset.indexing.requested.v1
→ Spring indexing listener → Elasticsearch bulk write
→ Asset becomes SEARCHABLE
```

Upload uses request `v1`; YouTube creation and retry use `v2`; both consume the unchanged result
`v1`. Kafka is at-least-once, so the result inbox is keyed by `eventId` and duplicate delivery is a
no-op.

## Source of truth versus derived state

| State | Truth | Derived |
|---|---|---|
| Workspace, Asset identity and lifecycle | PostgreSQL | — |
| Canonical transcript rows | PostgreSQL `asset_transcript_rows` | — |
| Saved moments, playback progress | PostgreSQL | — |
| Search documents | — | Elasticsearch, rebuildable from PostgreSQL |
| Media bytes | MinIO object | — |
| Integration events | outbox/inbox tables in PostgreSQL | Kafka topics are transport |

Deleting or rebuilding the Elasticsearch index never changes product truth. Search hydration
re-validates every selected hit against PostgreSQL before returning it, so a stale derived document
is discarded rather than shown.

## Ports and endpoints

| Port | Service | Notes |
|---|---|---|
| 5173 | Vite dev server / frontend container | proxies `/api` to 8081 |
| 8081 | Spring product core | the only browser-facing API |
| 8000 | FastAPI processor | internal |
| 5434 | PostgreSQL | product truth |
| 9201 | Elasticsearch | derived index |
| 9092 | Kafka | integration transport |
| 9000 / 9001 | MinIO API / console | media objects |
| 8180 | Keycloak | optional, `keycloak` profile only |
| 11434 | Ollama | optional, external |

## Required versus optional dependencies

**Required for the product core to start:** PostgreSQL. Flyway migrates and Hibernate validates at
startup; without it the service fails fast.

**Required for the full documented flow:** Elasticsearch (search), Kafka (async processing and
indexing), MinIO (Upload media), FastAPI (transcription).

**Optional:** Keycloak (only for the deferred `keycloak_jwt` mode), Ollama (only for assistant
answer generation), Redis (declared in the FastAPI stack for Celery infrastructure).

**Degradation observed and accepted:** without MinIO, Upload media playback and Asset deletion of
an Upload Asset fail with a bounded `503`; without Kafka, asynchronous processing and the title
sync stop while reads keep working; without Elasticsearch, search returns a bounded
`503 SEARCH_SERVICE_UNAVAILABLE`.

## Authentication

Default mode is `legacy_session`: register/login establish a server-side session and `GET /api/me`
reads it. `keycloak_jwt` exists as a foundation and is deferred — it has no browser media-playback
path, as recorded in the Phase 4 acceptance.

A development fallback can resolve an anonymous request to `local-dev-user`. It is enabled by
default for local work and is switched off, and enforced off, by the `production-like` profile. See
[`../runbooks/deployment.md`](../runbooks/deployment.md).

## Corrections applied during this review

- Phase 5's "no watch-history or progress-list endpoint" was stale after Phase 9 and now points at
  the Continue-watching document.
- The Home screen listed newest Assets under the title `Continue watching`; it is now
  `Recent videos`, so `Continue watching` means one thing.
- Redis is commented out in the product infra Compose file and is not a product-core dependency;
  it belongs to the FastAPI stack.
