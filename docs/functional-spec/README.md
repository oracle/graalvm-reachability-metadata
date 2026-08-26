# Functional spec

This directory holds everything that states *what* the repository must do or
what a contribution must satisfy — a rule a reviewer or a gate can be held to,
and the two suites those rules govern. How that behavior is implemented belongs
in [../architecture/](../architecture/README.md) instead.

Three kinds live here, and the prefix tells you which file to open:

| Kind | Home | Holds |
| --- | --- | --- |
| `FS` | this folder | The repository specification and the contribution/test contracts. |
| `METADATA` | [metadata.md](metadata.md) | The shipped `metadata/` suite: layout, invariants, provenance. |
| `TESTS` | [tests.md](tests.md) | The `tests/` suite that justifies every metadata entry. |

`METADATA` and `TESTS` are filed here because each describes a suite's contract —
what the shipped metadata must be, and what a test must do to justify it — not
how the build produces either. They keep their own prefixes: a `METADATA-` or
`TESTS-` ID is not an `FS-` ID, and each is declared in its own home file above
rather than anywhere in this folder.

| ID | Subject |
| --- | --- |
| [§FS-repository-functional-spec](functional-spec.md#fs-repository-functional-spec-repository-functional-specification) | Repository functional specification |
| [§FS-library-version-update-automation](functional-spec.md#fs-library-version-update-automation-library-version-update-automation) | Library version update automation |
| [§FS-contribution-contract](contribution-contract.md#fs-contribution-contract-test-and-metadata-contribution-contract) | Test and metadata contribution contract |
| [§FS-test-contract](test-contract.md#fs-test-contract-the-test-contract) | The test contract |
| [§FS-repository-status-report](repository-status.md#fs-repository-status-report-repository-issue-progress-and-state) | Repository issue progress and state |
| §METADATA-suite | The `metadata/` suite |
| §TESTS-suite | The `tests/` suite |

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
