# Functional spec

This directory holds everything that states *what* the repository must do or
what a contribution must satisfy — a rule a reviewer or a gate can be held to,
and the two suites those rules govern. How that behavior is implemented belongs
in [../architecture/](../architecture/README.md) instead.

One kind lives here: every declaration in this folder is an `FS-` ID, whichever
file it sits in. The two suites' contracts are part of that — `FS-metadata` says
what the shipped metadata must be and `FS-tests` what a test must do to justify
it, neither of which is about how the build produces either.

| ID | Subject |
| --- | --- |
| [§FS-repository-functional-spec](functional-spec.md#fs-repository-functional-spec-repository-functional-specification) | Repository functional specification |
| [§FS-library-version-update-automation](functional-spec.md#fs-library-version-update-automation-library-version-update-automation) | Library version update automation |
| [§FS-contribution-contract](contribution-contract.md#fs-contribution-contract-test-and-metadata-contribution-contract) | Test and metadata contribution contract |
| [§FS-test-contract](test-contract.md#fs-test-contract-the-test-contract) | The test contract |
| [§FS-repository-status-report](repository-status.md#fs-repository-status-report-repository-issue-progress-and-state) | Repository issue progress and state |
| [§FS-metadata](metadata.md#fs-metadata-the-metadata-suite) | The `metadata/` suite |
| [§FS-tests](tests.md#fs-tests-the-tests-suite) | The `tests/` suite |

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
- [metadata.md](metadata.md) — the `metadata/` suite: its layout, its additive
  invariants, and where each entry comes from.
- [tests.md](tests.md) — the `tests/` suite: the per-coordinate test projects
  and how the harness exercises them.
