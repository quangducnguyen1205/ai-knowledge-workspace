# Service Boundaries

## Boundary Summary

Phase 1 separates product logic from AI/media processing. Spring Boot is the product core. FastAPI is an internal processing service. Elasticsearch is the search layer. PostgreSQL is the domain data store.

## Spring Boot Product Core

### Currently Owns In This Repo Phase

- Workspace model and workspace-scoped access rules
- Asset registration and product-visible asset metadata
- Product orchestration across services
- Client-facing APIs
- Client-facing search API and result shaping
- Product-facing transcript reads and transcript-context responses
- Explicit transcript indexing into Elasticsearch

### Not Implemented Yet In This Repo Phase

- Authentication and user identity
- Authorization and workspace ownership enforcement

### Should Not Own In Phase 1

- Transcription
- Chunking
- Embedding generation
- Media-processing internals
- Direct public exposure of legacy search mechanics

## FastAPI AI Processing Service

### Owns

- Media ingestion for processing
- Transcription
- Transcript chunk generation
- Embedding generation
- Processing status and processing result payloads

### Should Not Own In Phase 1

- Authentication or user management
- Workspace ownership rules
- Authorization decisions
- Product-facing business logic
- Public product API surface
- Long-term product search contract

## Elasticsearch Search Layer

### Owns

- Search-optimized storage for transcript-row search documents and related search metadata
- Filtered retrieval across workspace and asset metadata
- Product search retrieval over indexed transcript text

### Should Not Own In Phase 1

- Domain system of record responsibilities
- Business logic
- User or workspace authority
- Media processing

## PostgreSQL

### Owns

- Domain metadata for workspaces, assets, processing jobs, and related product entities

### Should Not Own In Phase 1

- Primary search retrieval behavior
- Embedding or transcript-processing concerns

## Redis

### May Be Used For

- Cache
- Ephemeral coordination state
- Short-lived support data

### Should Not Own In Phase 1

- Durable domain records
- Search index responsibilities
- Workflow-engine responsibilities

## Boundary Rules

- Spring Boot is the only product entry point for clients.
- FastAPI may produce artifacts that support search, but it does not define the client-facing search contract.
- Elasticsearch supports product retrieval, but business rules remain in Spring Boot.
- Authentication and authorization are intentionally out of scope in the current implemented slice.
