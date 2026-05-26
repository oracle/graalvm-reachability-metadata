# Forge documentation

This directory holds Forge's grounded documentation: motivation, direction,
functional specifications, architecture, workflow specs, and test specs. Every
document follows the [`grund`](https://github.com/vjovanov/grund) convention, so
each fact has a stable ID and can be cited from prose, other docs, and source
code.

For the authoritative grounding rules and the agent contribution contract, see
[../AGENTS.md](../AGENTS.md). For how to run Forge, see [../README.md](../README.md).

## Directory structure

Documents are organized by grund *kind*. A kind groups facts of one type (a
goal, a functional requirement, an architecture decision, …) and has a fixed
*home* — a single file or a folder — declared in
[../.agents/grund.toml](../.agents/grund.toml). An ID always lives in its kind's
home, so the prefix tells you which file to open.

| Kind | Home | Holds |
| --- | --- | --- |
| `GRUND` | [grund.md](grund.md) | Why Forge exists — the motivation everything else climbs back to. |
| `GOAL` | [goals.md](goals.md) | Where Forge is headed — direction and measurable outcomes. |
| `FS` | [functional-spec.md](functional-spec.md) | What Forge must do — contributor-facing functional behavior. |
| `AR` | [architecture.md](architecture.md), [agent.md](agent.md) | How Forge is structured — boundaries, components, extension points. |
| `DW` | [do-work.md](do-work.md) | The unattended do-work loop architecture. |
| `STRAT` | [strategies.md](strategies.md) | Predefined strategy configuration architecture. |
| `ORCH` | [orchestration-scripts.md](orchestration-scripts.md) | Orchestration script behavior and architecture. |
| `GIT` | [git-scripts.md](git-scripts.md) | Git and PR publication behavior and architecture. |
| `WF` | [workflows/](workflows/) | Per-workflow specifications and operating rules. |
| `E2E` | [e2e.md](e2e.md) | End-to-end workflow test specification. |
| `BENCH` | [benchmarking.md](benchmarking.md) | Generation benchmarking specification. |
| `ROADMAP` | [roadmap.md](roadmap.md) | Planned, not-yet-implemented work. |

A kind whose home is a *folder* (`AR`, `FS`, `WF`) may spread its IDs across
several files in that folder; a kind whose home is a single *file* keeps all of
its IDs in that one file.

### Files at a glance

- [grund.md](grund.md) — `GRUND-forge-motivation`: why Forge exists.
- [goals.md](goals.md) — `GOAL-forge-direction` and the outcome goals beneath it.
- [functional-spec.md](functional-spec.md) — `FS-forge-functional-spec`: top-level functional spec and the workflow-spec catalog.
- [architecture.md](architecture.md) — `AR-forge-architecture`: control plane, workflow boundaries, and extension points.
- [agent.md](agent.md) — `AR-agent-api`: the agent API and its Pi implementation.
- [do-work.md](do-work.md) — `DW-do-work-loop`: the long-running worker loop.
- [strategies.md](strategies.md) — `STRAT-workflow-strategy-registry`: strategy registry, contract, and fields.
- [orchestration-scripts.md](orchestration-scripts.md) — `ORCH-forge-orchestration-spec`.
- [git-scripts.md](git-scripts.md) — `GIT-forge-publication`: PR eligibility, body, issue linking, and publication.
- [benchmarking.md](benchmarking.md) — `BENCH-forge-generation-benchmarking`.
- [e2e.md](e2e.md) — `E2E-forge-workflow-testing`: hermetic and live end-to-end testing.
- [roadmap.md](roadmap.md) — `ROADMAP-forge-implementation` and the planned improvements beneath it.

### The `workflows/` subtree

[workflows/](workflows/) is the `WF` home: every ID in this subtree — including
the workflow-system and per-workflow architecture — uses the `WF` prefix. The
shared overview file carries both the behavioral contract and the architecture;
each individual workflow is one file:

- [workflows/architecture.md](workflows/architecture.md) — `WF-forge-workflow-system` and `WF-forge-workflow-architecture`: shared workflow behavior, engines, and strategy configuration.
- [workflows/workflow-drivers.md](workflows/workflow-drivers.md) — `WF-forge-workflow-drivers`.
- [workflows/dynamic-access.md](workflows/dynamic-access.md) — `WF-dynamic-access-workflow`.
- [workflows/improve-library-coverage.md](workflows/improve-library-coverage.md) — `WF-improve-library-coverage`.
- [workflows/java-fail-fix.md](workflows/java-fail-fix.md) — `WF-java-fail-fix-workflow`.
- [workflows/native-image-run-fix.md](workflows/native-image-run-fix.md) — `WF-native-image-run-fix-workflow`.
- [workflows/native-metadata-tracing.md](workflows/native-metadata-tracing.md) — `WF-native-metadata-tracing`.
- [workflows/code-coverage-improvement.md](workflows/code-coverage-improvement.md) — `WF-code-coverage-improvement` and `WF-code-coverage-improvement-architecture`: planned code coverage workflow, behavior and architecture in one file.

A workflow file may declare both its behavioral contract and its own
architecture; both use the `WF` prefix, as the shared overview and the planned
code coverage workflow above do.

## Grund tags in the documentation

### IDs

Every grounded fact has a stable ID of the form `<KIND>-<slug>[.<section>]`:

- `<KIND>` is one of the prefixes above and selects the home file or folder.
- `<slug>` is a stable lowercase-kebab name; it does not change when the prose
  around it is edited.
- `.<section>` optionally points at a numbered subsection inside a declaration
  (`.1`, `.2.1`, …).

For example, `GOAL-forge-direction` names the top-level direction goal, and
`E2E-forge-workflow-testing.4` points at the fourth numbered section of the
end-to-end test spec.

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
example a `/// <ID>: …` or `# <ID>: …` comment), and a one-line stub in the kind
home links to it.

### Citations

A *citation* references a declared fact, written with the `§` marker (type `$$`
in a grund-aware editor to get `§`). Place it where the claim is made:

- In docs and prose, after the sentence it supports: `§GOAL-forge-direction`.
- Inline in source code, on the line the rule applies to.
- Across namespaces with an alias prefix: from the repository root this goal is
  cited as `§forge/GOAL-forge-direction`, and Forge docs cite a root fact as
  `§root/<ID>`.

Citations climb toward reasons: goals cite the motivation in [grund.md](grund.md),
specs cite goals, architecture cites specs, and code and tests cite specs.
Always cite the most specific point that supports the claim, and keep inline
notes short (≤ 1 line preferred, ≤ 100 columns).

Only `§`-prefixed tokens are checked — `[reference] strict = true` in
[../.agents/grund.toml](../.agents/grund.toml) — so a bare ID-shaped word in
prose is ignored.

### Reading a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with the `grund`
CLI from the `forge/` directory and read only as far as you need:

| Command | Returns |
| --- | --- |
| `grund <ID>` | The lead — the declaration body down to its first subsection. |
| `grund <ID> --brief` | Heading and first paragraph only. |
| `grund <ID> --toc` | The lead plus a map of the nested sections. |
| `grund <ID> --full` | The entire declaration body. |
| `grund refs <ID>` | Every site that cites the ID (add `--summary` for one line each). |
| `grund list [--kind FS,AR]` | All IDs, optionally filtered by kind. |

Run `grund refs <ID>` before renaming or moving a declaration, and `grund check`
from the workspace root to validate every declaration and citation.
