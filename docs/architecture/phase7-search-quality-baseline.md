# Phase 7 Search Quality Baseline

## Purpose And Scope

Slice 7.1 characterizes the current Spring-owned lexical search behavior without changing
production ranking. The baseline answers which canonical transcript rows the current mapping,
query adapter and Java relevance policy return, and in which order, for representative
Workspace video searches.

Slice 7.1A closes the Unicode transport regression discovered by that baseline. It does not
change search ranking, query clauses, mapping analyzers or public contracts.

Slice 7.2 adds one application-owned result policy: consecutive matching transcript rows from
the same Asset are represented as one video moment after deterministic relevance ordering and
before result quotas. Elasticsearch retrieval and scoring remain unchanged.

FastAPI is not involved. PostgreSQL remains the product and authorization authority;
Elasticsearch remains derived state.

## Versioned Corpus Ownership

The versioned test inputs are:

- `services/workspace-core/src/test/resources/search-quality/v1/corpus.json`
- `services/workspace-core/src/test/resources/search-quality/v1/expected-baseline.json`
- `services/workspace-core/src/test/resources/search-quality/v1/expected-slice-7.2.json`

The corpus uses stable Workspace, Asset and transcript-row IDs plus fixed timing and creation
metadata. Version `v1` contains `102` transcript-row documents across two Workspaces and ten
Assets. It covers exact phrases, bag-of-words retrieval, adjacent duplicates, one-Asset
candidate dominance, deterministic ties, short queries, English, accented and unaccented
Vietnamese, optional Asset scope and Workspace isolation.

`expected-baseline.json` remains the measured pre-Slice-7.2 history.
`expected-slice-7.2.json` is a reviewed overlay containing only the scenarios intentionally
changed by adjacent-moment policy. All other expectations are inherited unchanged. Changes to
corpus semantics or accepted ordering require a new reviewable corpus version or an explicitly
justified expectation overlay; do not edit historical order merely to make a policy change pass.

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
- `ElasticsearchTranscriptAdapter.indexTranscriptRows()` for all `102` corpus documents,
  including the accented Vietnamese row;
- `ElasticsearchTranscriptAdapter.search()` for the production query and hit parser;
- `SearchApplicationService` and `SearchRelevancePolicy` for authorization scope, the searchable
  Asset allowlist, post-filtering, the public cap and per-Asset cap;
- `AssistantSearchPortAdapter` to prove assistant retrieval reuses the same search use case.

The suite does not duplicate the production query JSON.

## Historical Measured Version 1 Baseline

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

The historical Slice 7.1 baseline records adjacent duplication; Slice 7.2 resolves that one
application-level limitation. Current unresolved limitations are:

- one strongly matching Asset can exhaust all `60` Elasticsearch candidates before Java
  diversity policy runs;
- the response returns only the matching row text and has no before/after context snippet;
- Vietnamese queries are not accent-insensitive;
- typo tolerance and prefix matching are not enabled;
- there is no pagination.

The corpus is intentionally not weakened around these gaps.

## Unicode Bulk-Indexing Closure

Slice 7.1 exposed that the production adapter passed the manually serialized
`application/x-ndjson` payload to Spring as a Java `String`. Without an explicit charset,
Spring's string HTTP converter encoded that media type with its non-JSON default instead of
UTF-8. Elasticsearch therefore rejected non-ASCII JSON fields even though each line had been
serialized correctly by Jackson.

Slice 7.1A converts the complete NDJSON payload to UTF-8 bytes before handing it to the HTTP
client. Metadata and document lines remain Jackson-serialized, every record retains its
newline, and the payload retains the final newline required by the Bulk API. The byte-array
HTTP converter also derives `Content-Length` from the encoded byte length.

The direct `application/json` seed and the expected-failure characterization have been
removed. All `102` v1 corpus documents now enter Elasticsearch through the production mapper,
write operation and bulk adapter. The suite additionally proves exact source fidelity for
Vietnamese, decomposed combining code points, CJK, emoji, JSON-sensitive text and mixed
ASCII/Unicode batches.

An exact accented query, `thuật toán tìm kiếm`, is now proven end to end through production
bulk indexing and production search, with canonical row ID and timing preserved at rank 1.
This is transport fidelity plus the behavior of the existing standard analyzer; it is not
accent folding, Vietnamese stemming or accent-insensitive search. The unaccented query
`thuat toan tim kiem` remains unsupported.

## Slice 7.2 Adjacent-Moment Policy

The policy pipeline is:

```text
raw Elasticsearch candidates
-> meaningful-term filtering
-> existing deterministic relevance ordering
-> adjacent-moment clustering
-> workspace per-Asset cap
-> public total cap
```

A cluster is limited to one Asset and one connected run of distinct, non-null consecutive
`segmentIndex` values. Thus `20,21,22` is one run, `20,21,23` is two runs and `20,22` is two
runs. Input order does not affect the cluster. Rows from different Assets never join, null
segment indexes remain independent, and identical text or neighboring timestamps do not create
a cluster. Duplicate candidates with the same non-null segment index join the same run.

The representative is the highest-ranked candidate under the existing production comparator:
score descending, then segment index, Asset ID and canonical transcript-row ID. This means equal
scores in `20,21,22` retain segment `20`, while a higher-scoring segment `21` remains the
representative. Representatives retain their existing global relevance order.

The policy applies equally to workspace-wide and Asset-scoped searches. Workspace-wide search
still permits at most three representatives per Asset; Asset-scoped search can return up to the
public maximum of twelve representatives.

| Scenario | Historical v1 | Slice 7.2 |
| --- | --- | --- |
| Adjacent `causal delivery` | `adjacent-020`, `adjacent-021`, `adjacent-022`; one duplicate run in public results | `adjacent-020`; no adjacent run remains |
| Candidate dominance `distributed tracing` | first three dominant adjacent rows; one Asset | `dominant-000`; still one Asset because weaker Assets were absent from the top-60 candidates |
| Shared cap corpus | 12 rows from five Assets, including adjacent rows | five moment representatives; dedicated non-adjacent fixtures retain hard 12-total and per-Asset cap evidence |

Exact phrase and bag-of-words ordering, deterministic ties, Workspace isolation, accented
Vietnamese rank/timing and Unicode production bulk indexing remain unchanged. Unaccented
Vietnamese remains unsupported. `AssistantSearchPortAdapter` reuses the same search use case, so
assistant retrieval receives the same representative ordering and canonical timing.

This is structural adjacency over canonical segment ordering, not semantic understanding.
Candidate-pool diversity remains unresolved, and no context snippet is added.

## Ranking And Retrieval Boundaries

Slice 7.1 adds only test infrastructure, corpus data and documentation. Slice 7.1A changes only
the bulk request byte encoding. Slice 7.2 changes application post-ranking selection only.
No slice changes mapping analyzers, Elasticsearch query clauses, boosts, candidate size or
public response fields. Later quality work must demonstrate improvement against this baseline
while retaining its hard authorization, identity, timing and deterministic-order invariants.
