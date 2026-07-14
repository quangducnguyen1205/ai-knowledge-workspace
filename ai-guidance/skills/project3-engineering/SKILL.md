---
name: project3-engineering
description: Long-lived engineering workflow for Project3 source changes, documentation, runtime validation, delivery hardening and future product phases.
---

# Project3 Engineering Skill

## Purpose

Use this skill for Project3 audits, documentation, Spring changes, FastAPI processing
changes, frontend changes, runtime validation, delivery engineering and future product
work. It describes enduring boundaries and points to versioned documents for current facts.
It is not a copy of the architecture documents and must not become a chronological task log.

## Supported task types

Classify the request before editing:

- `AUDIT`: inspect and report; do not mutate files or runtime.
- `DOCUMENTATION`: tracked or private documentation only.
- `STRUCTURAL_REFACTOR`: behavior-preserving source change with frozen contracts.
- `BEHAVIOR_CHANGE`: product/API/schema/profile change requiring an explicit decision.
- `RUNTIME_VALIDATION`: authorized service/browser/provider/database operation with bounded
  fixture, cleanup and evidence classification.
- `DELIVERY_HARDENING`: deployment, CI/CD, security, backup and operational changes.

Do not silently widen one task type into another.

## Repository roles

- Spring product core owns public APIs, authorization integration, workspaces, assets,
  canonical transcript snapshots, PostgreSQL product truth, processing/indexing intents,
  search policy and assistant policy.
- FastAPI is an internal processing/provider service. It owns Kafka request intake, Celery
  execution, media/artifact handling, durable result delivery and the internal provider
  adapter. It does not own public product state.
- Frontend owns browser presentation, routing, upload interaction, lifecycle presentation,
  search, assistant interaction and citation navigation. Browser calls Spring only.

Infrastructure boundaries remain explicit: PostgreSQL is truth, Elasticsearch is derived,
MinIO stores binary/artifacts, Kafka transports integration events, Redis supports cache or
Celery infrastructure, and providers are reached through FastAPI.

## Source hierarchy

When sources disagree, use this order:

1. Executable code, tests and migrations at the current commit.
2. Versioned HTTP/Kafka/data/auth contracts and typed configuration.
3. Tracked architecture, engineering and submission documentation.
4. Accepted runtime/browser evidence with explicit classification.
5. Ignored private notes only when deeper local context is necessary.

The public v1 authority is the final baseline and validation matrix linked from the Spring
documentation index. The `project3-submission-v1` tag is immutable; do not move or recreate
it. Current implementation details must not be promoted to permanent rules without a
decision.

## Normal, compatibility and recovery paths

The normal path is Spring `project3`/`kafka_request` → durable request outbox/Kafka →
FastAPI consumer/Celery → durable result outbox/Kafka → Spring result application →
canonical transcript → automatic indexing → `SEARCHABLE` → Spring search/assistant.

Compatibility and recovery remain functional: direct upload, compatibility profile and
legacy startup alias, FastAPI direct endpoint, explicit indexing, manual/one-shot relay,
exact-ID result recovery and bounded failed-outbox reconciliation. Never present them as
the normal v1 path, and never delete them without a separately authorized removal phase.

## Explore before edit

Before changing code or tracked docs:

1. Read the complete request and identify repository ownership.
2. Inspect status, branch, baseline commit/tag and unexpected files.
3. Read current callers, tests, contracts, transaction annotations and configuration.
4. Build a small implementation packet.
5. Freeze behavior that must not change.
6. List expected files, validation commands, commit boundaries and rollback.

An implementation packet contains: goal, evidence, scope, expected files, frozen contracts,
risks, focused validation, full validation when applicable, and output/commit plan. Do not
create speculative packages, empty checklists or generic abstractions without a real caller.

## Contract freeze

Unless explicitly authorized, preserve public HTTP paths/DTOs/status/error JSON, Kafka
topics/types/versions/keys/identities/payloads/timestamps, database schema/migrations/JPA
mappings, auth defaults, processing profiles, transaction boundaries, retry limits and
compatibility commands. Structural refactors must prove equivalence with characterization
or golden tests.

## Spring invariants

- Spring owns public product policy and PostgreSQL truth.
- Controllers call application use cases, not repositories.
- Cross-module calls use narrow public APIs or consumer-owned ports.
- Entities and repositories stay inside their owning module.
- Modulith cycles must remain zero and the reviewed ratchet must not increase.
- Outbox owns generic envelope/state/relay/recovery; feature codecs own event payloads.
- Elasticsearch writes remain derived and outside database transactions where currently so.
- Flyway is the schema authority; no ad hoc migration system is introduced.
- Preserve upload, result-application and indexing transaction participation.

Read the Spring checklist before Java, migration, architecture or profile work.

## FastAPI invariants

- FastAPI remains internal and browser-inaccessible.
- Kafka request acceptance and Celery handoff remain idempotent.
- Task names, artifact boundaries, result payloads and outbox identity remain stable.
- Result relay/reconciliation uses one durable implementation, with safe diagnostics.
- SQLAlchemy/session ownership and schema initialization remain explicit.
- Stable ASGI, worker, consumer and relay entrypoints stay importable without import-time
  network/database/provider I/O.
- Unit tests must not contact real Kafka, PostgreSQL, Celery, MinIO or Ollama.

Read the FastAPI checklist before processing, provider, result-delivery or Compose work.

## Frontend invariants

- Browser calls Spring through the shared HTTP boundary only.
- `app` owns shell/bootstrap/routing; features own workflows; entities own reusable domain
  behavior; shared/lib remains neutral.
- Upload and lifecycle orchestration are feature-owned.
- Automatic indexing is normal; manual indexing is recovery.
- Assistant/citation state remains Spring-authorized and provider-isolated.
- Preserve auth defaults, routes, API shapes and accessible interaction behavior.

Read the frontend checklist before UI, feature ownership, routing or browser work.

## Async, idempotency and recovery

Kafka is at-least-once transport. Preserve event identity, consumer inbox/idempotency,
outbox attempts and typed failure disposition. Reconcile only eligible transient failures
after cooldown and within bounded cycles. Unknown, permanent, historical ambiguous and
recovery-exhausted rows remain visible and terminal. Do not create an unbounded retry loop,
second publication implementation or automatic replay of ambiguous history.

## Security, privacy and logging

Do not commit secrets, tokens, cookies, private keys, raw authorization headers, payloads,
transcripts or private identifiers. Safe defaults in examples must be clearly local-only.
Logs and persisted diagnostics must be bounded and low-cardinality. Backend authorization
is authoritative; frontend checks are UX only. Internal deployment trust is not a substitute
for a documented auth boundary.

## Validation responsibility

- Docs-only: run link, privacy, diff and scope checks; do not start runtime without need.
- Spring source: focused tests, context/profile tests, architecture tests, then full Maven.
- FastAPI source: py_compile/import checks, focused tests, bounded full unittest suite.
- Frontend source: focused tests, full unit tests, typecheck and production build.
- Runtime/browser: only with explicit authorization, bounded fixture and cleanup plan.

Do not rerun expensive validation merely to create activity; report existing accepted
evidence separately from newly executed commands.

## Codex, Gemini and reviewer responsibilities

The implementation agent explores, edits, validates and reports exact local evidence. A
reviewer or Gemini handoff checks scope, architecture intent, contract preservation and
evidence strength. Neither role may replace missing runtime evidence with prose or weaken a
test to make a task pass.

## Git workflow

Work directly on `main` unless the request explicitly authorizes another workflow. Inspect
status before editing. Make one local commit per coherent scope after validation. Review
staged diff, use Conventional Commits, do not stage private notes or archives, and never
push without explicit authorization. Never reset, restore, stash, clean, rebase or rewrite
history in an ambiguous situation.

## Docker and runtime safety

Do not start/stop/recreate services, build/pull images, clean volumes/networks, publish Kafka,
upload media, invoke providers or mutate databases unless the task explicitly authorizes
that action. `docker compose config` is static; distinguish it from lifecycle commands.

## Documentation and private notes

Tracked docs are English and describe current supported behavior, ownership and evidence.
Private canonical notes are Vietnamese, concise, sanitized and link to public docs. Raw
phase logs belong only in a one-time external archive when genuinely useful. Do not create a
note for every session, and do not use private notes as API/schema authority.

## Delivery and future versions

Project3 v1 is the frozen local integrated product baseline. v1.1 is delivery engineering,
deployment, CI/CD, security and operational hardening. v2 is product evolution, currently
envisioned as persistent multi-asset grounded conversations and evidence feedback.

For delivery work, select a target environment first. Prefer a single-VPS Compose path
before orchestration; require immutable image provenance, secret separation, CI gates,
migrations/backup proof, health checks, rollback and observability. Do not assume Kubernetes
is the first target.

## Stop conditions

Stop and report when a baseline is missing, worktree is unexpectedly dirty, a secret is
detected, a public/data/event/auth contract drifts, a migration or dependency change appears
outside scope, a runtime action needs new authority, or evidence cannot support the claimed
classification. Keep validated work intact while blocked.

## Final report

Include classification, initial/final Git state, exact files, tests and commands actually
run, evidence classifications, commit hashes/messages, remaining limitations and explicit
confirmation of prohibited actions not performed.

## Skill-extension policy

Add a checklist only when a real repeated task needs binary checks. Link to the authoritative
tracked document instead of copying it. Do not add empty templates, sample packages, eval
directories or cloud-specific commands before a concrete deployment/product decision.
