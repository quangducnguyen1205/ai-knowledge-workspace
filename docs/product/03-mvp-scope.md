# MVP Scope

## MVP Goal

Deliver a narrow product that lets a user upload a lecture video, wait for processing, search transcript chunks within their workspace, and recover the relevant segment quickly.

## In Scope

- Upload a media asset
- Track processing status
- Store assets within a user-owned workspace boundary
- Search transcript chunks
- View relevant transcript segments in context
- Restrict retrieval to the current user and workspace scope

## Out Of Scope

- Chatbot or assistant interaction
- RAG answer generation
- Temporal orchestration
- Polished full frontend
- Multimodal fusion
- Broad support claims for media beyond lecture video

Audio lectures may be considered later as stretch scope, but they are not part of the primary phase 1 promise.

## Primary Demo Story

1. A user uploads a lecture video into their workspace.
2. The system shows processing status while transcription and chunk preparation complete.
3. The user searches for a concept they vaguely remember from the lecture.
4. The system returns relevant transcript chunks within that workspace.
5. The user opens a result and reviews the matching transcript segment to recover the needed explanation.

## Success Criteria

### User Value

- A user can recover a relevant lecture segment faster than manual scrubbing or full-video rewatching.
- Search results are specific enough to help the user decide which segment to open.

### Technical

- The system can process uploaded lecture videos and expose status clearly.
- Search returns transcript chunk results scoped to the correct user and workspace.
- The product boundary between Spring Boot core and FastAPI processing remains clear.

### Portfolio And Demo

- The MVP demonstrates a focused product problem, not a generic AI showcase.
- The demo clearly shows ingestion, processing, scoped search, and result recovery as one coherent workflow.
