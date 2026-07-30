# Phase 9 — Continue Watching

Status: implemented and validated. Continue watching is a bounded read over the playback progress
that Phase 5 already persists; it adds no new table, no second progress owner and no new service.

## Product goal

```text
watch Asset → playback progress is saved → return later
→ Continue watching shows the Asset → open it → the viewer restores the saved position
```

## List contract

```http
GET /api/playback-progress?workspaceId=<uuid>
```

The response is `workspaceIdFilter`, `itemCount`, `maxItems` and `items[]`. Each item carries
`assetId`, `workspaceId`, `assetTitle`, `sourceType`, `positionMs`, `completed` and `updatedAt` —
enough to present the Asset and reopen it, and nothing more.

`workspaceId` is optional; the current user's default Workspace is used when it is omitted. There is
no pagination and no client-controlled limit: the server-owned maximum is **12 items per Workspace**
and is reported as `maxItems`.

## Eligibility and ordering

An Asset is listed when all of the following hold:

- the progress row belongs to the current authenticated user;
- the Asset still exists and belongs to the requested owned Workspace;
- `positionMs > 0`;
- `completed = false`;
- `updatedAt` is present.

Ordering is `updatedAt` descending with `assetId` ascending as a deterministic tie break.

Eligibility deliberately carries **no Asset-status rule**. Playback progress is status independent
by design — Phase 5 established that it is readable and writable in `PROCESSING`, `FAILED`,
`TRANSCRIPT_READY` and `SEARCHABLE`, for both `UPLOAD` and `YOUTUBE` — so the only availability
requirement here is that the Asset still exists inside the owned Workspace. Adding a status gate
would make Continue watching disagree with the progress endpoints that feed it.

Completion is never inferred from an unknown media duration; the persisted `completed` flag is the
only source. Consequently:

- resetting the position to `0` removes the Asset from the list;
- completing it removes the Asset from the list;
- clearing `completed` while the position is still positive brings it back;
- deleting the Asset removes it, because the projection joins to the Asset that still owns the row.

## Ownership and consistency

The list lives inside the existing Asset/playback boundary. Progress is still persisted by the
Phase 5 atomic `INSERT ... ON CONFLICT DO UPDATE` upsert, still last-write-wins, still removed with
its Asset, and still isolated per user. The list is a **pure read**: it never writes, never deletes
and never repairs a row, so opening Explore cannot change stored progress.

Presentation data is projected from current Asset state in the same query rather than stored as a
snapshot, so renaming an Asset or changing its source is visible on the next read. The projection is
one bounded JPQL join, so the list is not an N+1 query, and the web layer receives a response record
rather than a JPA entity.

The frozen `GET`/`PUT /api/assets/{assetId}/playback-progress` contract, its validation, its
concurrency behavior and its save throttle plus final-flush behavior are unchanged.

## Frontend ownership

`GET /api/playback-progress` is mapped by a frontend-owned API module using the shared Spring HTTP
client; the browser calls Spring only. React Query keys the list by Workspace
(`['continue-watching', <workspaceId>]`) and issues no request until a Workspace is selected, so a
Workspace switch can never render the previous Workspace's items.

Nullable fields are normalized defensively: a missing, null or negative `positionMs` becomes `null`,
an unknown `sourceType` becomes `null`, and a blank `updatedAt` becomes `null`. An unusable position
is rendered as `Position unavailable` rather than a misleading `00:00`.

## Continue watching UX

Explore now shows three separate surfaces: `Continue watching`, the search panel, and
`Saved moments`. Continue watching has distinct loading, empty, content and error states, and each
item shows the Asset title, source badge, playback position, last watched time and one
`Continue watching` action.

Opening an item navigates to the ordinary viewer route:

```text
#/assets/:assetId
```

The playback position is deliberately **not** in the URL. Restoring it stays owned by the viewer's
existing playback-progress integration and its resume offer, exactly as accepted in earlier phases.
Opening an item does not autoplay and does not write progress; a write happens only once real
playback activity occurs. Search ranking and grouping, Saved moments, and the separation between the
playback-active row and the search-selected row are all unchanged.

## Product terminology

Visible copy on the surfaces this phase touches uses neutral vocabulary: `Continue watching`,
`Explore`, `Video knowledge workspace`, `Viewer`, `Playback progress`, `Video moment`. Education
framing such as `Continue learning`, `learning progress`, `study session`, `lesson` and `course` is
not used.

The Home screen's recent-video list previously carried the title `Continue watching` while actually
listing the newest Assets by creation date. It is now titled `Recent videos`, so `Continue watching`
means one thing across the product.

`study` remains as a **compatibility name** in internal routes, folders, CSS classes and component
identifiers. Those are not user-visible and were deliberately left alone; renaming them is a
separate, riskier refactor with no product benefit. `PlaybackProgress` keeps its name.

## Not in this phase

- No Course, Lesson, quiz, streak, completion dashboard or assignment.
- No pagination, sorting controls or cross-Workspace continue-watching view.
- No second progress table, progress service or duplicated presentation snapshot.
- No change to search ranking, saved moments, resume behavior or the playback-progress contract.
