# Roadmap Transition: Phase 1 to Phase 2

Historical note: this captures the transition point between Phase 1 and early Phase 2. It should be read as a roadmap checkpoint, not as the current product state or current backend contract.

## Phase 1 Closed

Phase 1 is closed at a practical level.

The project has a working search-first MVP with a Spring-owned backend flow and a demo-focused frontend that consumes only the Spring product API. This transition note exists to separate Phase 1 closure from future planning so that deferred items are not mistaken for incomplete Phase 1 work.

## Deferred Items from Phase 1

### Deferred by Design

- auth, user ownership, and collaboration
- transcript persistence
- chatbot/RAG features
- background retries, schedulers, orchestration, or workflow engines
- advanced search tuning and broader ranking/filter work
- richer asset-management capabilities beyond the current narrow slice
- production-grade frontend polish and broader navigation/media UX

### Record-Only Notes

- `processingJobStatus` and `assetStatus` are intentionally related but not identical product signals.
- At that transition point, transcript reads, transcript context, and indexing still relied on fetch-on-demand from FastAPI.
- Elasticsearch remains an external index, so some failure-path concerns are operational notes rather than Phase 1 blockers.
- Existing smoke and lightweight test coverage are good enough for the MVP slice, but they are not a substitute for later broader integration hardening if the project scope grows.

## Candidate Phase 2 Tracks

- Decide whether transcript data should remain fetch-on-demand or become product-owned persisted data in a later slice.
- Decide whether the product should continue as a narrow search-first workspace or expand toward richer knowledge-workspace behaviors.
- Evaluate which operational hardening items are worth doing only after a clear Phase 2 product direction exists.
- Evaluate whether the current frontend demo shell should stay intentionally narrow or grow into a more complete application surface.

These are candidate tracks for selection, not approved implementation work.

## Phase 2 Entry Rule

Phase 2 should start only after one candidate track is chosen and framed as a small decision or implementation slice with explicit non-goals.

Do not treat generic cleanup, deferred-by-design items, or broad "hardening" as the default Phase 2 start.
