# Phase 8 — Stable Moment Links And Saved Moments

Status: implemented and validated. Saved moments are an authenticated per-user bookmark of one
canonical transcript row; they are not notes, tags, folders or public sharing.

## Canonical permalink

The existing Asset route is the permalink:

```text
#/assets/:assetId?row=:transcriptRowId
```

A canonical link carries Asset identity and canonical transcript-row identity and nothing else. It
does not require `from=search`, `q`, cached search state, serialized result data or a previously
selected Workspace. `from` and `q` remain optional return-navigation hints written only when the
user arrived from search.

On a cold page load the frontend resolves the Asset, selects its owning Workspace from the
authorized Workspace list, opens the Asset and focuses the exact canonical row while preserving its
timestamp. Nothing autoplays, and opening a link never writes playback progress. An invalid,
deleted or unauthorized link leaves the Asset route and shows a bounded safe state instead of
leaking whether the identifier exists.

Links are authenticated product links. There is no public token, signed URL or anonymous access.

## Ownership and module boundary

Saved Moment is its own Spring Modulith module. It owns its application behavior and its table, and
reaches Asset facts only through one consumer-owned outbound port:

```text
POST/GET/DELETE /api/saved-moments
→ saved-moment web adapter            (HTTP mapping only)
→ saved-moment application service    (validation, ownership, orchestration)
→ SavedMomentAssetPort                (saved-moment-owned contract, exposed as a named interface)
→ SavedMomentAssetPortAdapter         (implemented inside the Asset module)
→ authorized Asset lookup + canonical transcript rows
→ saved-moment store port → JPA persistence adapter
```

The Asset module implements the port, so the dependency arrow is Asset → Saved Moment only and no
Modulith cycle exists. Saved Moment never sees an Asset repository or JPA entity, and the Spring
Data repository stays package-private behind the application-owned store port.

Asset deletion calls `SavedMomentAssetCleanupUseCase.deleteForAsset` inside the existing
product-truth deletion transaction, and the database foreign key cascades as defence in depth.

## Persisted model

Only canonical identity and ownership are stored: saved-moment ID, user, Workspace, Asset,
canonical transcript-row ID and the saved timestamp. No AI summary, note, tag, folder, ranking
score, originating query, playback position or copied context snapshot is persisted.

Presentation data — Asset title, source type, segment index, timing and text — is resolved from
current canonical Asset state on every read. A transcript edit is therefore reflected immediately,
and a saved moment can never render a stale snapshot.

## Authorization and stale-row policy

- One saved record per user, Asset and transcript row; the unique constraint is the concurrency
  boundary, so concurrent duplicate saves converge on one row and repeated saves are idempotent.
- Saving validates current ownership and canonical row existence before any write.
- A foreign Workspace, a foreign or unknown Asset, an unknown canonical row and a foreign
  `savedMomentId` all produce the same bounded `404`, so the API never reveals which identifier
  exists.
- The client cannot supply a Workspace ID when saving; it is resolved from Asset ownership.
- A saved moment whose canonical row no longer exists is omitted from the list rather than returned
  as a navigable link. Reading never writes, so a `GET` does not delete anything.
- The canonical row ID is authoritative. A stale supplied ID never falls back to a segment index and
  is never silently redirected to another row. Rows that never received a stored ID keep the
  existing `segment-<index>` identity convention, which is an identity rule rather than a fallback.
- Asset deletion removes the saved moments of that Asset, so no usable orphan remains.

## Bounded reads

`GET /api/saved-moments?workspaceId=…` returns the newest `100` saved moments of the current user in
that Workspace, ordered by saved time descending with the saved-moment ID as a deterministic tie
break. There is no pagination in this phase; the maximum is server-owned and reported as `maxItems`.

Canonical data for the whole page is resolved in one batched port call grouped by Asset, so the list
never becomes an N+1 query. Slice 7.4's `contextSnippet` is intentionally not reused here: the
matching row `text` is sufficient for Phase 8 and reusing the hydration path would duplicate
architecture for no product gain.

## Frontend behavior

Explore gains a `Saved moments` section with distinct loading, empty, content and error states. Each
item shows the Asset title, source badge, timestamp, canonical row text, saved time and three
actions: `Open moment`, `Copy link` and `Remove`.

`Save moment` / `Saved` lives on the selected-moment surface in the Asset viewer, outside every
result button, so no interactive control is nested inside another. Saved-moment queries are keyed by
Workspace ID, so changing Workspace can never display the previous Workspace's saved moments.
Failed mutations keep the list usable and show bounded feedback; a clipboard failure does the same.

Search ranking, grouping, adjacent deduplication, canonical context snippets and playback behavior
are unchanged. Playback-active and search-selected transcript states remain separate.

## Not in this phase

- No notes, tags, folders or collections.
- No public, anonymous or token-signed sharing.
- No pagination, sorting controls or cross-Workspace saved-moment view.
- No search-ranking, playback or assistant change.
