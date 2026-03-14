# ADR-002: Reuse FastAPI as AI Processing Service

## Status

Accepted

## Context

The project already has a legacy FastAPI codebase in a separate repository and local folder. That codebase aligns with processing concerns such as ingestion, transcription, chunking, embeddings, and processing result/status handling. Phase 1 should stay narrow and avoid unnecessary rewrites.

## Decision

Reuse the existing FastAPI system as an internal AI processing service rather than rebuilding those capabilities inside the Spring Boot product core.

## Consequences

- Phase 1 can build on existing processing work instead of starting from zero.
- The system keeps a clean separation between product logic and processing logic.
- The architecture must manage a service contract between two codebases.
- FastAPI remains a dependency of the product, not the product core itself.

## Alternatives Considered

- Reimplement processing capabilities inside Spring Boot for phase 1
- Make FastAPI the main product backend
- Decide on repository consolidation now
