# FastAPI processing checklist

- [ ] FastAPI remains an internal service, not a browser-facing product API.
- [ ] Request consumer validates the existing event envelope and payload.
- [ ] Kafka acceptance and Celery handoff remain idempotent.
- [ ] Existing task names and worker semantics remain compatible.
- [ ] Media and artifact boundaries remain explicit.
- [ ] Provider calls remain behind the internal adapter.
- [ ] Processing result identity/payload/outbox semantics are unchanged.
- [ ] Result relay and reconciliation have one implementation path.
- [ ] Historical ambiguous failures are not automatically replayed.
- [ ] SQLAlchemy/session ownership is explicit.
- [ ] Schema initialization is owned by process startup, not every relay iteration.
- [ ] Stable ASGI, worker, consumer and relay imports remain side-effect safe.
- [ ] Unit tests do not contact real Kafka, DB, Celery, MinIO or provider.
- [ ] `py_compile` or compileall covers changed Python files.
- [ ] Focused and bounded full unittest results are recorded.
- [ ] Compose checks use config-only mode when runtime is out of scope.
- [ ] Direct-processing compatibility remains available.
- [ ] Safe diagnostics exclude credentials, payloads and raw stack traces.
