# Functional spec

Repository behavior and contributor-facing requirements live here as
`FS-<slug>` declarations. A document belongs in this directory when it states
*what* the repository must do or what a contribution must satisfy — a rule a
reviewer or a gate can be held to. How that behavior is implemented belongs in
[../architecture/](../architecture/README.md) instead.

Each file declares its IDs at its headings; an `FS` ID always lives in this
folder, so the prefix tells you to open this directory.

| ID | Subject |
| --- | --- |
| §FS-repository-functional-spec | Repository functional specification |
| §FS-library-version-update-automation | Library version update automation |
| §FS-contribution-contract | Test and metadata contribution contract |
| §FS-test-contract | The test contract |
| §FS-repository-status-report | Repository issue progress and state |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [functional-spec.md](functional-spec.md) — the repository specification and
  the version-update automation that feeds it.
- [contribution-contract.md](contribution-contract.md) — what a metadata or
  test contribution must satisfy to be accepted.
- [test-contract.md](test-contract.md) — what a test must prove about the
  metadata it justifies.
- [repository-status.md](repository-status.md) — how repository issue progress
  and state are reported.
