# Project3 End-to-End Diagram Pack

Status: current normal product flow. Removed Spring compatibility paths are intentionally absent.

## 1. System topology

```text
Browser
  |
  v
Spring workspace-core ---------------------> PostgreSQL (product truth)
  |                  |                         - users/workspaces/assets
  |                  |                         - jobs/transcript/outbox/inbox
  |                  +----------------------> MinIO (binary/object storage)
  |
  +-- request outbox --> Kafka --> FastAPI/Celery/Whisper
  |                                  |
  |                                  +--> processing artifact
  |                                  +--> result outbox --> Kafka
  |
  +<-- result listener <-------------+
  |
  +-- indexing outbox --> Kafka --> Spring indexing listener --> Elasticsearch
  |
  +-- assistant provider port --> FastAPI/LLM
```

The browser calls Spring only. PostgreSQL owns product state and canonical transcript rows.
Elasticsearch is derived and rebuildable. Kafka is transport, not truth.

## 2. Upload and processing

```text
Browser       Controller       Upload service       MinIO       PostgreSQL       Kafka/FastAPI
  | POST multipart |                 |                |              |                 |
  |--------------->| command         |                |              |                 |
  |                |--------------->| validate       |              |                 |
  |                |                |--------------->| put object   |                 |
  |                |                |<---------------| reference    |                 |
  |                |                | AssetUploadTransaction         |                 |
  |                |                |------------------------------>| asset/job/outbox|
  | 202 IDs/status |<---------------|                               | commit          |
  |<---------------|                |                               |                 |
  |                |                |                relay outside DB tx ------------>|
  |                |                |                               | request/process |
  |                |                |                               |<--- result -----|
  |                |                | result handler transaction    |                 |
  |                |                |-- artifact HTTP in transaction ---------------->|
  |                |                |<------------------------------ rows             |
  |                |                |------------------------------>| transcript/job/ |
  |                |                |                               | inbox commit     |
```

If the database write fails after object upload, Spring performs best-effort object cleanup. The
artifact HTTP call deliberately retains its characterized transaction participation until a
separate consistency/idempotency redesign proves an equivalent model.

## 3. Indexing

```text
result application transaction
  -> replace canonical transcript
  -> create indexing job + indexing outbox
  -> commit

indexing relay -> Kafka -> indexing listener
  -> begin transaction: validate state/fingerprint, mark INDEXING -> commit
  -> Elasticsearch replace asset documents (no DB transaction)
  -> finalize transaction: recheck fingerprint, mark INDEXED/SEARCHABLE -> commit
```

An Elasticsearch write cannot redefine product truth. Search applies PostgreSQL ownership and
searchability gating even if a stale derived document exists.

## 4. Search and assistant

```text
SearchController -> SearchQueryUseCase -> authorized asset scope
                                      -> search index port -> Elasticsearch
                                      -> SearchResult -> web response

AssistantController -> command/query use case -> bounded authorized sources
                                             -> provider port -> FastAPI/LLM
                                             -> Spring citation validation
                                             -> public response
```

Provider citations are untrusted until Spring validates them against the source set it supplied.

## 5. State transitions

```text
PROCESSING
  | transcript.ready applied atomically
  v
TRANSCRIPT_READY
  | current fingerprint indexed and finalized
  v
SEARCHABLE

PROCESSING -- asset.processing.failed --> FAILED
```

Duplicate result IDs return the durable inbox state without applying effects twice. GET status and
transcript endpoints do not poll upstream services or mutate state.
