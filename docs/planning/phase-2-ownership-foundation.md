# Phase 2 Ownership Foundation

## Purpose

This note records the first Phase 2 foundation already implemented in Repo B: ownership now starts at the workspace boundary through a minimal current-user mechanism in Spring.

This is a historical slice note, not the full current backend status. Later Phase 2 work has since added transcript snapshot persistence, search refinement, workspace rename/delete, and basic session auth productization on top of this ownership foundation.

## What This Slice Added

- an initial minimal current-user identity mechanism using `X-Current-User-Id`
- a configured local/dev fallback current user when session and header are omitted
- a later minimal auth-entry step using `POST /api/auth/session` as a local/dev session shortcut
- a further basic-auth step using session-based register/login/logout and `GET /api/me` as the primary product-facing auth path
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

- JWTs, refresh tokens, OAuth, password reset, or a full auth platform
- collaboration, sharing, teams, roles, or memberships
- broad ownership enforcement across every mutation endpoint
- transcript persistence
- chatbot/RAG work

## Why This Is A Phase 2 Foundation

Phase 1 proved the narrow search-first MVP. This slice starts Phase 2 by making ownership semantics real in the product core without turning the repo into a full auth platform.

The current phase-2 state is still intentionally minimal:

- session-based register/login is primary for product-facing requests
- `/api/me` reads only the authenticated session user
- `POST /api/auth/session`, header fallback, and default-user fallback remain only as secondary local/dev paths
- ownership rules remain centered on workspace ownership rather than a larger auth model
