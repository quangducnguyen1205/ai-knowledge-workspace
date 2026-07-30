# Project3 Demo Script

A bounded **5–8 minute** live walkthrough. Timings assume the stack is already running and warm —
start it before the audience arrives, not during the demo.

No private credentials appear in this document. Create a throwaway demo account at demo time and
delete it afterwards (see [Cleanup](#cleanup)).

## Before the demo (not counted in the 5–8 minutes)

```bash
make stack-up                                                   # blocks until every dependency is healthy
make run                                                        # Spring, with the running revision stamped
docker compose -f ../ai-knowledge-workspace-fe/docker-compose.yml up -d
cd ../DemoFastAPI && make up                                    # only needed for the live-ingestion path
```

Confirm readiness — if any of these is wrong, fix it now, not on stage:

```bash
curl -s localhost:8081/api/build-info                # four fields, gitCommit matches HEAD
curl -s localhost:9201/_cluster/health | jq -r .status   # green or yellow
curl -s -o /dev/null -w '%{http_code}\n' localhost:5173  # 200
```

Have **one already-processed Asset with a transcript** in the demo Workspace. This is the safety
net: the demo must never depend on a transcription finishing while people watch.

---

## Minute 0:00–0:45 — Frame the problem

Say, don't click:

> "A two-hour lecture recording is a black box. You know the answer is in there, you don't know
> where. Project3 makes the spoken content searchable, and makes each moment a stable, shareable,
> resumable address."

Then, in one sentence, the shape: one browser-facing Spring service that owns product truth in
PostgreSQL, one internal transcription service, Elasticsearch as a rebuildable index.

## Minute 0:45–2:00 — Search inside the video

1. Open the Workspace. Point out **Continue watching** and **Recent videos** — different things,
   deliberately named apart.
2. Search a phrase that exists inside the audio, not in any title.
3. Point at one result: it is a **transcript row**, with a timestamp and surrounding context, not a
   document hit.

Say what is actually happening:

> "Elasticsearch proposes candidates. PostgreSQL decides. Before any hit is returned, its canonical
> row is re-read and compared. If the index has drifted, the hit is dropped in place — the answer
> never comes from derived state."

## Minute 2:00–3:15 — The moment is an address

1. Click the result. The player opens **at that moment** with the transcript row focused.
2. Show the URL: `#/assets/<assetId>?row=<transcriptRowId>`.
3. Copy it, open it in a fresh tab, land on the same row.

> "The link addresses a canonical transcript row, not a segment index and not a second offset. If
> the transcript is re-generated, this either resolves to the same row or fails honestly — it never
> silently sends you somewhere else."

## Minute 3:15–4:15 — Save it and resume it

1. Save the moment. Show it in **Saved moments**.
2. Click Save again — it stays one entry, no error.

> "There is no read-then-write here. The unique constraint is the concurrency boundary: we attempt
> the insert and treat the rejection as 'already saved'. Eight concurrent saves converge on one row."

3. Play for a few seconds, leave, return to the Workspace. The Asset is now in **Continue watching**
   at the position you left.

## Minute 4:15–5:30 — One engineering decision, in depth

Pick **one**. Do not attempt all three.

**Option A — the measured index.** Open
`services/workspace-core/src/main/resources/db/migration/V7__index_resumable_playback_progress.sql`
and read the header: sequential scan removing 79 609 rows and 1 009 buffers, versus an index scan at
143 buffers. Then say why the *faster* partial index was rejected — H2 cannot parse it, and forking
the migration chain per vendor would stop the portable tests from validating the production schema.

**Option B — the outbox.** Show the product transaction writing an outbox row, then claim → send →
finalize, and the result inbox keyed by `eventId`. No dual write, duplicates are no-ops.

**Option C — enforced boundaries.** Run one test and let it speak:

```bash
mvn -f services/workspace-core/pom.xml test -Dtest=ModuleBoundaryRulesTest
```

> "Repositories are package-private, controllers cannot see JPA entities, and cross-module access
> goes through a port the *consumer* owns and the *provider* implements. This is checked at build
> time, so it cannot decay."

## Minute 5:30–6:15 — Authorization, shown as a failure

Two authenticated users, one Workspace:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -b other.cookies \
  "localhost:8081/api/playback-progress?workspaceId=<workspaceId>"    # 404
```

> "A foreign resource returns 404, never 403. A 403 would confirm the resource exists. Missing and
> forbidden are deliberately indistinguishable, and error bodies carry no identifier, table name or
> SQL."

## Minute 6:15–7:00 — Operability

```bash
curl -s localhost:8081/api/build-info
```

> "Four fields: application, version, git commit, build time. No paths, no environment variables, no
> usernames, no dependency list. It degrades to nulls rather than guessing."

Show **Settings → Diagnostics** for the frontend revision, and say the startup is readiness-gated —
Compose health checks and `depends_on: service_healthy`, no fixed sleeps anywhere.

## Minute 7:00–8:00 — Close honestly

> "Single machine, single broker, single Elasticsearch node. No production traffic, no cloud, no
> Kubernetes. The known gaps are written down: JWT mode has no media-playback path, there is no
> dead-letter topic, and re-transcription silently invalidates saved moments pointing at replaced
> rows."

Land on the trade-off you most want to be asked about.

---

## Fallback: media processing is unavailable

If FastAPI, Whisper, MinIO or the network is not usable, **do not attempt a live upload.** Say so
and switch, without apology:

> "Transcription is a separate internal service and it isn't running right now. Everything after
> transcription is the interesting part anyway, and it's driven by the canonical transcript in
> PostgreSQL."

- **Use the pre-processed Asset.** Every step from minute 0:45 onward works unchanged — search,
  moment links, saved moments, Continue watching, authorization, build identity. Only the ingestion
  minute is lost.
- **If Elasticsearch is down**, search returns a bounded `503 SEARCH_SERVICE_UNAVAILABLE`. Show it
  and use it: the rest of the product keeps working, which *is* the failure-isolation claim. Then
  demo moment links, saved moments and Continue watching, which do not touch the index.
- **If nothing is running**, fall back to the code: `ModuleBoundaryRulesTest`, the `V7` migration
  header, and the outbox/inbox flow all read well without a runtime.

## Cleanup

Delete anything created during the demo, then stop the runtime. Never delete volumes, images,
networks or persistent developer data.

```bash
# 1. remove demo-created rows via the product API (preferred): delete the demo saved moments,
#    the demo Assets, then the demo Workspace, while still logged in as the demo account.

# 2. stop the runtime
make stack-down                                                      # stop only; volumes survive
docker compose -f ../ai-knowledge-workspace-fe/docker-compose.yml stop
cd ../DemoFastAPI && make down

# 3. verify
docker ps
lsof -nP -iTCP:8081 -sTCP:LISTEN; lsof -nP -iTCP:5173 -sTCP:LISTEN
```

If a demo account has to be removed at the database level, do it by explicit identifier and confirm
the row count first. Do not run a broad `DELETE` against a shared local database.
