# Discovery Summary

## Primary User

The initial user is a self-directed learner, such as a university student or early-career learner, who studies from long-form lecture videos.

## Core Pain Point

Users often remember that a useful concept, explanation, or example appeared somewhere in a lecture they watched before, but they cannot quickly recover the exact segment. Rewatching or manually scrubbing long media is slow and frustrating.

## Job To Be Done

When I vaguely remember that a concept or explanation appeared in a lecture video or audio I consumed before, I want to search and jump to the relevant segment quickly, so that I can recover the knowledge without rewatching the whole content.

## MVP Value

The MVP should help users recover the right knowledge segment from long-form learning media quickly. Transcript visibility matters, but the product is search-first rather than transcript-first.

## Phase 1 Scope

- Primary input is lecture video.
- Audio lectures may be considered stretch scope, but are not the primary promise.
- The first release should stay narrow and realistic for a student project.

## Phase 1 Must-Haves

- Upload a media asset
- Track processing status
- Search transcript chunks
- View relevant transcript segments
- Restrict retrieval to the current user and workspace scope

## Non-Goals

- Generic chatbot behavior
- RAG answer generation
- Temporal orchestration
- Polished full frontend
- Multimodal fusion

## Service Boundary

- Spring Boot product core: auth, users, workspaces, assets, orchestration, authorization, metadata, and the client-facing search API
- FastAPI processing service: ingestion, transcription, chunking, embeddings, and processing result/status handling
- Elasticsearch target role: search layer for transcript chunk retrieval
