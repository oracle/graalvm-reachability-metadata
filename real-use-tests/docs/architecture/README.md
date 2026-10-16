# Architecture

This directory explains how the real-use suite is wired: the harness tasks
that run it, the CI job that gates it, and the Rhei template that generates it.
What the suite and its workflow must do lives in
[../functional-spec/](../functional-spec/README.md).

One kind lives here: every declaration in this folder is an `AR-` ID, whichever
file it sits in.

| ID | Subject |
| --- | --- |
| [§AR-real-use-test-harness](harness.md#ar-real-use-test-harness-real-use-test-harness) | Real-use test harness |
| [§AR-real-use-test-launcher](launcher.md#ar-real-use-test-launcher-real-use-run-launcher) | Real-use run launcher |
| [§AR-real-use-test-workflow](workflow.md#ar-real-use-test-workflow-real-use-test-generation-workflow) | Real-use test generation workflow |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [harness.md](harness.md) — the project build, the `realUse*` Gradle tasks,
  their separation from the `tests/` lanes, the CI job, and repository wiring.
- [workflow.md](workflow.md) — the Rhei template: inputs, state machine, agent
  and program states, run artifacts, and publication.
- [launcher.md](launcher.md) — host checks, issue selection, the claim loop,
  and the batched review that approves and auto-merges published runs.
