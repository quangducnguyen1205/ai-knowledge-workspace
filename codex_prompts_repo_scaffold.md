# Codex Prompts - Repo Scaffold and Docs Setup

## Prompt 1 - Create initial repository scaffold

```text
You are helping me scaffold a serious personal software engineering project.

Project context:
- Working title: AI Knowledge Workspace
- Architecture target:
  - Spring Boot (Java) as the main product core backend
  - Existing FastAPI project reused as an internal AI processing service
  - PostgreSQL for application data
  - Redis for cache / ephemeral state / async support
  - Elasticsearch as the target search layer
- I want a docs-as-code setup in the repository from day one.
- This is NOT a demo-only project. Keep names professional and pragmatic.

Please create the following top-level structure in a monorepo style:

ai-knowledge-workspace/
  README.md
  .gitignore
  docs/
    README.md
    product/
    architecture/
    adr/
    api/
    planning/
  services/
    workspace-core/
    ai-processing-service/
  infra/
    docker/
    scripts/
  .github/
    workflows/

Requirements:
1. Create directories and placeholder .gitkeep files where needed.
2. Create minimal placeholder Markdown files for the docs listed below:
   - docs/product/01-problem-statement.md
   - docs/product/02-product-vision.md
   - docs/product/03-mvp-scope.md
   - docs/architecture/01-system-context.md
   - docs/architecture/02-service-boundaries.md
   - docs/architecture/03-search-architecture.md
   - docs/adr/ADR-001-spring-boot-as-product-core.md
   - docs/adr/ADR-002-fastapi-as-ai-processing-service.md
   - docs/adr/ADR-003-elasticsearch-as-search-layer.md
   - docs/planning/01-sprint-0.md
3. Each Markdown file should contain a clean heading and a short TODO checklist, not fake content.
4. Create docs/README.md as a documentation index.
5. Do not generate business logic or application code yet.
6. Keep file names stable and professional.

At the end, show the resulting tree.
```

## Prompt 2 - Fill the first discovery docs from my locked assumptions

```text
I already have a project scaffold. Please help me draft the first three docs as concise, professional Markdown documents:
- docs/product/01-problem-statement.md
- docs/product/02-product-vision.md
- docs/product/03-mvp-scope.md

Use these locked assumptions:
- Product working title: AI Knowledge Workspace
- Primary user: self-directed learners such as students or junior engineers who consume long-form learning media
- Core pain point: they remember that a concept existed in a lecture video/audio, but cannot quickly find the exact segment again
- Primary MVP medium: lecture videos
- Stretch medium: audio lectures/podcasts if nearly free from existing pipeline
- MVP core value: recover the right knowledge segment from long-form media quickly
- System direction:
  - Spring Boot as product core
  - FastAPI reused as AI processing service
  - Elasticsearch as target search layer
- Phase 1 non-goals:
  - chatbot / assistant UX
  - RAG answer generation
  - Temporal orchestration
  - polished full frontend product
  - multi-modal fusion

Writing requirements:
1. Be specific and practical.
2. Avoid buzzwords.
3. Keep each file short enough to actually maintain.
4. Use bullet points only where useful.
5. Make the docs sound like early-phase real software engineering artifacts.
6. Do not invent unsupported features.
7. Add a small "Open Questions" section to each doc.

After writing the files, show me the diff or file contents.
```

## Prompt 3 - Create an ADR set for the initial architecture decisions

```text
Create concise ADR documents for this project in Markdown:
- ADR-001-spring-boot-as-product-core.md
- ADR-002-fastapi-as-ai-processing-service.md
- ADR-003-elasticsearch-as-search-layer.md
- ADR-004-no-temporal-in-phase-1.md

Use a simple ADR structure:
- Title
- Status
- Context
- Decision
- Consequences
- Alternatives considered

Context assumptions:
- Existing asset: a FastAPI project already supports media upload, transcription, chunking, embeddings, and semantic search.
- New project goal: evolve into an AI Knowledge Workspace with stronger product-core backend design.
- Long-term career goal: backend engineering with Java/Spring.
- Current constraint: student project, limited time, should prioritize realistic scope.

Writing style:
- practical
- explicit trade-offs
- no marketing tone
- no fake certainty
```
