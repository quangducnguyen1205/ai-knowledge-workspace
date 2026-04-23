# End-To-End Diagram Pack

## Purpose

This is a reviewer-friendly current-state diagram pack for the backend baseline in Repo B.

Use it when you want one presentation-friendly document that shows:

- the current system topology
- which repo owns which responsibility
- the full user-visible golden path
- what gets written to PostgreSQL
- how Elasticsearch search becomes possible
- how the current asset and processing states move

This document is intentionally current-state only. It does not describe chatbot, RAG, vector search, collaboration, or production deployment maturity.

## 1. One-Screen System Overview

How to read this:
- Browser users interact through Repo FE.
- Repo B Spring Boot is the only product-facing backend entry point.
- Repo A FastAPI is internal processing, not the public product API.
- PostgreSQL is the product system of record.
- Elasticsearch is a derived search layer.

```mermaid
flowchart LR
    B["Browser / learner"] --> FE["Repo FE frontend"]
    FE -->|product API requests| S["Repo B Spring Boot product core"]

    S -->|persist product state| P["PostgreSQL (product system of record)"]
    S -->|explicit indexing + search queries| E["Elasticsearch (derived search layer)"]
    S -->|upload, task status, transcript capture| F["Repo A FastAPI processing service"]

    S:::product
    P:::storage
    E:::search
    F:::processing

    classDef product fill:#e8f3ff,stroke:#2b6cb0,stroke-width:1px
    classDef storage fill:#edf7ed,stroke:#2f855a,stroke-width:1px
    classDef search fill:#fff7e6,stroke:#b7791f,stroke-width:1px
    classDef processing fill:#fff0f0,stroke:#c53030,stroke-width:1px
```

## 2. End-To-End Current Golden Path Sequence

How to read this:
- This is the current product-visible happy path.
- The sequence shows where state is read or written as the user moves from auth to search and transcript context.
- Transcript snapshot persistence and explicit indexing are both part of the real current flow.

```mermaid
sequenceDiagram
    actor C as Client / Repo FE frontend
    participant S as Repo B Spring Boot product core
    participant P as PostgreSQL
    participant F as Repo A FastAPI processing service
    participant E as Elasticsearch

    C->>S: register / login
    S->>P: create or read UserAccount
    S-->>C: establish session

    C->>S: GET /api/workspaces
    S->>P: read or lazily create default Workspace
    S-->>C: workspace list

    C->>S: POST /api/assets/upload
    S->>P: resolve workspace ownership
    S->>F: upload lecture video
    F-->>S: fastapiTaskId + fastapiVideoId
    S->>P: persist Asset + ProcessingJob
    S-->>C: assetId

    loop until processing becomes terminal
        C->>S: GET /api/assets/{assetId}/status
        alt local ProcessingJob already terminal
            S->>P: read local Asset + ProcessingJob
        else local ProcessingJob non-terminal
            S->>F: poll task status
            S->>P: update ProcessingJob + Asset status
        end
        S-->>C: current product status
    end

    C->>S: GET /api/assets/{assetId}/transcript
    S->>P: read AssetTranscriptRowSnapshot rows
    alt snapshot missing after processing success
        S->>F: fetch transcript rows
        S->>P: persist validated transcript snapshot
    end
    S-->>C: transcript rows

    C->>S: POST /api/assets/{assetId}/index
    S->>P: read usable transcript snapshot
    S->>E: bulk index transcript-row documents
    S->>P: update Asset to SEARCHABLE
    S-->>C: indexing result

    C->>S: GET /api/search?q=...&workspaceId=...&assetId=...
    S->>P: resolve workspace ownership and optional asset scope
    S->>E: lexical search with workspace filter and optional asset filter
    E-->>S: ranked transcript-row hits
    S-->>C: product search results

    C->>S: GET /api/assets/{assetId}/transcript/context?transcriptRowId=...
    S->>P: read transcript snapshot window
    S-->>C: transcript context rows
```

## 3. Persistence And Write-Path Diagram

How to read this:
- Solid arrows show the main write path for the current baseline.
- PostgreSQL stores domain records and the local transcript snapshot.
- Elasticsearch stores only derived search documents after explicit indexing.

```mermaid
flowchart TD
    A["register / login"] --> UA["UserAccount write or read"]
    UA --> P["PostgreSQL (product system of record)"]

    W["workspace read/create"] --> WS["Workspace write or read"]
    WS --> P

    U["upload"] --> F["Repo A FastAPI processing service"]
    F --> AJ["Asset + ProcessingJob persist"]
    AJ --> P

    ST["status refresh"] --> PJ["ProcessingJob status update"]
    PJ --> P
    ST --> AS["Asset status update"]
    AS --> P

    TR["transcript read after success"] --> TS["AssetTranscriptRowSnapshot persist when missing and usable"]
    TS --> P

    IX["explicit indexing"] --> RD["read transcript snapshot from PostgreSQL"]
    RD --> ESW["write transcript-row search documents"]
    ESW --> E["Elasticsearch (derived search layer)"]
    IX --> AUP["Asset status update to SEARCHABLE"]
    AUP --> P

    DEL["asset delete"] --> PDEL["delete AssetTranscriptRowSnapshot + ProcessingJob + Asset"]
    PDEL --> P
    DEL --> EDEL["delete derived search documents when asset is SEARCHABLE"]
    EDEL --> E
```

## 4. Search And Indexing Lifecycle

How to read this:
- FastAPI helps produce transcript data, but it is not the product-facing search endpoint.
- Search becomes possible only after transcript snapshot capture plus explicit indexing.
- Workspace scope is always enforced first, and asset scope is optional.

```mermaid
flowchart LR
    F["Repo A FastAPI processing service"] -->|processed transcript rows| S["Repo B Spring Boot product core"]
    S -->|validate usable transcript rows| T["PostgreSQL AssetTranscriptRowSnapshot"]
    T -->|explicit POST /api/assets/{assetId}/index| I["Indexing path in Repo B"]
    I -->|derived transcript-row documents| E["Elasticsearch"]

    Q["GET /api/search?q=..."] --> S
    S -->|resolve owned workspace| P["PostgreSQL workspace / asset ownership state"]
    S -->|optional assetId validation within workspace| P
    S -->|lexical search with workspace filter and optional asset filter| E
    E -->|ranked transcript-row hits| S
    S -->|product search response| Q
```

## 5. Current State Transitions

How to read this:
- The product tracks both `Asset` state and `ProcessingJob` state.
- These are related but not identical; one represents product readiness, the other represents the Spring-side view of the upstream processing task.

### 5A. Asset Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PROCESSING
    PROCESSING --> TRANSCRIPT_READY: usable transcript snapshot captured
    PROCESSING --> FAILED: upstream processing fails\nor transcript is unusable
    TRANSCRIPT_READY --> SEARCHABLE: explicit indexing succeeds
    SEARCHABLE --> SEARCHABLE: repeat indexing succeeds
```

### 5B. ProcessingJob Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> RUNNING: upstream task starts
    PENDING --> SUCCEEDED: upstream task finishes quickly
    PENDING --> FAILED: upstream task fails
    RUNNING --> SUCCEEDED: upstream task succeeds
    RUNNING --> FAILED: upstream task fails
    SUCCEEDED --> SUCCEEDED: later reads use stored local terminal state
    FAILED --> FAILED: later reads use stored local terminal state
```

## Related Current-State Docs

- [Reviewer Overview](00-reviewer-overview.md)
- [System Context](01-system-context.md)
- [Service Boundaries](02-service-boundaries.md)
- [Search Architecture](03-search-architecture.md)
- [Current Implemented Product Flow](phase1-implemented-product-flow.md)
- [API Summary](../api/API.md)
- [Database](../data/Database.md)
