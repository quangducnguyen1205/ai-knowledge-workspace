# Implemented Product Flow After Timestamp-Aware Transcript Phase 1

Status: current Spring-side product behavior. Timestamp metadata is preserved as additive,
nullable integer milliseconds without changing transcript identity or playback behavior.

## Public boundary

The frontend uses Spring `/api/...` endpoints for authentication, workspaces, assets, lifecycle,
transcript context, search and assistant behavior. FastAPI is internal and never becomes a browser
product API.

Upload response fields remain `assetId`, `processingJobId`, `assetStatus` and `workspaceId`.
Asset, workspace and search controllers map application views/results to web response records;
they do not serialize JPA entities.

## Product command flow

1. Spring resolves the current user and authorized/default workspace.
2. The upload application service validates media metadata and writes the object to MinIO.
3. One product transaction persists asset metadata, processing job and version-1 request outbox.
4. The request relay publishes the existing Kafka request contract outside the database
   transaction.
5. FastAPI processes the object and publishes a version-1 terminal result.
6. Spring deduplicates by result event ID and correlates the result with the stored processing
   request event ID.
7. For success, Spring retrieves and validates the complete artifact row set and atomically
   replaces the canonical transcript while updating asset/job/inbox state.
8. The same successful product transaction creates derived-index intent.
9. Index execution writes Elasticsearch outside its begin/finalize DB transactions and marks the
   asset searchable only after rechecking current product state.

The old Spring direct FastAPI processing, upstream status-polling and transcript load-or-capture
paths have been removed. Reads are side-effect free.

## Canonical transcript

`asset_transcript_rows` is PostgreSQL truth. Each row has stable row identity, asset, source video
identity, segment order, nullable `start_ms`/`end_ms`, text and creation metadata. A row has either
both timing values null, or both present with `start_ms >= 0` and `end_ms >= start_ms`.

Canonical replacement invariants:

- validate the full artifact before changing product state;
- unique ordered segment indexes within an asset;
- replace the snapshot in the processing-result transaction;
- create indexing intent from the stable replacement;
- never treat Elasticsearch or FastAPI scratch state as canonical.

## Ownership

Workspace queries are owner-scoped. Assets inherit ownership through their workspace; application
policy resolves authorized asset/workspace state before returning product data. Search and
assistant operate on authorized, searchable assets. Unknown or non-owned identifiers map to the
same public not-found behavior.

## Failure and recovery

- object-storage upload happens before the product transaction; a later failure triggers
  best-effort deletion;
- known artifact/application failures create a bounded durable failed inbox record;
- unexpected processing-result failures roll back and remain eligible for Kafka redelivery;
- duplicate result IDs do not reapply product effects;
- indexing failures remain retryable derived-state work;
- asset deletion completes external cleanup before deleting product truth.

Exact-ID result/outbox recovery and explicit indexing remain supported operator controls. There
is no broad alternate processing path.

## Phase 1 result

Timing now crosses the FastAPI wire adapter, processing-owned artifact row, asset-owned canonical
row, PostgreSQL, search indexing ports, Elasticsearch, search responses, assistant canonical
citation resolution and browser-facing responses. Web adapters still own HTTP mapping; the
provider-facing assistant source does not carry authoritative timing. Kafka v1 payloads,
transaction boundaries and duplicate-result behavior are unchanged.
