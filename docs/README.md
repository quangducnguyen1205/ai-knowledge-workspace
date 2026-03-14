# Documentation Index

This directory is the docs-as-code home for product thinking, architecture decisions, API notes, and planning artifacts.

## Sections

- `product/`: problem framing, vision, and MVP definition
- `architecture/`: system-level design and service boundaries
- `adr/`: architectural decision records
- `api/`: API contracts and interface notes
- `planning/`: execution plans and early delivery milestones

## Product Documents

- `product/00-discovery-summary.md`: concise discovery baseline for the phase 1 product
- `product/01-problem-statement.md`: why the problem matters and why the current workflow fails
- `product/02-product-vision.md`: target user, value proposition, and product boundaries
- `product/03-mvp-scope.md`: intended first release scope and success criteria

## Architecture Documents

- `architecture/01-system-context.md`: top-level view of product core, processing service, and data dependencies
- `architecture/02-service-boundaries.md`: ownership boundaries across Spring Boot, FastAPI, search, and storage
- `architecture/03-search-architecture.md`: search-first retrieval model and Elasticsearch role
- `architecture/04-integration-assumptions.md`: current assumptions about the separate FastAPI repository and deferred questions

## ADR Documents

- `adr/ADR-001-spring-boot-as-product-core.md`: records Spring Boot as the product core backend
- `adr/ADR-002-fastapi-as-ai-processing-service.md`: records reuse of FastAPI as the internal processing service
- `adr/ADR-003-elasticsearch-as-search-layer.md`: records Elasticsearch as the target product search layer
- `adr/ADR-004-no-temporal-in-phase-1.md`: records the decision to exclude Temporal from phase 1

## TODO

- [ ] Establish documentation review conventions
- [ ] Add contribution guidance for docs updates
- [ ] Link future service-specific documentation
