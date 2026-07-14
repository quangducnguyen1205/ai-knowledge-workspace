# Project3 Spring product-core agent entrypoint

Read the central [Project3 engineering skill](ai-guidance/skills/project3-engineering/SKILL.md)
before editing. The public v1 authority is
[`docs/submission/project3-final-baseline.md`](docs/submission/project3-final-baseline.md)
and its validation matrix.

This repository owns Spring public APIs, authorization integration, workspace/asset state,
canonical transcript snapshots, PostgreSQL truth, processing/indexing policy, search and
assistant policy. PostgreSQL is truth; Elasticsearch is derived.

Before Java, migration, architecture or profile work, read:

- `ai-guidance/skills/project3-engineering/checklists/change-safety.md`
- `ai-guidance/skills/project3-engineering/checklists/spring-product-core.md`

Canonical validation is `mvn -q -f services/workspace-core/pom.xml test`. Do not change
schemas, APIs, Kafka contracts, auth defaults, compatibility paths or transaction boundaries
without an explicit task. Do not start services, publish Kafka, upload media, invoke a
provider, mutate a real database or use Docker lifecycle commands unless authorized.

Inspect `git status` first, work on `main`, create local Conventional Commit commits only
after validation, never stage `.local-notes`, and do not push without explicit authorization.
