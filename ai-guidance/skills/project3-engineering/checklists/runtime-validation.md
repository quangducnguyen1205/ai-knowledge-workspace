# Project3 runtime-validation checklist

- [ ] Runtime task has explicit authorization and a bounded purpose.
- [ ] Repository, branch, tag and environment preflight are recorded.
- [ ] Exact topology, profiles and overrides are documented.
- [ ] Fixture scope and cleanup plan are explicit.
- [ ] No unapproved upload, provider inference or Kafka publication occurs.
- [ ] No unapproved DB, MinIO, Elasticsearch or Redis mutation occurs.
- [ ] No Docker cleanup, image build/pull or resource deletion occurs.
- [ ] Browser calls remain inside the documented product boundary.
- [ ] Logs/evidence are sanitized before persistence or handoff.
- [ ] PASS/PARTIAL/FAILED classification has a clear scope.
- [ ] Compatibility and recovery depth is reported separately.
- [ ] Restart/reconnect evidence does not imply application recovery automatically.
- [ ] Outbox state is inspected without manual mutation.
- [ ] Provider/model/version and timeout evidence are recorded without secrets.
- [ ] Final runtime state is restored or the residual state is reported.
- [ ] Git worktrees and immutable submission tags remain unchanged.
