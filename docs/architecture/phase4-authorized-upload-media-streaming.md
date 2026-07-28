# Phase 4 Slice 4A — Authorized Upload Media Streaming

Status: Spring backend contract implemented. The frontend Upload player and browser playback/seek
acceptance remain separate work; Phase 4 is not complete.

## Product boundary

Spring remains the only browser-facing media boundary:

```text
authenticated GET/HEAD
→ Asset media web adapter
→ Asset media application use case
→ owner-scoped Asset lookup and UPLOAD eligibility
→ storage named API
→ S3/MinIO HEAD or exact byte-range GET
```

The browser never receives the persisted bucket, object key, ETag, internal object-storage URL,
credentials or a presigned URL. The endpoint uses the same legacy-session or Keycloak bearer-token
identity resolution as the rest of the Spring product API.

## Contract

`GET /api/assets/{assetId}/media` supports a full response and one standard byte range, including
bounded, open-ended and suffix forms. A satisfiable range returns HTTP `206` with an exact
`Content-Range` and `Content-Length`. Malformed, multiple, overflowing and unsatisfiable ranges
return HTTP `416`; multipart ranges are deliberately deferred.

`HEAD /api/assets/{assetId}/media` runs the same owner/source/object checks and returns full-object
metadata without opening the body. A Range header on HEAD is ignored and the response remains
HTTP `200`.

Both methods return:

```text
Content-Type
Content-Length
Accept-Ranges: bytes
Content-Disposition: inline
Cache-Control: private, no-store
```

The no-store policy prevents a shared or browser cache from becoming an accidental second
authorization boundary. No permissive CORS behavior is added.

## Source and lifecycle policy

- `UPLOAD`: streamable whenever the authorized retained object exists, including while processing
  is pending or failed.
- `YOUTUBE`: no Spring proxy and no redirect; returns `ASSET_MEDIA_NOT_AVAILABLE`.
- deleted/unauthorized/missing Asset: existing owner-safe Asset not-found behavior and no storage
  access.
- zero-length or size-inconsistent upload/object state: unavailable rather than an empty success.

Transcript readiness and searchability are unrelated to media-object availability. A playback
read or client disconnect does not mutate Asset or ProcessingJob state.

Source-aware deletion is unchanged:

```text
UPLOAD  → Elasticsearch cleanup → owned MinIO deletion → database deletion
YOUTUBE → Elasticsearch cleanup → database deletion
```

If deletion races an already opened stream, that response may complete or terminate safely. Later
requests return not found; this slice adds no distributed stream/deletion lock.

## Streaming and storage safety

The storage adapter uses S3-compatible `HEAD Object` for availability/size and `GET Object` with an
exact `bytes=start-end` range for every body response. The web adapter copies at most the resolved
range through an 8 KiB buffer and closes the input stream on success or output failure. It does not
use `readAllBytes`, whole-object `byte[]` buffering or a temporary media file.

Authorization and descriptor resolution finish before the body stream is opened. No database
transaction spans the transfer.

## Delivery scope

Spring proxy streaming is the intentional first authorized local/product contract. A production
CDN or presigned delivery design is deferred and must separately preserve short-lived
authorization, revocation, cache and object-identity confidentiality. This slice does not imply
that such delivery is enabled or preferred.
