# Forge documentation

This directory holds Forge's grounded documentation: motivation, direction,
functional specifications, architecture, and workflow specs. Every
document follows the [`grund`](https://github.com/vjovanov/grund) convention, so
each fact has a stable ID and can be cited from prose, other docs, and source
code.

For the authoritative grounding rules and the agent contribution contract, see
[../AGENTS.md](../AGENTS.md). For how to run Forge, see [../README.md](../README.md).

## Directory structure

Documents are organized by grund *kind*. A kind groups facts of one type (a
goal, a functional requirement, an architecture decision, …) and has a fixed
*home* — a single file or a folder — declared in
[../grund.toml](../grund.toml). An ID always lives in its kind's
home, so the prefix tells you which file to open.

| Kind | Home | Holds |
| --- | --- | --- |
| `GRUND` | [grund.md](grund.md) | Why Forge exists — the motivation everything else climbs back to. |
| `GOAL` | [goals.md](goals.md) | Where Forge is headed — direction and measurable outcomes. |
| `FS` | [functional-spec/](functional-spec/README.md) | What Forge must do — contributor-facing functional behavior and the per-component contracts. |
| `AR` | [architecture/](architecture/README.md) | How Forge is structured — boundaries, components, extension points, and the per-component design. |
| `ROADMAP` | [roadmap.md](roadmap.md) | Planned, not-yet-implemented work. |

`AR` and `FS` are *folder* homes: they spread their IDs across every file in
that folder, and the file — not the prefix — tells you which component a
declaration belongs to.

`docs/` holds only the three documents that answer a question about Forge as a
whole — why (`GRUND`), where (`GOAL`), and what is planned (`ROADMAP`).
Everything else lives under [architecture/](architecture/README.md) or
[functional-spec/](functional-spec/README.md), and the directory tells you
whether a document describes structure or behavior.

### Files at a glance

- [grund.md](grund.md) — `GRUND-forge-motivation`: why Forge exists.
- [goals.md](goals.md) — `GOAL-forge-direction` and the outcome goals beneath it.
- [functional-spec/functional-spec.md](functional-spec/functional-spec.md) — `FS-forge-functional-spec`: top-level functional spec and the workflow-spec catalog.
- [functional-spec/continuation.md](functional-spec/continuation.md) — `FS-forge-run-continuation`: resuming a failed run at the phase that failed, and the continuation marker contract.
- [architecture/architecture.md](architecture/architecture.md) — `AR-forge-architecture`: control plane, workflow boundaries, and extension points.
- [architecture/agent.md](architecture/agent.md) — `AR-agent-api`: the agent API and its Pi implementation.
- [architecture/do-work.md](architecture/do-work.md) — `AR-do-work-loop`: the long-running worker loop.
- [architecture/orchestration-scripts.md](architecture/orchestration-scripts.md) — `AR-forge-orchestration`.
- [architecture/git-scripts.md](architecture/git-scripts.md) — `AR-forge-publication`: PR eligibility, body, issue linking, and publication.
- [functional-spec/strategies.md](functional-spec/strategies.md) — `FS-workflow-strategy-registry`: strategy registry, contract, and fields.
- [functional-spec/benchmarking.md](functional-spec/benchmarking.md) — `FS-forge-generation-benchmarking`.
- [roadmap.md](roadmap.md) — `ROADMAP-forge-implementation` and the planned improvements beneath it.

### A component still awaiting its split

[architecture/code-coverage-improvement.md](architecture/code-coverage-improvement.md)
keeps behavior and architecture in one document. It is
the only component documented that way. The split — behavior into the functional
spec, engine into [architecture/workflows.md](architecture/workflows.md), driver
into [architecture/drivers.md](architecture/drivers.md) — happens in a later
change, and the document says so in its opening paragraph. Cite `CC-` IDs
normally in the meantime; they are stable until that change renames them.

## Grund tags in the documentation

### IDs

Every grounded fact has a stable ID of the form `<KIND>-<slug>[.<section>]`:

- `<KIND>` is one of the prefixes above and selects the home file or folder.
- `<slug>` is a stable lowercase-kebab name; it does not change when the prose
  around it is edited.
- `.<section>` optionally points at a numbered subsection inside a declaration
  (`.1`, `.2.1`, …).

For example, `GOAL-forge-direction` names the top-level direction goal, and
`FS-forge-functional-spec.4` points at the fourth numbered section of the
functional spec.

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
[../grund.toml](../grund.toml) — so a bare ID-shaped word in
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
