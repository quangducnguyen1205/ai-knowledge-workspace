# Service Boundaries

## Boundary Summary

Phase 1 separates product logic from AI/media processing. Spring Boot is the product core. FastAPI is an internal processing service. Elasticsearch is the search layer. PostgreSQL is the domain data store.

## Spring Boot Product Core

### Owns

- Authentication and user identity
- Workspace model and workspace-scoped access rules
- Asset registration and product-visible asset metadata
- Authorization
- Product orchestration across services
- Client-facing APIs
- Client-facing search API and result shaping

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

- Search-optimized storage for transcript chunks and related search documents
- Filtered retrieval across workspace and asset metadata
- Search behavior aligned with future hybrid search direction

### Should Not Own In Phase 1

- Domain system of record responsibilities
- Business logic
- User or workspace authority
- Media processing

## PostgreSQL

### Owns

- Domain metadata for users, workspaces, assets, and related product entities

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
- Elasticsearch supports product retrieval, but access control and business rules remain in Spring Boot.
