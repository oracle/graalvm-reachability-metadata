# Repository documentation

This directory holds the repository's grounded documentation: motivation,
direction, the functional specification, and one module per major component.
Every document follows the [`grund`](https://github.com/vjovanov/grund)
convention, so each fact has a stable ID and can be cited from prose, other
docs, and source code.

For the authoritative grounding rules and the agent contribution contract, see
[../AGENTS.md](../AGENTS.md). For consumer-facing and contributor-facing guides,
see [../README.md](../README.md), [CONTRIBUTING.md](CONTRIBUTING.md),
[DEVELOPING.md](DEVELOPING.md), and [REVIEWING.md](REVIEWING.md).

## Namespaces

The repository root is the `root` grund namespace. Forge is a separate
namespace (an independently checked subproject) declared as a workspace member;
it owns its own motivation, goals, spec, and architecture under
[../forge/docs/](../forge/docs). Cite root facts as `§<ID>` here and as
`§root/<ID>` from Forge; cite Forge facts as `§forge/<ID>` from here. Run
`grund check` from the repository root to validate both.

## Directory structure

Documents are organized by grund *kind*. A kind groups facts of one type and has
a fixed *home* — a single file or a folder — declared in
[../.agents/grund.toml](../.agents/grund.toml). An ID always lives in its kind's
home, so the prefix tells you which file to open.

| Kind | Home | Holds |
| --- | --- | --- |
| `GRUND` | [grund.md](grund.md) | Why the repository exists — the motivation everything climbs back to. |
| `GOAL` | [goals.md](goals.md) | Where the repository is headed — measurable outcome goals. |
| `FS` | [functional-spec.md](functional-spec.md) | What the system must do — behavior, requirements, and how it is used. |
| `AR` | [architecture.md](architecture.md), [build-infra.md](build-infra.md) | How the repository is structured and how the build is wired. |
| `TCK` | [tck.md](tck.md) | The Gradle test harness task groups. |
| `E2E` | [e2e.md](e2e.md) | The infrastructure end-to-end tests (`testInfra`/`testAllInfra`). |
| `CI` | [ci.md](ci.md) | Recurring CI workflows, composite actions, and shared scripts. |
| `METADATA` | [metadata.md](metadata.md) | The shipped `metadata/` suite — the curated reachability metadata. |
| `TESTS` | [tests.md](tests.md) | The `tests/` suite that justifies every metadata entry. |
| `SKILL` | [../skills/](../skills/) | Agent review and automation skills. |

A kind whose home is a *folder* (`FS`, `AR`, `SKILL`) may spread its IDs
across several files in that folder; a kind whose home is a single *file* keeps
all of its IDs in that one file.

### Files at a glance

- [grund.md](grund.md) — `GRUND-repository-motivation`: why this repository exists.
- [goals.md](goals.md) — the outcome goals: tested metadata, broad version coverage, fresh releases, and no regressions.
- [functional-spec.md](functional-spec.md) — `FS-repository-functional-spec`: the system's behavior, requirements, the native-build-tools interface contract, library-version compatibility automation, and how each audience uses the repository.
- [architecture.md](architecture.md) — `AR-repository-architecture`: the component map and high-level implementation overview.
- [build-infra.md](build-infra.md) — `AR-build-infrastructure`: the two-layer Gradle build, convention plugins, and scaffolding.
- [tck.md](tck.md) — `TCK-test-harness`: the harness task groups.
- [e2e.md](e2e.md) — `E2E-infrastructure-tests`: `testInfra` and `testAllInfra`.
- [ci.md](ci.md) — `CI-repository-ci` and one citable declaration per workflow, action, and script.
- [metadata.md](metadata.md) — `METADATA-suite`: the published `metadata/` directory and its index/schema contracts.
- [tests.md](tests.md) — `TESTS-suite`: the `tests/` suite that justifies the metadata.

### Non-grounded contributor docs

These standard guides live alongside the grounded docs and are referenced from
the functional spec rather than carrying grund IDs:
[CONTRIBUTING.md](CONTRIBUTING.md), [DEVELOPING.md](DEVELOPING.md),
[REVIEWING.md](REVIEWING.md), [SECURITY.md](SECURITY.md), and
[CollectingMetadata.md](CollectingMetadata.md).

## Reading a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with the `grund`
CLI and read only as far as you need:

| Command | Returns |
| --- | --- |
| `grund <ID>` | The lead — the declaration body down to its first subsection. |
| `grund <ID> --brief` | Heading and first paragraph only. |
| `grund <ID> --toc` | The lead plus a map of the nested sections. |
| `grund <ID> --full` | The entire declaration body. |
| `grund refs <ID>` | Every site that cites the ID (add `--summary` for one line each). |
| `grund list [--kind FS,CI]` | All IDs, optionally filtered by kind. |

Run `grund refs <ID>` before renaming or moving a declaration, and `grund check`
from the repository root to validate every declaration and citation.
