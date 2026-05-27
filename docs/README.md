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

## Grund tags in the documentation

### IDs

Every grounded fact has a stable ID of the form `<KIND>-<slug>[.<section>]`:

- `<KIND>` is one of the prefixes above and selects the home file or folder.
- `<slug>` is a stable lowercase-kebab name; it does not change when the prose
  around it is edited.
- `.<section>` optionally points at a numbered subsection inside a declaration
  (`.1`, `.2.1`, ...).

For example, `GOAL-tested-metadata` names a repository goal, and
`FS-repository-functional-spec.5.2` points at the test requirements subsection
of the functional spec.

### Declarations

A *declaration* defines an ID. In Markdown it is a heading whose text starts
with the ID, followed by the body that states the fact:

```
# GOAL-<slug>: <one-line title>

Body that states the fact.

## 1. <citable subsection>
## 2. <another citable subsection>
```

Numbered headings inside a declaration (`## 1.`, `### 1.1`) are citable as
`<ID>.1` / `<ID>.1.1`; their heading depth must match the number depth. Plain
headings are fine for non-citable local structure. In source code the same
declaration is written in a doc-comment with the leading `#` dropped (for
example a `/// <ID>: ...` or `# <ID>: ...` comment), and a one-line stub in the
kind home links to it.

### Citations

A *citation* references a declared fact, written with the `§` marker (type `$$`
in a grund-aware editor to get `§`). Place it where the claim is made:

- In docs and prose, after the sentence it supports:
  `§GOAL-tested-metadata`.
- Inline in source code, on the line the rule applies to.
- Across namespaces with an alias prefix: from the repository root cite Forge
  facts as `§forge/<ID>`, and Forge docs cite root facts as `§root/<ID>`.

Citations climb toward reasons: goals cite the motivation in [grund.md](grund.md),
specs cite goals, architecture cites specs, and code and tests cite specs.
Always cite the most specific point that supports the claim, and keep inline
notes short (≤ 1 line preferred, ≤ 100 columns).

Only `§`-prefixed tokens are checked — `[reference] strict = true` in
[../.agents/grund.toml](../.agents/grund.toml) — so a bare ID-shaped word in
prose is ignored.

### Reading a citation

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
