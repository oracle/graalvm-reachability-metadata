# AGENTS

Guidelines for agents working in the real-use tests project.

## Required reading

- [docs/README.md](docs/README.md) — the documentation index and namespace
  rules.
- The real-use test contract in
  [docs/functional-spec/contract.md](docs/functional-spec/contract.md) before
  writing or changing a scenario.

## Rules

- The repository's grounding conventions apply here unchanged: the spec is the
  foundation, and a spec change lands as its own commit ahead of the code. See
  the "Grounding conventions for this repository" section of
  [../AGENTS.md](../AGENTS.md).
- Never cite under `src/`. Checkstyle holds the scenario projects to ASCII, so
  the `§` marker cannot appear in a test file.
- Work in this project never edits `metadata/` or `tests/`; everything a
  scenario needs beyond shipped metadata stays test-local.
- Cite repository facts as `§root/<ID>` and Forge facts as `§forge/<ID>`, and
  run `grund check` from the repository root.

<!-- BEGIN GRUND MANAGED BLOCK -->
## Grounding with grund (v7)

This project uses [`grund`](https://github.com/vjovanov/grund): every spec, goal, decision, and end-to-end test has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, FS, AR}`), cited with the marker `§` — e.g. `<§>FS-user-login.3.1` (the `FS-user-login` here is a shape illustration, not a real ID in this repo, hence the `<§>` escape). Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are ignored — `[reference] strict = true` is set in `grund.toml`, so only `§`-prefixed citations are checked.

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead (heading-less, cut at the first child section). The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
- `grund list` / `grund list --kind FS,AR` — discover IDs if you get lost

### Project map

- [GRUND](docs/grund.md): Why: real-use tests motivation
- [GOAL](docs/goals.md): Where: real-use tests direction and outcomes
- [FS](docs/functional-spec): What the real-use suite and its generation workflow must do
- [AR](docs/architecture): How the real-use suite, its harness tasks, and its Rhei template are wired
- [.agent-grounds/rhei/](.agent-grounds/rhei): The Rhei template and the programs its states run
- [src/](src): Per-coordinate real-use test projects

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>.

- [`forge`](../forge/AGENTS.md): Automation subproject that turns labeled issues into reviewed metadata pull requests
- [`real-use-tests`](AGENTS.md): Realistic library scenarios run on the JVM and Native Image against the shipped metadata
- [`root`](../AGENTS.md): Repository for shared GraalVM reachability metadata, tests, and release infrastructure

### Declarations and citations

Declarations are heading lines `# FS-user-login: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// FS-user-login: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. Plain headings or bold labels are fine for non-citable local structure. One doc-comment may declare multiple IDs (e.g. an `AR-` and an `FS-` on the same class) — each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Marker = live citation.** A `§`-prefixed token resolves and is checked wherever it appears — including inside Markdown backticks. To mention an ID without citing it, write `<§><ID>`, omit the marker, or use a fenced code block.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 20 lines; ≤ 100 columns.
- **Always cite the most-specific point.**

### Citation directions

- **GOAL** must cite GRUND.
- **FS** must cite GOAL or FS.
- **AR** must cite FS or GOAL.
- **.agent-grounds/rhei/** must cite FS; should cite AR.
- **code** (Loose files outside a kind home) must cite FS; should cite AR.
Unlisted kinds and pairs are fine.

### Clickable citations

On repository web surfaces, link `§<ID>` to the PR branch in PR bodies, the reviewed commit in reviews, an exact commit for permalinks, and the default branch otherwise; fall back to plain when unsure.
<!-- END GRUND MANAGED BLOCK -->
