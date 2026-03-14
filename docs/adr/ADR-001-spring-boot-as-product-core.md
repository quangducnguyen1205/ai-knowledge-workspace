# ADR-001: Spring Boot as Product Core

## Status

Accepted

## Context

AI Knowledge Workspace needs a clear product core that owns authentication, users, workspaces, assets, authorization, orchestration, metadata, and client-facing APIs. The system also depends on a separate FastAPI service for AI and media processing, so the product core must remain distinct from that processing boundary.

## Decision

Use Spring Boot as the main product core backend for phase 1.

## Consequences

- Product-facing business logic has one clear home.
- The system boundary between product concerns and AI processing stays explicit.
- The architecture supports a professional backend structure without making the FastAPI service the center of the product.
- The project now depends on a cross-service boundary between Spring Boot and FastAPI.

## Alternatives Considered

- Use FastAPI as the main product backend
- Split product-core responsibilities across both services
- Delay choosing a product-core backend until later
