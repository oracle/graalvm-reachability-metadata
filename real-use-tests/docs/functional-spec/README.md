# Functional spec

This directory states what the real-use suite and the workflow that generates
it must do — the rules a scenario, a reviewer, or a program step can be held
to. How the harness tasks, the CI job, and the Rhei template implement it lives
in [../architecture/](../architecture/README.md).

One kind lives here: every declaration in this folder is an `FS-` ID, whichever
file it sits in.

| ID | Subject |
| --- | --- |
| [§FS-real-use-tests](suite.md#fs-real-use-tests-the-real-use-tests-suite) | The `real-use-tests/` suite |
| [§FS-real-use-test-contract](contract.md#fs-real-use-test-contract-the-real-use-test-contract) | The real-use test contract |
| [§FS-real-use-test-generation](generation.md#fs-real-use-test-generation-generating-real-use-tests) | Generating real-use tests |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [suite.md](suite.md) — what the suite is for, its project layout, the
  metadata it consumes and keeps test-local, and when it runs.
- [contract.md](contract.md) — what a real-use test must have and must not
  do, and which points of the `tests/` contract it inherits.
- [generation.md](generation.md) — the generation workflow: its inputs,
  phases, budgets, scenario removal, and outcomes.
