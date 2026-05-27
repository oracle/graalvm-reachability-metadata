# AGENTS

Minimal guidelines for automation agents contributing to this repository.

Purpose: ensure safe, focused, and reviewable changes.

## Required Reading

Agents MUST read and strictly adhere to the following before making any changes:

- [Functional Spec](docs/functional-spec.md) — what the system must do.
- [Architecture](docs/architecture.md) — how the system is structured.

## Do
- Read README.md and DEVELOPING.md before acting.
- Before adding code, search the repository to see whether the behavior already exists or belongs in an existing module. Prefer `rg` for code and file discovery.
- Use the module map below to decide where code belongs before editing.
- Run Forge E2E tests only when the user explicitly asks. When asked, follow `docs/e2e.md` and treat `forge_metadata.py` runs as the source of truth instead of mock-only or direct-driver tests.
  §E2E-forge-workflow-testing
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
- Don't duplicate helpers for path resolution, strategy loading, metrics writing, source-context preparation, style checks, or GitHub/Git utilities without first checking the existing shared modules.
- Don't use `try/catch` if `catch` block is going to be empty. Use it only when you need to handle errors with custom messages or fallback logic.

## Before Coding
- Search for existing implementations before creating new functions, classes, scripts, or prompt-building logic.
- If the change is reusable across workflows, agents, or pipelines, move it to a shared module instead of copying it into a workflow script.
- If the change only alters wording or agent instructions, prefer `prompt_templates/` or `strategies/predefined_strategies.json` over Python logic.

### Module Map
- `forge_metadata.py`: control-plane dispatcher that claims GitHub issues, dispatches the matching workflow driver, and updates project status. Do not pile shared workflow logic here. §AR-forge-control-plane
- `ai_workflows/drivers/`: workflow drivers for major automation flows such as new-library support, javac fixes, and native-image-run fixes. Put deterministic setup, working-directory preparation, branch/context setup, and metrics finalization here; do not make Codex decide this plumbing during generated runs. §WF-forge-workflow-drivers
- `ai_workflows/core/`: reusable workflow engine implementations. Put iteration logic, workflow decision-making, and prompt sequencing here. §WF-forge-workflow-engine
- `ai_workflows/agents/`: agent integrations and transport adapters. Put agent specific session, prompt, fork, and logging behavior here, not in workflow scripts. §AR-forge-strategy-agent-boundary
- `git_scripts/`: Git and GitHub automation for commits, branches, PR creation, and stats formatting. Shared GitHub or git helpers belong in `git_scripts/common_git.py`. §GIT-forge-publication
- `utility_scripts/`: shared support code used across workflows and pipelines. Check here first before adding helper logic anywhere else. §WF-forge-workflow-architecture
- `benchmarks/`: benchmark campaign configuration and runner for comparing generation strategies across several new-library support targets. §BENCH-forge-generation-benchmarking
- `prompt_templates/`: prompt text only. Put instruction wording here when behavior does not require Python changes. §WF-forge-workflow-strategy-config
- `strategies/predefined_strategies.json`: declarative workflow configuration such as strategy names, prompt files, parameters, model, and agent selection. §STRAT-workflow-strategy-registry
- `schemas/`: JSON schemas for persisted outputs and configuration files. Update these when changing output contracts.
  §FS-durable-generation-logs
- `docs/`: project documentation, specifications, and testing specifications.
  §WF-forge-workflow-system

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

## Grounding with grund (v2)

This project uses [`grund`](https://github.com/vjovanov/grund): every spec, goal, decision, and end-to-end test has a stable ID `<KIND>-<slug>[.<section>]` (`KIND ∈ {GRUND, GOAL, AR, FS, DW, STRAT, ORCH, GIT, WF, E2E, BENCH, ROADMAP}`), cited with the marker `§` — e.g. `FS-user-login.3.1` (the `FS-user-login` here is a shape illustration, not a real ID in this repo). Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are ignored — `[reference] strict = true` is set in `.agents/grund.toml`, so only `§`-prefixed citations are checked.

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead (heading-less, cut at the first child section). The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
- `grund list` / `grund list --kind FS,AR` — discover IDs if you get lost

### Project map

- [GRUND](docs/grund.md): Why: Forge motivation
- [GOAL](docs/goals.md): Where: Forge direction and outcomes
- [AR](docs): Forge architecture and technical structure
- [FS](docs): Forge functional behavior and requirements
- [DW](docs/do-work.md): Forge do-work loop architecture
- [STRAT](docs/strategies.md): Forge strategy configuration architecture
- [ORCH](docs/orchestration-scripts.md): Forge orchestration scripts behavior and architecture
- [GIT](docs/git-scripts.md): Forge git scripts publication behavior and architecture
- [WF](docs/workflows): Forge workflow specifications and operating rules
- [BENCH](docs/benchmarking.md): Forge benchmark specifications
- [ROADMAP](docs/roadmap.md): Forge implementation roadmap

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `.agents/grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>.

- `forge` → [AGENTS.md](AGENTS.md)
- `root` → [../AGENTS.md](../AGENTS.md)

### Declarations and citations

Declarations are heading lines `# FS-user-login: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// FS-user-login: …` directly. Numbered headings inside a declaration are citable sections: use depth-matching headings (`## 1. …`, `### 1.1 …`, etc.) so `§<ID>.1` / `§<ID>.1.1` resolve; mismatched heading depth is a `grund check` error. Plain headings or bold labels are fine for non-citable local structure. One doc-comment may declare multiple IDs (e.g. an `AR-` and an `FS-` on the same class) — each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 3 lines; ≤ 100 columns.
- **Always cite the most-specific point.**
- **Citations climb to reasons (grund.md).** Goals cite reasons, specs cite goals; architecture cites specs; code and executable tests cite specs.
