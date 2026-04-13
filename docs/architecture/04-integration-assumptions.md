# Integration Assumptions

## Current State

The legacy FastAPI project currently lives in a separate GitHub repository and a separate local folder. It already represents existing work that can be reused for AI and media-processing responsibilities.

## Phase 1 Assumption

For phase 1, the architecture assumes that the FastAPI project will be integrated as an external internal service. It is a dependency of the new system, but it is not the product core.

This assumption implies:

- Spring Boot remains the product-facing backend.
- Spring Boot already owns the current product-side indexing trigger into Elasticsearch.
- FastAPI is called across a service boundary for processing-related responsibilities.
- The architecture should document contracts and ownership boundaries without assuming repository consolidation.

## What This Document Does Not Decide

This document does not decide:

- Repository merge strategy
- Git submodule or subtree usage
- Local development wiring between repositories
- Deployment topology
- Final service-to-service authentication approach

## Deferred Questions

The following questions are intentionally deferred to a later phase:

- How will Spring Boot trigger FastAPI processing: synchronous request, background handoff, callback, polling, or another pattern?
- How will processing artifacts be versioned and validated across service boundaries?
- If transcript handling changes later, which artifacts should remain upstream-owned vs become product-owned data?
- What operational visibility is needed across both services?
- At what point, if any, should repository consolidation be reconsidered?
