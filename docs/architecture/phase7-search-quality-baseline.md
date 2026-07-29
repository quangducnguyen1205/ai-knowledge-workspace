# Phase 7 Search Quality Baseline

## Purpose And Scope

Slice 7.1 characterizes the current Spring-owned lexical search behavior without changing
production ranking. The baseline answers which canonical transcript rows the current mapping,
query adapter and Java relevance policy return, and in which order, for representative
Workspace video searches.

FastAPI is not involved. PostgreSQL remains the product and authorization authority;
Elasticsearch remains derived state.

## Versioned Corpus Ownership

The versioned test inputs are:

- `services/workspace-core/src/test/resources/search-quality/v1/corpus.json`
- `services/workspace-core/src/test/resources/search-quality/v1/expected-baseline.json`

The corpus uses stable Workspace, Asset and transcript-row IDs plus fixed timing and creation
metadata. Version `v1` contains `102` transcript-row documents across two Workspaces and ten
Assets. It covers exact phrases, bag-of-words retrieval, adjacent duplicates, one-Asset
candidate dominance, deterministic ties, short queries, English, accented and unaccented
Vietnamese, optional Asset scope and Workspace isolation.

Changes to corpus semantics or accepted ordering require a new reviewable corpus version or an
explicitly justified baseline update. Do not edit expected order merely to make a ranking
change pass.

## Disposable Elasticsearch Suite

Run the standard Docker-independent unit suite with:

```bash
mvn -f services/workspace-core/pom.xml test
```

Run the real Elasticsearch evaluation with:

```bash
mvn -f services/workspace-core/pom.xml -Psearch-quality-it verify
```

The dedicated profile starts Elasticsearch `8.11.1` through Testcontainers, creates an isolated
index with no persistent volume, applies the production mapping, indexes the versioned corpus,
refreshes deterministically, runs the production query and response parsing, deletes the test
index and removes the disposable containers. The profile requires a working Docker-compatible
runtime and does not silently skip when it is unavailable. The standard unit command does not
execute `SearchQualityBaselineIT`.

Testcontainers `2.0.5` is pinned in test scope because it supports Docker Engine 29's minimum
API version. `commons-lang3` `3.18.0` is also test-scoped to match Testcontainers' transitive
`commons-compress` requirement. Neither dependency is on the production classpath.

## Production Path Exercised

`SearchQualityBaselineIT` uses:

- `ElasticsearchClientConfig` for the production HTTP client;
- `ElasticsearchTranscriptAdapter.ensureTranscriptIndexExists()` for the production mapping;
- `TranscriptIndexDocumentMapper` for the production document shape;
- `ElasticsearchTranscriptAdapter.indexTranscriptRows()` for normal corpus ingestion;
- `ElasticsearchTranscriptAdapter.search()` for the production query and hit parser;
- `SearchApplicationService` and `SearchRelevancePolicy` for authorization scope, the searchable
  Asset allowlist, post-filtering, the public cap and per-Asset cap;
- `AssistantSearchPortAdapter` to prove assistant retrieval reuses the same search use case.

The suite does not duplicate the production query JSON.

## Measured Version 1 Baseline

| Scenario | Ordered baseline / metric | Classification |
| --- | --- | --- |
| Exact phrase `vector clocks` | `vector-exact`, `vector-bag`; exact phrase rank 1 | Hard invariant |
| Bag of words `replicated state convergence` | `bag-reordered` at rank 1 | Hard invariant |
| Adjacent `causal delivery` | rows `020`, `021`, `022`; one adjacent cluster, one Asset | Characterized limitation |
| Candidate dominance `distributed tracing` | first three returned rows all from dominant Asset; two relevant Assets absent | Characterized limitation |
| Equal-score ties `consensus quorum` | `tie-a`, then `tie-b`, stable across ten repeats | Hard invariant |
| One token / one character / numeric | `kubernetes`, `x`, and `2024` each retrieve the matching row | Characterized behavior |
| Typo / prefix | `kubernets` and `kubernet` return no result | Characterized limitation |
| Vietnamese with accents | `thuật toán tìm kiếm` retrieves `vi-accented` at rank 1 | Hard invariant |
| Vietnamese without accents | `thuat toan tim kiem` returns no result | Characterized limitation |
| Workspace isolation | only `isolation-local`; identical foreign row excluded | Hard invariant |
| Optional Asset scope | only rows from the selected Asset | Hard invariant |
| Public and per-Asset caps | 12 rows, 5 distinct Assets, no Asset above 3 rows | Hard invariant |

The current Elasticsearch candidate pool is `60`. Spring returns at most `12` rows, with a
workspace-wide maximum of `3` rows per Asset. `resultCount` is the number returned after the
Spring relevance policy, not Elasticsearch total hits. The public API has no pagination or
client-controlled limit.

## Hard Invariants

The real-Elasticsearch suite fails on regression of:

- Workspace and optional Asset isolation;
- deterministic repeated ordering;
- stable canonical transcript-row identity;
- nullable and present timing propagation;
- production response parsing;
- the `60`-candidate behavior as observed through dominance;
- the public `12`-result cap;
- the workspace-wide `3`-per-Asset cap;
- exact phrase target within the locked position;
- assistant retrieval reuse of the same search use case.

## Known Quality Gaps

Slice 7.1 deliberately records these current limitations:

- adjacent matching transcript rows are not deduplicated;
- one strongly matching Asset can exhaust all `60` Elasticsearch candidates before Java
  diversity policy runs;
- the response returns only the matching row text and has no before/after context snippet;
- Vietnamese queries are not accent-insensitive;
- typo tolerance and prefix matching are not enabled;
- there is no pagination.

The corpus is intentionally not weakened around these gaps.

## Existing Unicode Bulk-Indexing Regression

The production NDJSON bulk path currently rejects documents containing non-ASCII text because
the manually serialized NDJSON body is not delivered with a safe UTF-8 contract. The suite
locks this as a characterized regression. To measure the current Elasticsearch analyzer
without changing production in Slice 7.1, the Vietnamese evaluation document is built by the
production `TranscriptIndexDocumentMapper` and seeded into the disposable index through the
same production `RestClient` as an `application/json` document. All query construction,
mapping and hit parsing remain production paths.

This is an indexing transport defect, not evidence that Vietnamese production ingestion is
supported. It must be fixed separately before claiming end-to-end Vietnamese search support.

## Why Ranking Is Unchanged

Slice 7.1 adds only test infrastructure, corpus data and documentation. It does not change
mapping analyzers, query clauses, boosts, candidate size, post-filtering, result caps or public
response fields. Later quality work must first demonstrate improvement against this baseline
while retaining its hard authorization, identity, timing and deterministic-order invariants.
