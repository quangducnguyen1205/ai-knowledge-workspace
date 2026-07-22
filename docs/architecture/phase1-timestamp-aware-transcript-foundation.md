# Phase 1 — Timestamp-Aware Transcript Foundation

Status: implemented in the Spring product core, FastAPI processing repository, and frontend
client models. This document records the verified starting contracts and the focused changes.
It does not claim playback behavior.

## Verified starting contracts

| Location | Starting behavior | Classification before Phase 1 |
|---|---|---|
| FastAPI `services.video_processing.transcribe_audio_with_whisper` | Whisper returned a provider result containing `segments[].start`/`end` in seconds, but the helper returned only full text | timing existed and was dropped |
| FastAPI `WhisperProcessingTranscriptionProvider` | split full text into deterministic rows; provider segment timing did not survive | timing was dropped |
| FastAPI `ProcessingTranscriptRow` and `ProcessingRequestTranscript` | nullable `start_seconds`/`end_seconds` existed, but the active provider returned strings and the application reconstructed rows without timing | fields existed but normal execution populated null |
| FastAPI internal artifact endpoint | returned `id`, `video_id`, `segment_index`, `text`, `created_at` | contract extension required |
| FastAPI result event | `transcript.ready` v1 carried correlation, status, count and completion time only | unchanged; transcript rows do not belong in Kafka |
| Spring FastAPI wire and processing row | stopped at `segmentIndex`, text and creation metadata | contract extension required |
| Spring canonical transcript and PostgreSQL | no media-time columns | V2 migration required |
| Spring indexing/search/assistant | no timing in ports, documents, hits or public results | propagation required |
| Frontend transcript/search/citation models | directly cast payloads without timing normalization | additive types and compatibility mapping required |

Whisper is the only provider-time source in scope. FastAPI owns conversion to integer
milliseconds; Spring and the frontend never observe floating-point seconds.

## End-to-end impact map

```text
Whisper result segments[start,end seconds]
  -> FastAPI normalize_whisper_result[start_ms,end_ms]
  -> ProcessingTranscriptRow
  -> processing_request_transcripts.start_ms/end_ms
  -> GET /internal/.../transcript-rows (snake_case)
  -> FastApiTranscriptRowResponse
  -> processing.api.ProcessingTranscriptRow
  -> ProcessingResultAssetPortAdapter
  -> AssetTranscriptRowInput / AssetTranscriptRowView
  -> asset_transcript_rows.start_ms/end_ms
  -> IndexingTranscriptRow / IndexingRequestRow
  -> TranscriptIndexDocument
  -> Elasticsearch startMs/endMs long fields
  -> TranscriptSearchHit / SearchHit
  -> SearchResultResponse (camelCase)
  -> canonical AssistantTranscriptSegment
  -> AssistantContextSource / AssistantAnswerCitation
  -> assistant HTTP responses (camelCase)
  -> frontend feature API normalizers and internal models
```

| Boundary | Producer | Consumer | Unit/nullability |
|---|---|---|---|
| Whisper -> FastAPI | `segments[].start`, `segments[].end` | `normalize_whisper_result` | finite seconds -> rounded integer ms; both nullable |
| FastAPI store -> HTTP | `ProcessingRequestTranscript.start_ms/end_ms` | `ProcessingTranscriptRowRead` | integer ms; both nullable; snake_case |
| FastAPI HTTP -> Spring | internal artifact JSON | `FastApiTranscriptRowResponse` | `start_ms`/`end_ms` mapped to nullable `Long` |
| Processing -> asset | `ProcessingTranscriptRow` | `AssetTranscriptRowInput` | nullable `Long`; no wire/JPA type crossing |
| Asset -> PostgreSQL | `AssetTranscriptRowInput` | `AssetTranscriptRowSnapshot` | nullable BIGINT with paired validity constraint |
| Canonical -> indexing | `AssetTranscriptRowView` | `IndexingTranscriptRow`/`IndexingRequestRow` | nullable `Long` |
| Indexing -> Elasticsearch | `TranscriptIndexDocument` | JSON document/mapping | nullable JSON numbers; mapping type `long` |
| Elasticsearch -> search | `_source.startMs/endMs` | `TranscriptSearchHit` then `SearchResultResponse` | nullable `Long`; camelCase HTTP |
| Canonical source -> assistant | `AssistantTranscriptSegment` | `AssistantContextSource` then citations | canonical nullable `Long`; provider does not author it |
| Spring HTTP -> frontend | camelCase public fields | feature API payload types/normalizers | missing or null -> internal null; zero preserved |

## FastAPI normalization and artifact compatibility

`seconds_to_milliseconds` is the single conversion function. It rejects booleans, non-numbers,
NaN, infinity and negative values. `normalize_whisper_result` rejects partial pairs, backwards
ranges, malformed segments and blank structured-segment text. A provider result without
structured segments retains the prior text chunker and produces null timing rather than
fabricating it.

The processing artifact table now uses `start_ms`/`end_ms`. Fresh metadata creates both BIGINT
columns and `ck_processing_request_transcript_timing`. The idempotent existing-schema upgrader
adds the new nullable columns and, on PostgreSQL, the named constraint under the existing schema
initialization lock. It does not drop historical columns or backfill values. Existing artifact
rows therefore remain readable with null timing.

The `transcript.ready` and `asset.processing.failed` v1 Kafka envelopes and payload fields are
unchanged. Exact-field contract tests continue to prevent transcript rows from entering result
events.

## Spring boundary proof

- `integration.fastapi.adapter.out.provider.processing` owns snake_case transport mapping.
- `TranscriptArtifactGatewayAdapter` maps transport rows once into the processing-owned
  `ProcessingTranscriptRow` record.
- `TranscriptArtifactValidator` owns paired/null/non-negative/ordered timing validation before
  the processing result changes product state.
- `ProcessingResultAssetPortAdapter` crosses the named processing-to-asset API and creates an
  asset application input; it does not pass a provider DTO or JPA entity.
- `CanonicalTranscriptPersistenceAdapter` is the only timing mapper into/out of
  `AssetTranscriptRowSnapshot`.
- `SearchAssetPortAdapter` exposes an indexing-owned view; search imports neither asset JPA nor
  FastAPI wire types.
- `SearchController`, `AssetController`, `AssistantContextController` and
  `AssistantAnswerController` perform the final HTTP response mapping.
- `AssistantProviderSource` is deliberately unchanged. `AssistantContextApplicationService`
  resolves the matched canonical transcript row and its timing before the provider call;
  `AssistantAnswerApplicationService` accepts only validated source aliases and rebuilds public
  citations from those canonical sources.

The processing-result transaction still validates the complete artifact, replaces the canonical
snapshot, updates job/asset/inbox state and creates indexing intent atomically. Kafka
acknowledgment, retry and duplicate-result semantics are unchanged. Elasticsearch remains outside
the database begin/finalize transactions.

## Product database migration

`V1__create_product_schema.sql` is unchanged. `V2__add_transcript_timing.sql` adds only:

```sql
start_ms BIGINT NULL,
end_ms BIGINT NULL,
CONSTRAINT ck_asset_transcript_rows_timing CHECK (
  (start_ms IS NULL AND end_ms IS NULL)
  OR (start_ms IS NOT NULL AND end_ms IS NOT NULL
      AND start_ms >= 0 AND end_ms >= start_ms)
)
```

There is no backfill. Stable `rowId`, source identity, `segmentIndex`, ordering and uniqueness
constraints remain unchanged.

## Fingerprint strategy

The pre-Phase-1 SHA-256 input for an all-null-timing row is byte-for-byte unchanged. The golden
two-row value remains:

```text
4eadbca95e55585c1a3268fa19b6db8edf2f8e830dc83104aa07912e7b175315
```

When timing is present, the encoder appends a timing marker (`0x1e`) and length-delimited,
big-endian 64-bit start/end values after the existing segment index and UTF-8 text fields.
Changing either boundary changes the fingerprint. No null marker is appended for legacy rows,
which is what preserves historical fingerprints.

## Elasticsearch mapping convergence

A newly created transcript index receives `startMs` and `endMs` with type `long` in its full
mapping. An existing index receives an idempotent `PUT /{index}/_mapping` for the two additive
fields. An index-creation race rechecks existence and then applies the same update. No index is
deleted, recreated or automatically reindexed. Legacy documents without the fields parse as
null and remain valid.

## Frontend preservation

The internal `TranscriptRow`, `SearchResult` and `AssistantAnswerCitation` types require
`startMs: number | null` and `endMs: number | null`. Their wire payload variants allow missing
fields only at the API boundary. Feature API mappers normalize missing values with `?? null`, so
`0` is preserved. React Query caches and feature selection state retain the normalized objects;
no player, seek, auto-scroll or timestamp-rendering state was introduced.

## Automated validation

Focused tests cover:

- FastAPI conversion/rounding, zero/null/invalid ranges, persistence, internal endpoint JSON,
  legacy artifacts, schema constraints and unchanged result-event fields;
- Spring wire mapping, artifact validation, processing application, canonical persistence,
  V1->V2 migration, database constraints, golden/timing fingerprints, index mapping convergence,
  bulk documents, hit parsing, transcript/search/assistant HTTP responses, canonical citation
  trust and architecture rules;
- frontend zero/null/legacy normalization, transcript/search/context/citation preservation,
  state-driven workflows and unchanged rendering.

Repository-wide validation at implementation time produced:

- FastAPI: 79 unit/integration tests passed; application bytecode compilation and both generic
  and Project3 Compose configuration validation passed;
- Spring: the canonical Maven suite passed 380 tests, including Flyway V1->V2, H2 constraint,
  persistence, Elasticsearch mapping, API and architecture tests;
- frontend: TypeScript typecheck, 124 Vitest tests and the Vite production build passed.

Full media/Whisper/Kafka/PostgreSQL/Elasticsearch/browser runtime evidence is separate from this
automated evidence and must not be inferred from it. Read-only Compose inspection found no
running Spring, FastAPI or frontend project containers. The implementation did not start or reset
the user's stacks, create product data, download a Whisper model, or fabricate an authenticated
browser session. Consequently runtime Case A and Case B are blocked before the upload/Whisper
boundary; no Phase 1 E2E runtime claim is made here.

## Remaining limitations and non-goals

- The deprecated standalone FastAPI direct-upload compatibility table/API remains text-only; the
  normal Spring/Kafka processing artifact path is the Phase 1 timing path.
- Legacy rows and documents have null timing; no data is synthesized.
- Phase 1 adds no playback, seek, active-row, synchronization, editing, YouTube ingestion,
  diarization or word-level timing.
- Elasticsearch mapping convergence does not backfill or reindex existing documents; their
  timing remains absent until a normal canonical reindex writes it.
- Runtime evidence requires the local multi-service environment and a real media fixture; absent
  such an execution, only automated contract/build evidence may be claimed.
