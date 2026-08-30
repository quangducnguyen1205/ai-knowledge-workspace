# Known Limitations

Every limitation that a reviewer, an interviewer or a future maintainer would otherwise discover on
their own. Each one is deliberate or accepted, and each says what would close it.

## Operational scope

| Limitation | Detail | What would close it |
|---|---|---|
| Single-machine topology | One PostgreSQL, one Kafka broker (replication factor 1), one Elasticsearch node, no TLS between services, no orchestration | A real deployment target; nothing in the product core assumes single-node |
| Never run in production | No production traffic, no commercial users, no cloud, no Kubernetes | — (stated so it is never implied otherwise) |
| No backup automation | Volumes are durable but nothing schedules or verifies a restore | A backup job plus a tested restore procedure |
| No secret manager | `.env` from `.env.example`, local-safe placeholders only, `.env` uncommitted | External secret storage at deployment time |
| Memory floor | The stack needs ~6 GB available to the Docker engine; below that Elasticsearch has been observed `Exited (137)` under concurrent transcription | Documented in the [deployment runbook](runbooks/deployment.md); bounded limits and `restart: unless-stopped` mitigate rather than remove it |

## Authentication

| Limitation | Detail |
|---|---|
| `keycloak_jwt` is a foundation, not a shipped mode | A native media element cannot carry an in-memory bearer token, so authorized Upload playback has no path in JWT mode. Recorded in the Phase 4 acceptance. Closing it needs a signed-URL or cookie strategy that has not been designed. |

## Search

| Limitation | Detail |
|---|---|
| No unaccented Vietnamese support | A query without diacritics does not match accented transcript text. Measured and recorded in `architecture/phase7-search-quality-baseline.md`; not claimed as supported anywhere. |
| Lexical only | Elasticsearch 8.11.1 lexical matching. There is no semantic or vector retrieval, and no retrieval-quality benchmark has been run against a baseline. |
| Validation cost on every search | Each returned hit is re-validated against PostgreSQL — bounded at 12 targets and 36 rows, but a real per-search cost. |
| Title change is coupled to search availability | `PATCH` on an Asset title requires Elasticsearch **and** an already-indexed Asset; otherwise it returns `503` rather than accepting the write and reconciling later. |

## Data and integration

| Limitation | Detail |
|---|---|
| Wholesale transcript replacement | Re-transcription replaces all rows for an Asset. Saved moments pointing at replaced rows stop appearing — correctly, but silently. |
| No dead-letter topic | Failed integration rows stay operator-visible in PostgreSQL and are recovered with SQL. Deliberate, but recovery is manual. |
| Payload alias tolerance | `ProcessingResultEventParser` accepts both camelCase and snake_case aliases — pragmatic during integration, less strict than a single canonical shape. |
| Portable-over-optimal index | `V7` uses a non-partial composite index. A partial index measured slightly better and smaller but H2 cannot parse it, and the portable tests run the production migration chain. |

## Testing

| Limitation | Detail |
|---|---|
| H2 cannot exercise PostgreSQL-specific SQL | H2 2.2.224 in PostgreSQL mode supports neither `ON CONFLICT` nor partial indexes. Empirically probed, not assumed. PostgreSQL-specific behaviour is covered by opt-in integration profiles that need Docker. |
| Deterministic transcripts require a declared fixture | The production write path pulls the artifact from FastAPI, so acceptance runs seed PostgreSQL directly and declare it. Indexing still goes through the production endpoint. |
| Test counts are not product metrics | 722 backend tests (0 failures, 17 skipped) and 53 frontend test files describe how the work was verified — nothing about users, traffic or quality of output. |

## AI functionality — what is and is not claimed

The system **uses** AI: Whisper for transcription through the internal FastAPI/Celery service, and
an optional local Ollama model for grounded assistant answers over retrieved canonical transcript
context, with row-level citations.

It does **not** train, fine-tune or evaluate models. No retrieval or generation quality has been
measured against a baseline. The assistant is a bounded feature over the same canonical transcript,
not a research contribution. Research directions that *would* produce measurable results are in
[`thesis/thesis-handoff.md`](thesis/thesis-handoff.md).

## Frontend

| Limitation | Detail |
|---|---|
| Internal `study` names retained | Some internal routes and folders still use the earlier `study` vocabulary. User-facing text is neutral product vocabulary; renaming internals was deliberately out of scope to avoid a large mechanical diff. |
| Build revision absent under the dev server | Vite's dev server injects no `VITE_APP_REVISION`, so **Settings → Diagnostics** shows `unknown`. This is the tested safe-degradation path, not a defect. |

## Where the reasoning lives

Each limitation above is argued, with the alternative that was rejected, in
[`architecture/architecture-review.md`](architecture/architecture-review.md).
