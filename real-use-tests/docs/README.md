# Real-use tests documentation

This directory holds the real-use tests project's grounded documentation:
motivation, direction, the functional specification, and the architecture.
Every document follows the [`grund`](https://github.com/vjovanov/grund)
convention, so each fact has a stable ID and can be cited from prose, other
docs, and source code.

For the grounding rules and the agent contribution contract, see
[../AGENTS.md](../AGENTS.md).

## Namespace

`real-use-tests` is a grund workspace member with its own namespace, like
Forge. Inside this project cite its facts as `§<ID>`; cite repository facts as
`§root/<ID>` and Forge facts as `§forge/<ID>`. From the repository root and
from Forge, cite this project's facts as `§real-use-tests/<ID>`. Run
`grund check` from the repository root, the only place the workspace alias
table is in scope.

## Directory structure

| Kind | Home | Holds |
| --- | --- | --- |
| `GRUND` | [grund.md](grund.md) | Why the project exists — the motivation everything climbs back to. |
| `GOAL` | [goals.md](goals.md) | Where the project is headed — the outcomes it commits to. |
| `FS` | [functional-spec/](functional-spec/README.md) | What the suite and its generation workflow must do. |
| `AR` | [architecture/](architecture/README.md) | How the suite, its harness tasks, and its Rhei template are wired. |

### Files at a glance

- [grund.md](grund.md) — `GRUND-real-use-motivation`: why real-use tests exist next to `tests/`.
- [goals.md](goals.md) — `GOAL-consumer-evidence` and `GOAL-reviewable-metadata-gaps`.
- [functional-spec/suite.md](functional-spec/suite.md) — `FS-real-use-tests`: layout, metadata, and when the suite runs.
- [functional-spec/contract.md](functional-spec/contract.md) — `FS-real-use-test-contract`: what a real-use test must have and must not do.
- [functional-spec/generation.md](functional-spec/generation.md) — `FS-real-use-test-generation`: the generation workflow.
- [architecture/harness.md](architecture/harness.md) — `AR-real-use-test-harness`: tasks, CI job, and wiring.
- [architecture/workflow.md](architecture/workflow.md) — `AR-real-use-test-workflow`: the Rhei template.
