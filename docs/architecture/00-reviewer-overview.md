# Reviewer Overview

## Purpose

This is the fastest backend-oriented entry point for understanding the current Repo B product baseline.

Use it when you want a concise answer to:

- what problem the system solves
- what the backend currently does
- where the service boundaries are
- what the golden demo flow is
- what is intentionally deferred

## Product In One Paragraph

AI Knowledge Workspace is currently a pre-AI, search-first product for recovering the relevant segment from previously consumed lecture video. Spring Boot is the product core and the only product-facing backend boundary. FastAPI remains an internal media/AI processing dependency. Elasticsearch is the product search layer. The system is intentionally not a chatbot, RAG, vector-search, or collaboration platform in the current baseline.

## Current Product Baseline

- Session-based auth through register, login, logout, and `GET /api/me`
- Ownership-aware workspace scope
- Upload through Spring into an internal FastAPI processing flow
- Product-facing processing status
- Local transcript snapshot persistence in PostgreSQL
- Explicit indexing into Elasticsearch
- Lexical search over transcript-row documents and asset title
- Transcript context follow-up from the product-owned transcript snapshot

## Golden Demo Flow

1. Register or log in and establish a Spring session.
2. Read or lazily create the current user's default workspace.
3. Upload a lecture video through Spring.
4. Poll status until processing is terminal.
5. Read the transcript through the local transcript snapshot path.
6. Trigger explicit indexing for that asset.
7. Search for relevant transcript rows.
8. Open transcript context around the chosen search hit.

## Service Boundary Snapshot

- Spring Boot owns the client-facing API, ownership rules, asset/workspace lifecycle, transcript snapshot, explicit indexing trigger, and search contract.
- FastAPI owns internal media-processing behavior such as upload handling, transcription, and task-status/reporting payloads.
- PostgreSQL is the relational system of record for product state.
- Elasticsearch stores derived transcript-row search documents for product retrieval.

## Read This Next

- [System Context](01-system-context.md)
- [Service Boundaries](02-service-boundaries.md)
- [Current Implemented Product Flow](phase1-implemented-product-flow.md)
- [API Summary](../api/API.md)
- [Database](../data/Database.md)
- [Local Development](../runbooks/local-dev.md)
- [Integration Smoke Checklist](../testing/integration-smoke-checklist.md)

## Intentionally Deferred

- Chatbot, assistant, or RAG answer generation
- Hybrid/vector retrieval productization
- Collaboration, sharing, roles, teams, or memberships
- OAuth, JWT, refresh-token, or broader auth-platform work
- Cloud/platform deployment work
- Broad workflow orchestration
