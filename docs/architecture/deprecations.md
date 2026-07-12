# Deprecation Registry

## Purpose

The integrated `project3` path is the only normal/default local product path:

```text
Spring upload
-> kafka_request
-> durable outbox and Kafka processing
-> automatic result handling
-> automatic indexing
-> SEARCHABLE
```

Start the processing topology with `make project3-up` in the FastAPI repository and Spring with `make run`. New Project3 integrations must use this asynchronous path.

Deprecation in this registry is non-blocking. The listed compatibility paths remain executable for rollback during the deprecation period. No removal date has been assigned, and removal requires a separate implementation decision.

## Registry

| Capability | Status | Replacement | Still supported for | Removal prerequisites | Removal date |
| --- | --- | --- | --- | --- | --- |
| Spring `direct_upload` | Deprecated, functional | `kafka_request` | Rollback and recovery | Completed deprecation window, caller and documentation audit, replacement observation evidence, rollback plan, and separate removal decision | Not scheduled |
| Spring `compatibility` profile / `make run-compatibility` | Deprecated, functional | `project3` / `make run` | Rollback drills and local recovery | Completed deprecation window, operator workflow audit, replacement observation evidence, rollback plan, and separate removal decision | Not scheduled |
| `make run-standalone` | Deprecated alias | `make run-compatibility` | Existing local callers during migration | Caller and script audit; the alias may be removed before the underlying compatibility profile only through a separate decision | Not scheduled |
| FastAPI direct processing endpoint | Deprecated, functional | Project3 Kafka consumer path | Spring rollback mode and generic standalone FastAPI use | Completed deprecation window, caller audit, replacement observation evidence, standalone-use audit, rollback plan, and separate removal decision | Not scheduled |

## Explicitly not deprecated

- explicit indexing endpoint and `Index transcript` recovery UI;
- manual and one-shot relay operations;
- exact-ID recovery;
- legacy session authentication;
- development identity fallback;
- canonical citation contracts;
- generic standalone FastAPI use outside Project3 integration.

## Compatibility behavior

The deprecated paths are not deleted, disabled, renamed, newly gated, or scheduled for removal. `direct_upload` still performs the same synchronous Spring-to-FastAPI handoff, the `compatibility` profile still disables the asynchronous chain, and the FastAPI direct processing endpoint retains its request, response, status, storage, and Celery behavior. Startup and invocation warnings contain no request data or credentials.

The explicit indexing endpoint remains the supported recovery action when automatic indexing does not advance an asset from `TRANSCRIPT_READY`. Manual relay and exact-ID recovery remain supported operational controls. Legacy session authentication is an independent compatibility decision.

## Kafka operational risk carried forward

Classification: `KAFKA_HARDENING_RECOMMENDED_NOT_DEPRECATION_BLOCKER`.

One prior local Kafka container OOM event was observed. The controlled observation campaign completed without an OOM recurrence, but this does not prove production-scale stability. P3-S4.B1 provides a bounded local Kafka heap and container memory budget plus automatic `unless-stopped` restart behavior while preserving the KRaft volume and broker semantics. P3-S4.B2 adds bounded automatic reconciliation for typed transient publication failures after normal attempts are exhausted. Historical, unknown, permanent, and recovery-exhausted failures still require explicit operator review or recovery; manual and exact-ID recovery remain supported and are not deprecated. Compatibility removal is still not scheduled.

## Removal gates

No deprecated capability is safe to delete in this phase. A future removal proposal must provide all of the following:

1. a completed and communicated deprecation window;
2. an audit showing no required caller, standalone workflow, script, documentation, or rollback drill depends on the candidate;
3. continued default-path evidence, categorized failure observations, and no unresolved duplicate/stuck outcomes;
4. a tested rollback strategy that does not require the candidate;
5. local-development usability evidence;
6. a separate, explicit removal decision with focused tests and documentation updates.
