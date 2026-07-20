# Removed and Retained Surfaces

Status: current compatibility decision registry.

## Removed from Spring

| Surface | Decision | Replacement |
|---|---|---|
| direct FastAPI upload/status processing | Removed before timestamp-aware transcript work | transactional asset/job/outbox request plus Kafka |
| `compatibility` Spring profile | Removed | coherent `project3` profile |
| direct-upload trigger-mode flag | Removed | one normal Kafka/outbox path |
| old compatibility Make targets | Removed | `make run` or `make run-project3` |
| GET status refresh with upstream polling | Removed | side-effect-free PostgreSQL status query |
| transcript load-or-capture fallback | Removed | canonical snapshot applied by processing-result command |
| direct-upload task/video columns | Removed from the clean baseline | processing request event correlation |

These paths duplicated the verified Project3 normal path and caused mixed orchestration,
persistence and transport responsibility. The FastAPI repository was not modified; any standalone
endpoint there is outside the Spring product contract.

## Retained

| Surface | Status | Reason |
|---|---|---|
| legacy-session authentication | Supported local/default option | authentication cutover is separate |
| explicit indexing endpoint | Supported recovery | rebuild one authorized asset's derived index |
| exact-ID result recovery | Supported operator control | reapply one durable known failed envelope |
| exact-ID stale outbox requeue | Supported operator control | bounded recovery without a broad scan |
| scoped one-shot relay/smoke commands | Supported validation/recovery | reuse the normal event state machine |
| typed bounded outbox reconciliation | Supported in `project3` | retry classified transient exhausted failures |

Unknown, permanent, historical-unclassified and recovery-exhausted outbox failures remain manual.
There is no retry-topic framework or Kafka DLQ in this baseline.
