# Project3 Documentation

Single entrypoint for the Project3 documentation set. Everything current is linked from this page.
Anything not linked here is historical.

## Start here

| Question | Document |
|---|---|
| What is this product and what problem does it solve? | [Product overview](#product-overview) |
| What actually exists, and where does each thing live? | [`architecture/system-inventory.md`](architecture/system-inventory.md) |
| Why is it built this way, and what is wrong with it? | [`architecture/architecture-review.md`](architecture/architecture-review.md) |
| How do I run it? | [`runbooks/deployment.md`](runbooks/deployment.md) |
| What are the endpoints? | [`api/API.md`](api/API.md) |
| What is in the database? | [`data/Database.md`](data/Database.md) |
| How is it verified? | [`testing/integration-smoke-checklist.md`](testing/integration-smoke-checklist.md) |
| What does it *not* do? | [`known-limitations.md`](known-limitations.md) |

Shortest useful path for a new reader: **system inventory → architecture review → deployment
runbook**. Three documents, and you can run and explain the system.

## Current documentation

### Product overview

- [`product/01-problem-statement.md`](product/01-problem-statement.md) — why the problem matters
- [`product/02-product-vision.md`](product/02-product-vision.md) — target user, value, boundaries
- [`product/03-mvp-scope.md`](product/03-mvp-scope.md) — first-release scope and success criteria
- [`product/00-discovery-summary.md`](product/00-discovery-summary.md) — discovery baseline

### Architecture

- [`architecture/system-inventory.md`](architecture/system-inventory.md) — **authoritative
  current state**: components, ownership, boundaries, flows, truth versus derived, ports,
  required versus optional dependencies
- [`architecture/architecture-review.md`](architecture/architecture-review.md) — critical review;
  findings classified as accepted strength / known trade-off / technical debt / future scaling
  trigger
- [`architecture/backend-modularity-baseline.md`](architecture/backend-modularity-baseline.md) —
  module ownership, API/port rules, package convention, architecture gate
- [`architecture/00-reviewer-overview.md`](architecture/00-reviewer-overview.md) — short reviewer
  onboarding note
- [`architecture/05-end-to-end-diagram-pack.md`](architecture/05-end-to-end-diagram-pack.md) —
  topology, flow, write, search and state-transition diagrams
- [`architecture/03-search-architecture.md`](architecture/03-search-architecture.md) — retrieval
  model and the Elasticsearch role
- [`architecture/deprecations.md`](architecture/deprecations.md) — removed surfaces and retained
  recovery decisions

### Deployment and operations

- [`runbooks/deployment.md`](runbooks/deployment.md) — **authoritative**: topology, prerequisites,
  startup and shutdown, environment template, volumes, resource policy, health checks, build
  identity, authentication modes, failure diagnosis, validation commands
- [`runbooks/local-dev.md`](runbooks/local-dev.md) — day-to-day local development and reset rules

### API and data

- [`api/API.md`](api/API.md) — product-facing Spring API
- [`data/Database.md`](data/Database.md) — PostgreSQL persistence summary

### Testing

- [`testing/integration-smoke-checklist.md`](testing/integration-smoke-checklist.md) — manual smoke
  checklist
- Automated gateways and integration profiles are listed in
  [`runbooks/deployment.md`](runbooks/deployment.md#validation-commands)

### Handoff

- [`portfolio/backend-engineer-portfolio.md`](portfolio/backend-engineer-portfolio.md) — using
  Project3 in a Backend Engineer application
- [`demo/demo-script.md`](demo/demo-script.md) — bounded 5–8 minute live walkthrough, with a
  fallback for when media processing is unavailable
- [`thesis/thesis-handoff.md`](thesis/thesis-handoff.md) — four research directions with research
  questions, hypotheses, variables, baselines, corpora, designs, threats to validity and the fork
  changes each requires
- [`known-limitations.md`](known-limitations.md) — consolidated limitations and what is *not*
  claimed

### Decision records

- [`adr/ADR-001-spring-boot-as-product-core.md`](adr/ADR-001-spring-boot-as-product-core.md)
- [`adr/ADR-002-fastapi-as-ai-processing-service.md`](adr/ADR-002-fastapi-as-ai-processing-service.md)
- [`adr/ADR-003-elasticsearch-as-search-layer.md`](adr/ADR-003-elasticsearch-as-search-layer.md)
- [`adr/ADR-004-no-temporal-in-phase-1.md`](adr/ADR-004-no-temporal-in-phase-1.md)
- [`project3-architecture/technology-rationale.md`](project3-architecture/technology-rationale.md)

## Phase records

Each phase document records the decisions, contract and evidence for one delivered slice. They stay
accurate for their own scope; where one disagrees with the system inventory, the inventory wins.

| Phase | Document |
|---|---|
| 1 | [`architecture/phase1-timestamp-aware-transcript-foundation.md`](architecture/phase1-timestamp-aware-transcript-foundation.md), [`architecture/phase1-implemented-product-flow.md`](architecture/phase1-implemented-product-flow.md) |
| 2 | [`architecture/phase2-source-aware-asset-foundation.md`](architecture/phase2-source-aware-asset-foundation.md) |
| 4 | [`architecture/phase4-authorized-upload-media-streaming.md`](architecture/phase4-authorized-upload-media-streaming.md) |
| 5 | [`architecture/phase5-user-playback-progress.md`](architecture/phase5-user-playback-progress.md) |
| 7 | [`architecture/phase7-search-quality-baseline.md`](architecture/phase7-search-quality-baseline.md) |
| 8 | [`architecture/phase8-saved-moments.md`](architecture/phase8-saved-moments.md) |
| 9 | [`architecture/phase9-continue-watching.md`](architecture/phase9-continue-watching.md) |
| 10 | [`architecture/system-inventory.md`](architecture/system-inventory.md), [`architecture/architecture-review.md`](architecture/architecture-review.md), [`runbooks/deployment.md`](runbooks/deployment.md), and the handoff documents above |

## Historical

Kept for reasoning and provenance, not as a description of the current system. Each carries a banner
pointing at its replacement.

- `submission/project3-final-baseline.md`, `submission/project3-validation-matrix.md` — pre-Phase-1
  baseline and validation matrix
- `planning/deployable-demo-baseline.md` — original run-mode decision, superseded by the deployment
  runbook
- `planning/01-sprint-0.md`, `planning/sprint-1-thin-slice.md`,
  `planning/phase-1-closure-summary.md`, `planning/phase-2-ownership-foundation.md`,
  `planning/roadmap-transition-phase1-to-phase2.md` — delivery notes
- `architecture/pre-phase1-architecture-overhaul.md`, `architecture/01-system-context.md`,
  `architecture/02-service-boundaries.md`, `architecture/04-integration-assumptions.md` —
  superseded in current-state terms by the system inventory
- `project-history/project2-to-project3-evolution.md` — engineering history from Project2 to
  Project3
