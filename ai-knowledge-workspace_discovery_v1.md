# AI Knowledge Workspace - Discovery v1

## 1. Primary user
Primary user for MVP: self-directed learners such as students or junior engineers who consume long-form learning media (especially lecture videos) and often remember that a useful explanation existed somewhere in a past video, but cannot quickly find the exact segment again.

Why this user first:
- matches the builder's real usage
- aligns with the current FastAPI asset base (video ingestion, transcription, semantic search)
- is narrow enough for a serious MVP

## 2. Core pain point
Long educational videos and audio recordings are information-dense. Users often:
- remember the idea but not the exact wording
- remember the source video but not the exact moment
- waste time manually scrubbing or replaying large parts of the media
- cannot skim efficiently when transcripts are missing or hard to access

## 3. JTBD
When I vaguely remember that a concept or explanation appeared in a lecture video or audio I consumed before, I want to search and jump to the relevant segment quickly, so that I can recover the knowledge without rewatching the whole content.

## 4. MVP core value
Primary value:
- help users recover the right piece of knowledge from long-form media quickly

Supporting value:
- transcripts make long media skimmable and easier to review

Important framing:
- the MVP is not a chatbot
- the MVP is not an AI assistant
- the MVP is a search-first knowledge recovery tool

## 5. MVP input scope
### Recommended MVP scope
- Primary: lecture videos
- Stretch (only if almost free from existing pipeline): audio lectures / podcasts

Reasoning:
- the current system is explicitly video-centric
- video lecture search already proves the problem well
- including both video and audio in the promise makes the first milestone fuzzier

## 6. MVP output scope
### Must-have
- upload media asset
- ingest/process status tracking
- searchable transcript chunks
- search results that point to relevant segment(s)
- transcript viewing for the asset
- filtering by workspace or owner scope

### Not required in phase 1
- generated summaries
- chatbot Q&A
- recommendations
- multi-modal ranking
- collaborative workspace sharing

## 7. Why AI / semantic retrieval
Keyword search alone is weak when users:
- remember meaning but not exact words
- search using paraphrases or related concepts
- search across noisy ASR transcripts
- switch between terminology during learning

Semantic retrieval helps find conceptually related transcript chunks.

But not everything needs AI:
- workspace/user filtering is normal backend filtering
- asset status, ownership, auth, pagination are standard product backend concerns
- exact-match keyword search is still useful, so the long-term target should be hybrid search

## 8. Workspace meaning in this product
Workspace is not "a group of users with the same goal" in phase 1.

Recommended definition:
- a logical container owned by one user
- contains that user's media assets, transcript chunks, search scope, and future notes/tags
- used for isolation, filtering, and future expansion

Phase 1 simplification:
- one user can have one or more workspaces
- each workspace belongs to exactly one user
- no collaboration yet

## 9. Service boundaries
### FastAPI AI Processing Service
FastAPI service only handles AI/media processing work such as media ingestion, transcription, chunking, embedding generation, and returning processing artifacts/status to the core system.

### Spring Boot Product Core
Spring Boot core owns product/backend concerns such as auth, users, workspaces, assets, orchestration, authorization, search APIs, metadata management, and integration with Elasticsearch.

## 10. Search strategy
### Target
Elasticsearch should be the public search layer for the new system.

### Why
- supports hybrid search direction better
- better fit for product-oriented filters and query composition
- better separation between business metadata and AI retrieval artifacts

### Role of FAISS
- legacy / transitional retrieval mechanism inside the old FastAPI service
- acceptable as an internal temporary component
- should not be the long-term public search contract for the new product

FAISS remains reasonable only if the goal is a small semantic-search demo with limited filtering and no strong product search requirements.

## 11. Phase 1 non-goals
- chatbot / assistant UX
- RAG answer generation
- Temporal orchestration
- polished full frontend product
- multi-modal fusion (visual + audio + text ranking)

## 12. MVP success criteria
### User value
For a prepared set of lecture media, the user can recover a relevant segment for most test queries without manually scrubbing the whole file.

### Technical
Upload -> process -> searchable -> search result flow works reliably in local development, with clear job status and scoped search results.

### Portfolio / demo
The project clearly demonstrates:
- Spring Boot as product core
- FastAPI reused as AI service
- search-first product design
- clean docs and architecture boundaries

## 13. 5-minute demo story
Recommended demo flow:
1. show a workspace with one already-processed lecture asset
2. run a natural-language query based on a remembered concept
3. open the returned transcript segment / asset detail
4. show that results are scoped to the current workspace
5. upload a new media file and show processing status pipeline

Reasoning:
- show user value first
- show ingestion pipeline second

## 14. Working assumptions locked for now
- product type: search-first AI knowledge workspace
- primary user: learners using long-form educational media
- primary medium: lecture video
- architecture: Spring Boot core + FastAPI AI service + Elasticsearch target
- implementation planning comes after problem framing and MVP definition

## 15. Open decisions to revisit later
- whether audio is phase-1 in-scope or phase-1.5 stretch scope
- whether to create workspace as a visible UI concept in the very first demo or keep one default workspace initially
- exact transcript/timestamp UX shape for search results
