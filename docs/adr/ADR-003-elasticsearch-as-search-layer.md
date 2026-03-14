# ADR-003: Elasticsearch as Product Search Layer

## Status

Accepted

## Context

AI Knowledge Workspace is a search-first product. Phase 1 needs a search layer that can support transcript chunk retrieval, metadata filtering, user and workspace scoping, and a path toward hybrid search behavior. The legacy FastAPI system may use FAISS internally, but that should not define the long-term product search contract.

## Decision

Use Elasticsearch as the target product search layer for AI Knowledge Workspace. Treat any FAISS-based search from the legacy system as a transitional or internal detail rather than the public product retrieval contract.

## Consequences

- Product search has a clear target layer aligned with filtering and scoped retrieval needs.
- Search behavior can evolve without binding the product API to legacy FAISS details.
- The system takes on the operational cost of an Elasticsearch dependency.
- The architecture now needs a clear indexing path from processing outputs into searchable documents.

## Alternatives Considered

- Continue using FAISS as the primary product search layer
- Rely on PostgreSQL-only text search
- Expose search directly from the FastAPI service
