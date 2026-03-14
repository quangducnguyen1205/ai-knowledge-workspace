# ADR-004: No Temporal in Phase 1

## Status

Accepted

## Context

Phase 1 is intentionally narrow and should remain realistic for a student-built system. The architecture already includes multiple core dependencies and a cross-service boundary between Spring Boot and FastAPI. Introducing Temporal would add orchestration infrastructure and operational complexity before the product has demonstrated a need for it.

## Decision

Do not include Temporal in phase 1.

## Consequences

- The phase 1 architecture stays simpler and easier to reason about.
- Operational overhead is reduced.
- Orchestration must remain modest and handled without a dedicated workflow engine.
- If processing workflows become more complex later, this decision may need to be revisited.

## Alternatives Considered

- Introduce Temporal in phase 1
- Add another workflow engine in phase 1
- Design phase 1 around long-running orchestration infrastructure from the start
