# Project3 Validation Matrix

## Classification legend

| Classification | Meaning |
|---|---|
| `VERIFIED_STATIC_C1` | Re-executed in C1 without starting runtime services |
| `VERIFIED_RUNTIME_EXISTING` | Accepted from a named bounded runtime phase; not repeated in C1 |
| `PASSED` | The stated validation completed successfully within its declared scope |
| `PARTIAL` | No known defect was found, but the evidence does not cover every compatibility/recovery branch |
| `RETAINED` | Functional compatibility or recovery surface deliberately remains available |
| `DEFERRED` | Not proven or not implemented; no stronger claim is made |

Static tests prove contracts and wiring only to the degree encoded by those
tests. Runtime/browser evidence is local and bounded. Neither classification is
a production-scale or security-certification claim.

## Spring product-core validation

| Area | Evidence | C1 result | Classification |
|---|---|---|---|
| Canonical suite | `mvn -q -f services/workspace-core/pom.xml test` | 85 suites; 464 tests; 0 failures; 0 errors; 0 skipped | `VERIFIED_STATIC_C1`, `PASSED` |
| Application context | `WorkspaceCoreApplicationContextTest` in the canonical suite | Context created with repository test configuration | `VERIFIED_STATIC_C1`, `PASSED` |
| Profiles/configuration | Project3 coherence, compatibility/default properties, listener/relay and FastAPI settings tests | Green in canonical suite | `VERIFIED_STATIC_C1`, `PASSED` |
| HTTP contracts | Asset, workspace, auth, search, assistant controller/advice tests | Green; no C1 source/API change | `VERIFIED_STATIC_C1`, `PASSED` |
| Kafka contracts | Processing/indexing golden codecs, result parser/listener, outbox/relay tests | Green; topics, identity, keys, versions, payload/timestamp semantics unchanged | `VERIFIED_STATIC_C1`, `PASSED` |
| Transaction boundaries | Asset upload, processing-result, indexing begin/write/finalize characterization tests | Green | `VERIFIED_STATIC_C1`, `PASSED` |
| Modulith ratchet | `BackendModularityBaselineTest` and committed fingerprint | 79 violation messages; 0 cycle messages | `VERIFIED_STATIC_C1`, `PASSED` ratchet; strict verification remains red |
| Integrity | `git diff --check` | Passed before documentation changes | `VERIFIED_STATIC_C1`, `PASSED` |

## FastAPI processing validation

Validated HEAD: `1bb878f6e430cebce7bdf9ea4c297d4c1aa023e4`.

| Area | Command/evidence | C1 result | Classification |
|---|---|---|---|
| Canonical unit suite | `PYTHONPATH=backend python -m unittest discover -s backend -p 'test_*.py'` | 65 tests passed | `VERIFIED_STATIC_C1`, `PASSED` |
| Source compilation | `python -m compileall -q backend/app` | Passed (C1 directed bytecode output outside the repository) | `VERIFIED_STATIC_C1`, `PASSED` |
| Stable entrypoint imports | ASGI app, Celery app, processing consumer, manual relay, automatic relay | Imported without starting network/database/runtime work | `VERIFIED_STATIC_C1`, `PASSED` |
| Base Compose parse | `docker compose -f docker-compose.yml config --quiet` | Passed; no containers started | `VERIFIED_STATIC_C1`, `PASSED` |
| Project3 Compose parse | Base plus `docker-compose.project3.yml`, `config --quiet` | Passed; no containers started | `VERIFIED_STATIC_C1`, `PASSED` |
| Processing contracts | Request parser, idempotent dispatch, worker adapter/execution tests | Green | `VERIFIED_STATIC_C1`, `PASSED` |
| Result delivery | Codec, durable append/claim, publisher, relay, reconciliation tests | Green | `VERIFIED_STATIC_C1`, `PASSED` |
| Assistant adapter | Structured response, timeout, provider alias mapping tests | Green | `VERIFIED_STATIC_C1`, `PASSED` |
| Runtime refactor evidence | P3-S5.B3.R1 integrated path | Reached `SEARCHABLE`; no refactor-induced defect | `VERIFIED_RUNTIME_EXISTING`, `PARTIAL` |

P3-S5.B3.R1 retains the exact classification
`P3_S5_B3_R1_RUNTIME_PARTIAL`. Direct-upload end-to-end processing was not
repeated in that phase; transient result recovery was observed but not driven
through a complete deterministic controlled cycle; the manual one-shot relay
passed only as a bounded no-op. These are evidence-depth limits, not known B3
implementation defects.

## Frontend validation

Validated HEAD: `b71e32615ad1ee4c468b1ffe01f3603ea98f8eed`.

| Area | Command/evidence | C1 result | Classification |
|---|---|---|---|
| Canonical unit suite | `pnpm test` | 15 test files; 74 tests passed | `VERIFIED_STATIC_C1`, `PASSED` |
| Type safety | `pnpm typecheck` | Passed | `VERIFIED_STATIC_C1`, `PASSED` |
| Production bundle | `pnpm build` | 136 modules transformed; build passed | `VERIFIED_STATIC_C1`, `PASSED` |
| Browser/API boundary | Import-boundary tests and production-source search | Feature APIs use shared Spring HTTP boundary; no direct FastAPI/infrastructure request | `VERIFIED_STATIC_C1`, `PASSED` |
| Upload/lifecycle | Upload and lifecycle hook/characterization tests | Automatic `PROCESSING`/`TRANSCRIPT_READY` polling and searchable stop behavior green | `VERIFIED_STATIC_C1`, `PASSED` |
| Search/assistant/citations | Search flow, assistant state, citation order/navigation tests | Green | `VERIFIED_STATIC_C1`, `PASSED` |
| Browser evidence | P3-S5.B4.R1 | Full bounded browser flow passed | `VERIFIED_RUNTIME_EXISTING`, `PASSED` |

P3-S5.B4.R1 retains the exact classification
`P3_S5_B4_R1_BROWSER_PASSED`.

## Cross-repository contract validation

| Boundary | Producer -> consumer | Owner and transport | Source of truth | Idempotency/retry | Evidence |
|---|---|---|---|---|---|
| Upload | Browser -> Spring | Frontend interaction; Spring public HTTP contract | Spring PostgreSQL asset/workspace state; MinIO binary | Client duplicate-submit guard plus Spring transaction/validation | Spring controller/transaction tests; frontend tests; B4.R1 browser |
| Search | Browser -> Spring | Spring public HTTP search API | PostgreSQL authorization/product state; Elasticsearch derived results | Read-only request; lifecycle/search invalidation in frontend | Spring search tests; frontend search tests; B4.R1 |
| Assistant | Browser -> Spring | Spring public HTTP assistant API | Spring-owned authorized bounded context and citation identity | Request identity/stale-response control in frontend; strict Spring response validation | Spring/FastAPI assistant tests; frontend assistant tests; runtime citation passes; B4.R1 |
| Processing request | Spring -> FastAPI | Spring request outbox -> Kafka `asset.processing.requested.v1` -> FastAPI consumer | Spring durable intent/product state | At-least-once relay, deterministic event/task identity, FastAPI idempotent acceptance | Spring/FastAPI golden and relay tests; P3-S2/P3-S4 runtime evidence; B3.R1 |
| Processing result | FastAPI -> Spring | FastAPI result outbox -> Kafka `asset.processing.result.v1` -> Spring listener | FastAPI durable delivery intent; Spring product application state | CAS claim/retry/reconciliation plus Spring consumed-result inbox | FastAPI result tests; Spring listener/inbox tests; outage recovery evidence; B3.R1 |
| Transcript artifact | FastAPI -> Spring | Internal HTTP retrieval referenced by processing result | FastAPI artifact until validated retrieval | Processing-result retry/recovery and inbox state | Spring gateway/result tests; B3.R1 |
| Canonical transcript | Spring internal | Processing-result application -> asset snapshot service | Spring PostgreSQL | Atomic snapshot replacement and consumed-result idempotency | Spring snapshot/transaction tests; default-path runtime |
| Indexing | Spring -> Spring/Elasticsearch | Index request outbox -> Kafka listener -> indexing executor -> Elasticsearch | PostgreSQL indexing/asset state; Elasticsearch derived documents | Fingerprint, job reuse/supersession, outbox/consumer idempotency, begin/finalize recheck | Spring indexing golden/transaction tests; default-path runtime; B4.R1 |
| Citation source identity | Spring -> FastAPI -> Spring -> browser | Internal assistant HTTP with provider aliases; public Spring response | Spring canonical source set | Alias mapping, dedupe, bounds, canonical validation | Spring/FastAPI golden tests; grounded runtime passes; B4.R1 |

No raw payload sample is required to validate this matrix. Event field/value
contracts remain protected by the repository golden tests.

## Runtime integration evidence

Accepted evidence established before C1:

- ten consecutive fresh `project3` default-path successes;
- three controlled application restart cycles and two controlled Kafka restarts;
- no duplicate or current-run stuck terminal product outcomes;
- compatibility rollback through `direct_upload` and restoration to `project3`;
- explicit indexing recovery;
- three grounded assistant/citation passes;
- bounded Kafka heap/container memory and restart baseline;
- a real Kafka outage that exhausted normal publication attempts, produced a
  typed transient failure, observed cooldown, automatically requeued the row,
  and delivered the final product effect without duplication; and
- B3.R1 request -> Celery -> artifact -> result outbox -> Kafka -> Spring ->
  automatic indexing -> `SEARCHABLE` after the FastAPI refactor.

This evidence is `VERIFIED_RUNTIME_EXISTING`; C1 did not repeat it.

## Browser evidence

P3-S5.B4.R1 proved:

- upload through the Spring API;
- lifecycle polling without duplicate request ownership;
- automatic indexing and `SEARCHABLE`;
- search and return-to-context behavior;
- assistant answer and citation rendering/navigation;
- desktop and mobile usability in the tested viewports; and
- no direct browser request to FastAPI.

Classification: `P3_S5_B4_R1_BROWSER_PASSED`.

## Compatibility and recovery evidence

| Surface | Evidence status | Submission decision |
|---|---|---|
| Spring `direct_upload`/compatibility profile | Earlier full rollback drill passed; B3.R1 route metadata, safe warning, and invalid-request behavior passed; B3.R1 full processing not repeated | `RETAINED`, deprecated and functional |
| Explicit indexing | Earlier recovery drill and current tests passed | `RETAINED`, supported recovery |
| Manual/one-shot relay | Unit/static coverage plus B3.R1 bounded no-op | `RETAINED`; not presented as a successful publication drill after B3 |
| Exact-ID processing-result recovery | Spring tests exercise same application use case | `RETAINED`; no C1 runtime replay |
| Failed-outbox reconciliation | Typed classifier/CAS/cooldown/cycle tests; real successful transient Spring recovery | `RETAINED`; unknown/permanent/exhausted rows stay terminal |
| Legacy-session authentication | Current default/profile tests and prior browser/runtime use | `RETAINED`; auth cutover is separate |

No retained path is classified as safe to delete in C1.

## Unverified or partially verified behavior

- FastAPI B3.R1 direct-upload processing was not exercised end to end.
- B3.R1 transient result reconciliation was not driven through a complete
  deterministic controlled cycle; manual relay published no row in its no-op
  check.
- Two concurrent PostgreSQL reconcilers did not directly contend for the same
  eligible row in the final resilience drill.
- A complete three-cycle runtime path to `RECOVERY_EXHAUSTED` was not safely
  exercised.
- Production load, availability, sizing, memory growth, provider quality,
  multilingual quality, internal-service authentication, and security
  certification are not established by local validation.
- Strict Spring Modulith verification is not green; 79 reviewed non-cycle
  exposure messages remain.
- No full Kafka DLQ exists, and abandoned-`publishing` crash-age recovery remains
  deferred.

## Final acceptance criteria

The C1 baseline is accepted only when all of the following remain true:

- the three exact code HEADs in the repository manifest were clean, on `main`,
  and synchronized before validation;
- Spring reports 464/464 passing tests and the 79/0 architecture ratchet;
- FastAPI reports 65/65 passing tests plus successful compile, stable-entrypoint
  import, and both static Compose parses;
- frontend reports 74/74 passing tests plus successful typecheck/build;
- public documents distinguish static, runtime, partial, retained, and deferred
  evidence;
- the Spring documentation-only commit changes no product source, test,
  configuration, schema, dependency, or runtime behavior;
- the local annotated `project3-submission-v1` tag points to the intended final
  HEAD in each repository; and
- no commit or tag is pushed as part of C1.
