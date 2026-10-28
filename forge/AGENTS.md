# AGENTS

Minimal guidelines for automation agents contributing to this repository.

Purpose: ensure safe, focused, and reviewable changes.

## Required Reading

Agents MUST read and strictly adhere to the following before making any changes:

- [Functional Spec](docs/functional-spec/functional-spec.md) — what the system must do.
- [Architecture](docs/architecture/architecture.md) — how the system is structured.

## Do
- Read README.md and DEVELOPING.md before acting.
- Before adding code, search the repository to see whether the behavior already exists or belongs in an existing module. Prefer `rg` for code and file discovery.
- Use the module map below to decide where code belongs before editing.
- Follow project style.
- When making AI-powered scripts, ensure prompts are clear and concise for the LLM.
  §GOAL-forge-direction
- Ask for clarification when requirements are ambiguous.
- Always use the `os` library for path resolution so they are OS-agnostic.
- Write only meaningful comments related to code logic, do not explain your reasoning.
- When writing error messages use `print` in this form: `print("ERROR: <error-msg>", file=sys.stderr)`.

## Don't
- Don't reformat unrelated files or introduce sweeping refactors.
- Don't add new logic to an entry-point script just because that is where you found the flow. Put reusable behavior in the module that owns that concern.
- Don't duplicate helpers for path resolution, metrics writing, source-context preparation, style checks, or GitHub/Git utilities without first checking the existing shared modules.
- Don't use `try/catch` if `catch` block is going to be empty. Use it only when you need to handle errors with custom messages or fallback logic.

## Before Coding
- Search for existing implementations before creating new functions, classes, scripts, or prompt-building logic.
- If the change is reusable across workflows, agents, or pipelines, move it to a shared module instead of copying it into a workflow script.
- If the change only alters wording or agent instructions, prefer `prompt_templates/` or the template's `states.yaml` over Python logic.

### Module Map
- `forge_metadata.py` and `run-forge.sh`: the launcher. It fetches, claims, checks the issue form, and starts one Rhei run; it holds no workflow logic. §AR-forge-control-plane
- `.agent-grounds/rhei/templates/forge-metadata/`: the issue workflow. States, routes, and agent instructions live in `states.yaml`. §AR-forge-workflow-pipeline.3
- `.agent-grounds/rhei/programs/`: the programs the template's states run. Put deterministic setup, build runs, routing decisions, and publication here, never in an agent prompt. §AR-forge-workflow-pipeline.4
- `dispatcher/`: the claim, the issue-form gate, the project board, and the launcher's review of published pull requests. §FS-published-pr-review
- `ai_workflows/agents/`: the Pi and Claude Code adapters the code coverage workflow still drives. §AR-forge-strategy-agent-boundary
- `git_scripts/`: Git and GitHub automation for branches, descriptors, and stats formatting. Shared GitHub or git helpers belong in `git_scripts/common_git.py`. §AR-forge-publication
- `utility_scripts/`: shared support code the programs and launchers import. Check here first before adding helper logic anywhere else. §AR-forge-workflow-boundary
- `benchmarks/`: the code coverage benchmark. §FS-code-coverage-benchmarking
- `prompt_templates/`: prompt text only. Put instruction wording here when behavior does not require Python changes. §AR-forge-workflow-prompts
- `schemas/`: JSON schemas for persisted outputs and configuration files. Update these when changing output contracts.
  §FS-durable-generation-logs
- `docs/`: project documentation, specifications, and testing specifications.
  §AR-forge-architecture

## Style
- In Markdown files always use backticks to format code and commands.
- 
## PR Description Rules
- When the user asks to create or prepare a PR, review the current conversation before writing the PR description.
- If the conversation contains design decisions, tradeoffs, follow-up concerns, add a `### Open question` section to the PR description.
- Use `### Open question` for items that still need reviewer input, confirmation, or visibility. Do not move already-settled decisions there.
- If the user explicitly tells you what to include in `### Open question`, include it even if it was not part of the earlier discussion.
- Summarize each open question briefly and concretely so a reviewer can understand the decision point without re-reading the whole conversation.
- If there are no open questions or user-requested notes for reviewers, omit the section instead of adding an empty heading.

<!-- BEGIN GRUND MANAGED BLOCK -->
## Grounding with grund (block v39)

This project uses [`grund`](https://github.com/agent-grounds/grund): every spec, goal, decision, and end-to-end test has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, AR, FS, ROADMAP}`), cited with the marker `§` — e.g. `<§>FS-user-login.3.1`, where the ID is a shape illustration rather than a real one in this repo, which is what the `<§>` escape says. Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are ignored — `[reference] strict = true` is set in `grund.toml`, so only `§`-prefixed citations are checked.

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead (heading-less, cut at the first child section). The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
  After the rename or move, edit the declaration and affected authored citation IDs, then run `grund fmt --write` from the config/project root or workspace root. When cross-reference emission applies, it re-derives generated Markdown link destinations and anchors; the root-scoped run also updates eligible section locks. Run `grund check` and `grund fmt --check`, then commit the lock changes. Locks are keyed by ID, so a file move alone does not rename one. fmt does not replace authored IDs, repair arbitrary URLs, or silently approve a protected section move; fix citations or leave a pointer for that move first. See [Maintaining declarations](https://github.com/agent-grounds/grund/blob/main/docs/user-facing/structure.md#maintaining-declarations).
- `grund list` / `grund list --kind FS,AR` — discover IDs if you get lost
- `grund list --unused` — the declarations nothing cites yet, in one call
- `grund list --size=words --top 10` — find heavy leads and move detail into citable child points before reading them in full
- `grund list --glob '<KIND>-*' --total` — count the coordinates a pattern selects; `grund show --batch --glob '<KIND>-*' --toc --format json` then reads each one's section map in the same scan, from `.result.sections[]` (`path`, `title`, `depth`). `*` matches within one component, `**` any number of whole components, and `{a,b}` either alternative. More recipes: [Querying grund](https://github.com/agent-grounds/grund/blob/main/docs/user-facing/querying.md).

### Project map

- [GRUND](docs/grund.md): Why: Forge motivation
- [GOAL](docs/goals.md): Where: Forge direction and outcomes
- [AR](docs/architecture): Forge architecture and technical structure
- [FS](docs/functional-spec): Forge functional behavior and requirements
- [ROADMAP](docs/roadmap.md): Forge implementation roadmap
- [prompt_templates/](prompt_templates): Agent prompt templates the workflows render
- [examples/](examples): Worked examples of a workflow run
- [ai_workflows/](ai_workflows): Agent adapters the code coverage workflow runs
- [git_scripts/](git_scripts): Git and GitHub automation: commits, branches, PRs, stats
- [utility_scripts/](utility_scripts): Shared support code used across workflows and pipelines
- [benchmarks/](benchmarks): The code coverage benchmark
- [tests/](tests): Unit and integration tests for the Python modules
- [.agent-grounds/rhei/](.agent-grounds/rhei): The Rhei templates and the programs their states run
- [schemas/](schemas): JSON Schemas for metrics and persisted outputs

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>, one alias segment per workspace level.

- [`real-use-tests`](../real-use-tests/): Realistic library scenarios run on the JVM and Native Image against the shipped metadata
- [`root`](../): Repository for shared GraalVM reachability metadata, tests, and release infrastructure

### Declarations and citations

Declarations are heading lines `# <ID>: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// <ID>: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. In a declaration body, Markdown or doc-comment, a heading may instead write only the component it adds and take the rest from the heading one level up: `### .1. Scope` under `## 3. Third` declares `§<ID>.3.1`, the one coordinate it is cited and reported by. After citing such a heading, run `grund fmt --write` so the section lock records it, and commit the lock. Every non-declaration heading inside a Markdown declaration body must carry a numbered or enabled named section path; file titles, headings that close the body, fenced examples, non-ATX text, and source doc-comments stay exempt, while bold labels remain the non-citable alternative. One doc-comment may declare multiple IDs (e.g. an `AR-` and an `FS-` on the same class) — each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Marker = live citation.** A `§`-prefixed token resolves and is checked wherever it appears — including inside Markdown backticks. To mention an ID without citing it, write `<§><ID>`, omit the marker, or use a fenced code block.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 20 lines; ≤ 100 columns. A note is one comment block: a blank line splits it, an empty comment line does not. Doc-comments (`///`, `//!`, `/** */`, a docstring, a Go, Ruby, shell, SQL or Lisp comment right above a definition) are documentation, not notes: they are never measured, so cite in-sentence there.
- **Always cite the most-specific point.**

### Citation directions

`must`/`never` are `grund check` errors; `should`/`avoid` are suggestions (`grund check --suggestions`).

- Each **GOAL** declaration must cite GRUND or GOAL.
- Each **AR** declaration must cite FS or GOAL.
- Each **FS** declaration must cite GOAL or FS.
- Each **ROADMAP** declaration should cite GOAL, FS, or AR.
- Each file in **prompt_templates/** that cites anything should cite FS in any project, AR, or GOAL. A file there that cites nothing is not checked; `require_grounding = true` on its `[[kinds]]` row makes it a finding.
- Each file in **examples/** that cites anything should cite FS or AR. A file there that cites nothing is not checked; `require_grounding = true` on its `[[kinds]]` row makes it a finding.
- Each file in **ai_workflows/** that cites anything should cite FS or AR. A file there that cites nothing is not checked; `require_grounding = true` on its `[[kinds]]` row makes it a finding.
- Each file in **git_scripts/** that cites anything should cite FS or AR. A file there that cites nothing is not checked; `require_grounding = true` on its `[[kinds]]` row makes it a finding.
- Each file in **utility_scripts/** that cites anything should cite FS or AR. A file there that cites nothing is not checked; `require_grounding = true` on its `[[kinds]]` row makes it a finding.
- Each file in **benchmarks/** that cites anything should cite FS or AR. A file there that cites nothing is not checked; `require_grounding = true` on its `[[kinds]]` row makes it a finding.
- Each file in **tests/** that cites anything should cite FS or AR. A file there that cites nothing is not checked; `require_grounding = true` on its `[[kinds]]` row makes it a finding.
- Each file in **.agent-grounds/rhei/** that cites anything must cite FS or AR. A file there that cites nothing is not checked; `require_grounding = true` on its `[[kinds]]` row makes it a finding.
- Each source file outside the Project map (**code**: Loose scripts and plugins outside a kind home) that cites anything must cite FS; should cite AR.
Anything not listed above is allowed.

### Clickable citations

On repository web surfaces, link `§<ID>` to the PR branch in PR bodies, the reviewed commit in reviews, an exact commit for permalinks, and the default branch otherwise; fall back to plain when unsure.
<!-- END GRUND MANAGED BLOCK -->
