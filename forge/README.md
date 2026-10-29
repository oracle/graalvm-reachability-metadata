# Forge

Forge automates reachability-metadata maintenance for community
libraries: generating new library support, fixing version-bump failures,
reviewing generated PRs, and recording run metrics.
§FS-forge-issue-resolution-goal

This directory lives inside the `graalvm-reachability-metadata` checkout. The
reachability repository is the parent directory (`..`) and Forge metrics are
stored under this directory.
§FS-durable-generation-logs

## Primary Entry Point

Use `run-forge.sh` for unattended operation (`do-work.sh` only starts it). Each
cycle checks the host, fetches the issue queues with `gh issue list`, claims the
most urgent eligible issue, checks its form, and runs its `forge-metadata` Rhei
workspace under `runs/` with the browser dashboard. Every third run, and when
nothing can be claimed, it reviews up to three open Forge pull requests whose
checks have finished, with the `--review-agent` (default
`claude-code[high]:opus`): a green one is approved and auto-merged, and any
other ends in a rerun, a pushed fix, or an issue and the `human-intervention`
label. A terminal outcome adds `reviewed`, so the pull request is not reviewed
again. `python3 forge_metadata.py --review --dry-run --pr <n> [--head <sha>]`
replays the review of one pull request, open or closed, and writes nothing on
GitHub.
§AR-forge-workflow-pipeline.1 §FS-published-pr-review

```console
./run-forge.sh [--setup-agent TARGET] [--worker-agent TARGET] [--review-agent TARGET]
    [--label LABEL[:COUNT]]... [--take-blocked-issues] [-- RHEI_RUN_OPTIONS...]
```

The agent targets are the only run-time choice
(§FS-forge-agent-runtime-selection): `--setup-agent` defaults to
`claude-code[medium]:sonnet` and must be a Claude Code target, because setup
needs web search; `--worker-agent` defaults to
`pi[high]:openai-codex/gpt-5.6-luna` and serves the cover and fix states;
`--review-agent` defaults to `claude-code[high]:opus` and must be a Claude Code
target, because the review needs the web and the GraalVM history. Codex and
OpenCode are not supported.

Without `--label`, every cycle fetches every queue. Each `--label LABEL[:COUNT]`
gives its queue COUNT runs in a row, one by default, and the queues take turns
in the order given; one with nothing to claim passes its turn to the next
(§AR-forge-workflow-pipeline.1). This serves two new-library requests, one
update request, and one `fails-java-run` issue, then starts over:

```console
./run-forge.sh --label library-new-request:2 --label library-update-request --label fails-java-run
```

To see what one cycle would claim without claiming anything:

```console
python3 forge_metadata.py --dry-run
```

Before every claim, the launcher validates the host requirements for the tools,
environment, GitHub access, Docker, and the two agents.
§FS-forge-host-requirements

A failed check stops the launcher, with one exception: once the check has passed,
a failure that clears on its own, such as a GitHub rate limit or an endpoint
that cannot be reached, makes it claim nothing and check again after a backoff
from one minute to fifteen. A failed cycle waits on the same backoff.
§FS-forge-host-requirements.1

## Setup

Run commands from this directory unless a command says otherwise.
§AR-forge-workflow-boundary

```console
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
```

Required local tools depend on the work queue being processed:

- `gh` for issue, PR, and review automation.
- `rhei` 0.6.1 exactly, for every workflow; the host gate refuses any other
  release, including source builds. Install it from
  [the v0.6.1 release](https://github.com/agent-grounds/rhei/releases/tag/v0.6.1).
- `pi` and `claude` for the worker and setup agents.
- `GRAALVM_HOME`, or `JAVA_HOME`, pointing at a GraalVM of JDK 25 or newer with
  Native Image, its agent, and the reachability-metadata schema.
§FS-forge-host-requirements

Local Forge automation must run without `sudo`. Local CI verification fails
fast instead of prompting for an administrator password if a command or script
would require elevated privileges.
§FS-local-ci-equivalent-verification

Forge scopes `GRADLE_USER_HOME` per reachability-repo worktree so worktrees do
not share Gradle daemons, and shares downloaded dependencies between them
through Gradle's read-only dependency cache (`GRADLE_RO_DEP_CACHE`), published
from the checkout's long-lived Gradle user home before each run creates its
first worktree. Gradle wrapper distributions are shared under the system temp
directory. Set `FORGE_GRADLE_DISTRIBUTIONS_HOME` to override that cache
location, or `FORGE_GRADLE_USER_HOME` to move the checkout's Gradle user home,
under which the worktree homes and the published dependency cache then live.
§AR-forge-workflow-boundary

## Manual Workflows

The code coverage workflow has its own launchers.
§AR-code-coverage-improvement

To select the highest-priority eligible `code-coverage-improvement` issue and
start its Rhei workspace with the terminal TUI and browser dashboard:

```console
./run-code-coverage-improvement.sh
```

Pass `rhei run` options after `--`, for example
`./run-code-coverage-improvement.sh -- --parallel 2`. The launcher requires
GitHub CLI 2.39.0 or newer, validates the coverage host requirements before
querying the issue queue, and validates the selected issue's exact-version test
project before invoking Rhei.
§FS-forge-host-requirements §AR-code-coverage-improvement.2

To inspect or execute the fixed code coverage benchmark matrix, use the
benchmark launcher. With no selection flags it selects all 90 cells; filters
compose and accept multiple values:

```console
./run-code-coverage-benchmark.sh --dry-run
./run-code-coverage-benchmark.sh --library-index 1 3 5
./run-code-coverage-benchmark.sh --agent claude-code --model sonnet-5
./run-code-coverage-benchmark.sh --thinking high
```

Each cell keeps its Rhei workspace below
`local_repositories/code_coverage_benchmarks/`, immediately appends compact
metrics to `../code-coverage-benchmarks/<group>/<artifact>/<version>.json` on a
descriptor-backed `ai/**` branch, and lets trusted Actions open the result PR.
It removes only its disposable source worktree after the branch is durable or
the result is already merged. Retry preserved
unpublished results without rerunning coverage:

```console
./run-code-coverage-benchmark.sh retry-pending
```

§FS-code-coverage-benchmarking

To run one issue the launcher already claimed, see
`.agent-grounds/rhei/templates/forge-metadata/README.md`. Prompt text lives in
`prompt_templates/` and the template's `states.yaml`; persisted output
contracts live in `schemas/`.
§AR-forge-workflow-prompts

## Repository Layout

```console
forge/
├─ .agent-grounds/rhei/
│  ├─ templates/
│  └─ programs/
├─ ai_workflows/agents/
├─ benchmarks/
├─ dispatcher/
├─ git_scripts/
├─ utility_scripts/
├─ docs/
├─ prompt_templates/
└─ schemas/
```

- `run-forge.sh`: the launcher loop; `do-work.sh` starts it.
- `forge_metadata.py`: one launcher cycle: fetch, claim, form check, and the Rhei run.
- `.agent-grounds/rhei/templates/forge-metadata/`: the issue workflow's Rhei template.
- `.agent-grounds/rhei/programs/`: the programs its states run, and their tests.
- `dispatcher/`: the claim, the issue-form gate, the board, and the review of published pull requests.
- `ai_workflows/agents/`: the Pi and Claude Code adapters the code coverage workflow still uses.
- `benchmarks/`: the code coverage benchmark. §FS-code-coverage-benchmarking
- `git_scripts/`: branch, descriptor, and publication helpers.
- `utility_scripts/`: shared support code.
- `docs/`: specifications and architecture.
§AR-forge-architecture

See `DEVELOPING.md` for command-level workflow details,
`docs/functional-spec/functional-spec.md` for the functional specification, and
`docs/architecture/workflow-pipeline.md` for the issue workflow.
