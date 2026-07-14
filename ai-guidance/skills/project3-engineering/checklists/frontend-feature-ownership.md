# Frontend feature-ownership checklist

- [ ] Browser requests go through the shared Spring HTTP boundary only.
- [ ] No feature calls FastAPI, Kafka, Elasticsearch, MinIO or provider directly.
- [ ] `app` owns shell, bootstrap and routing only.
- [ ] Feature folders own user workflows and feature API calls.
- [ ] Entities own reusable product behavior; shared/lib remains neutral.
- [ ] Upload orchestration has one feature owner.
- [ ] Lifecycle polling has one feature owner.
- [ ] Automatic indexing is presented as normal.
- [ ] Manual indexing remains visible as recovery at `TRANSCRIPT_READY`.
- [ ] Search state and study-route state have clear ownership.
- [ ] Assistant state is asset/workspace scoped and Spring-authorized.
- [ ] Citation navigation uses canonical source identity.
- [ ] Existing routes, API shapes and auth defaults are unchanged.
- [ ] Loading, empty, error and terminal states are covered.
- [ ] Keyboard/focus behavior remains usable where touched.
- [ ] Focused tests pass.
- [ ] Full unit tests, typecheck and build pass for source changes.
- [ ] Browser evidence is classified separately from static tests.
