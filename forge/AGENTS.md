# AGENTS

Minimal guidelines for automation agents contributing to this repository.

Purpose: ensure safe, focused, and reviewable changes.

## Required Reading

Agents MUST read and strictly adhere to the following before making any changes:

- [Functional Spec](docs/functional-spec.md) — what the system must do.
- [Architecture](docs/architecture.md) — how the system is structured.
- [ADRs](docs/adr/) — recorded architectural decisions; do not contradict them.

## Do
- Read README.md and DEVELOPING.md before acting.
- Before adding code, search the repository to see whether the behavior already exists or belongs in an existing module. Prefer `rg` for code and file discovery.
- Use the module map below to decide where code belongs before editing.
- When testing AI workflows, follow `docs/ai-workflow-testing.md` and treat real workflow runs as the source of truth instead of mock-only tests.
- Follow project style.
- When making AI-powered scripts, ensure prompts are clear and concise for the LLM.
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
- `forge_metadata.py`: top-level automation entry point that claims GitHub issues, dispatches the matching workflow, and updates project status. Do not pile shared workflow logic here.
- `ai_workflows/`: workflow entry points and orchestration for major automation flows such as new-library support, javac fixes, and native-image-run fixes.
- `ai_workflows/workflow_strategies/`: reusable workflow strategy implementations. Put iteration logic, workflow decision-making, and prompt sequencing here.
- `ai_workflows/agents/`: agent integrations and transport adapters. Put agent specific session, prompt, fork, and logging behavior here, not in workflow scripts.
- `git_scripts/`: Git and GitHub automation for commits, branches, PR creation, and stats formatting. Shared GitHub or git helpers belong in `git_scripts/common_git.py`.
- `utility_scripts/`: shared support code used across workflows and pipelines. Check here first before adding helper logic anywhere else.
- `prompt_templates/`: prompt text only. Put instruction wording here when behavior does not require Python changes.
- `strategies/predefined_strategies.json`: declarative workflow configuration such as strategy names, prompt files, parameters, model, and agent selection.
- `schemas/`: JSON schemas for persisted outputs and configuration files. Update these when changing output contracts.
- `docs/`: project documentation, specifications, and testing specifications.

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
