# Project3 change-safety checklist

- [ ] Task type and repository owner are explicit.
- [ ] Worktree, branch, upstream and immutable baseline were inspected.
- [ ] No unexpected tracked/untracked file requires preservation.
- [ ] Expected files and commit boundary are listed before editing.
- [ ] Public HTTP, Kafka, data, auth and profile contracts are frozen.
- [ ] Transaction and external-I/O boundaries are recorded when relevant.
- [ ] No schema, migration, dependency or lockfile change is implicit.
- [ ] No secret, credential, token, cookie or raw payload is introduced.
- [ ] No generated artifact or private note is in the tracked scope.
- [ ] Focused validation matches the changed ownership boundary.
- [ ] Full validation is run when source/config risk requires it.
- [ ] Runtime/browser actions have explicit authorization and cleanup.
- [ ] `git diff --check` passes.
- [ ] Staged file list contains only intended files.
- [ ] Staged diff was read, not only filenames.
- [ ] Commit message follows Conventional Commits.
- [ ] Push is not performed without separate authorization.
