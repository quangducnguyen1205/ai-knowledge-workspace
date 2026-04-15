# Phase 2 Ownership Foundation

## Purpose

This note records the first Phase 2 foundation already implemented in Repo B: ownership now starts at the workspace boundary through a minimal current-user mechanism in Spring.

## What This Slice Added

- an initial minimal current-user identity mechanism using `X-Current-User-Id`
- a configured local/dev fallback current user when session and header are omitted
- a later minimal auth-entry step using `POST /api/auth/session` as the primary product-facing current-user path
- workspace ownership in the domain model
- one default workspace path per current user
- ownership enforcement first on:
  - workspace create/list/read
  - workspace-scoped asset listing
  - workspace-scoped search
- ownership-safe `404` rollout on asset-by-id reads and mutations through workspace ownership:
  - asset read
  - asset status
  - transcript read
  - transcript context
  - explicit indexing
  - title-only update
  - delete

## What Stayed Intentionally Out Of Scope

- login flows, passwords, tokens, OAuth, or a full session platform
- collaboration, sharing, teams, roles, or memberships
- broad ownership enforcement across every mutation endpoint
- transcript persistence
- chatbot/RAG work

## Why This Is A Phase 2 Foundation

Phase 1 proved the narrow search-first MVP. This slice starts Phase 2 by making ownership semantics real in the product core without turning the repo into a full auth platform.

The current phase-2 state is still intentionally minimal:

- session-based current-user entry is primary for product-facing requests
- header/default-user behavior remains only as local/dev fallback
- ownership rules remain centered on workspace ownership rather than a larger auth model
