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

Use `do-work.sh` for unattended operation. It is a stable wrapper that forwards
all arguments to `do_up_to_date_work.sh`; the up-to-date worker owns argument
parsing, self-updates, queue processing, sleeping, and re-execing the latest
script before the next cycle.
§AR-do-work-loop

Before self-update or queue processing, and again at the start of every
work-starting `forge_metadata.py` invocation, Forge prints and validates the
deterministic host requirements for the tools, environment variables, filesystem
and network permissions, GitHub repository/project access, Docker, and agent
authentication its mode needs. A failed required check exits before Forge claims
or reviews anything; a `--review-pr` run is never asked for a GraalVM.
§FS-forge-host-requirements

The 25.0.x validation lane is pinned in `graalvm-versions.json`; update that
file when Forge should move to a newer 25.0.x release. The main and EA lanes are
checked against the latest published GA and EA release metadata at startup. Pass
`--graalvm-version-check warn` or `off` to run against a locally built Graal;
Native Image, its agent, and the reachability-metadata schema remain mandatory.

```console
./do-work.sh [options] [forge-branch]
```

Common options:

- `--branch BRANCH`: monitor a specific Forge branch on `origin`.
- `--new-limit N`: process up to `N` new-library tasks per cycle.
- `--javac-limit N`: process up to `N` Java compilation failure tasks per cycle.
- `--java-run-limit N`: process up to `N` JVM runtime failure tasks per cycle.
- `--ni-run-limit N`: process up to `N` Native Image runtime failure tasks per cycle.
- `--parallelism N`: run up to `N` issue workflows in parallel. Maximum: 4.
- `--review-limit N`: process up to `N` PR review tasks per label per cycle.
- `--agent-family {claude-code,pi,codex,opencode}`: select one backend for both
  agent roles; `--analysis-agent` and `--test-agent` override individual roles.
- `--random-offset`: start new-library issue scans at a random offset instead of the newest issues first.
- `--priority {high,priority,normal}`: process only the selected issue priority tier.
- `--user-requested-only`: fetch only user-requested issue queue items, excluding configured automation and maintainer authors.
- `--graalvm-version-check {strict,warn,off}`: how a GraalVM version mismatch is treated. Default: `strict`.
- `--once`: run a single update/work cycle through `do_up_to_date_work.sh` and exit.
- `--fail-fast`: return nonzero on the first unsuccessful work cycle.
- `--analysis-agent COMMAND --analysis-family FAMILY`: choose the analysis executable and adapter family.
- `--test-agent COMMAND --test-family FAMILY`: choose the test executable and adapter family.
- `--stop`: ask all Forge `do-work` loops for the current user to exit by creating `~/.metadata-forge-stop`.
- `--stop --branch BRANCH`: ask only loops monitoring `BRANCH` to exit, using a branch-scoped marker such as `~/.metadata-forge-stop.master`.
- `--clear-stop`: remove the matching global or branch-scoped stop marker so future `do-work` loops can run.
- `--clear-issue-caches`: delete local issue claim/search caches used by work-queue scanning and exit.

Examples:

```console
./do-work.sh --new-limit 1 --javac-limit 1
./do-work.sh --parallelism 2
DO_WORK_SLEEP_SECONDS=60 ./do-work.sh --branch master
./do-work.sh --user-requested-only --new-limit 1
./do-work.sh --once --agent-family codex --new-limit 1 --javac-limit 0 --java-run-limit 0 --ni-run-limit 0 --review-limit 0
FORGE_REVIEW_LABEL=library-new-request ./do-work.sh --review-limit 2
./do-work.sh --stop
./do-work.sh --stop --branch master
./do-work.sh --clear-stop
./do-work.sh --clear-stop --branch master
./do-work.sh --clear-issue-caches
```

The same limits can be controlled with environment variables such as
`FORGE_WORK_LIMIT`, `FORGE_JAVAC_WORK_LIMIT`, `FORGE_JAVA_RUN_WORK_LIMIT`,
`FORGE_NI_RUN_WORK_LIMIT`, `FORGE_PARALLELISM`, `FORGE_REVIEW_LIMIT`,
`FORGE_BULK_UPDATE_REVIEW_LIMIT`, `FORGE_USER_REQUESTED_ISSUES_ONLY`, and
`DO_WORK_SLEEP_SECONDS`. Set `FORGE_DO_WORK_STOP_FILE` to override the shared
stop marker path.
§AR-do-work-loop

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
- `pi` for Pi-agent strategies and automated style recovery.
- `codex` for Codex-agent strategies and metadata fixups.
- For issue work, set `GRAALVM_HOME`, `GRAALVM_HOME_25_0`, and
  `GRAALVM_HOME_LATEST_EA` to the exact versions printed by the host-requirement
  report. Each distribution must include Native Image, its agent, and the
  reachability-metadata schema. Review-only work needs only `JAVA_HOME` pointing
  to JDK 25.
§FS-forge-predefined-strategy-contract

Local Forge automation must run without `sudo`. Local CI verification fails
fast instead of prompting for an administrator password if a command or script
would require elevated privileges.
§FS-local-ci-equivalent-verification

Forge scopes `GRADLE_USER_HOME` per reachability-repo worktree so parallel
workers do not share Gradle daemons, but reuses one shared Gradle wrapper
distribution cache under the system temp directory. Set
`FORGE_GRADLE_DISTRIBUTIONS_HOME` to override that cache location, or
`FORGE_GRADLE_USER_HOME` to override the full Gradle user home.
§AR-forge-workflow-boundary

## Manual Workflows

The top-level worker delegates to these lower-level entry points. Use them
directly when debugging a single task or reproducing a failure.
§AR-forge-drivers

```console
python3 forge_metadata.py --help
python3 ai_workflows/drivers/add_new_library_support.py --coordinates <group:artifact:version>
python3 ai_workflows/drivers/improve_library_coverage.py --coordinates <group:artifact:version>
python3 ai_workflows/drivers/fix_java_fails.py --javac --coordinates <group:artifact:oldVersion> --new-version <newVersion>
python3 ai_workflows/drivers/fix_java_fails.py --java-run --coordinates <group:artifact:oldVersion> --new-version <newVersion>
python3 ai_workflows/drivers/fix_javac_fail.py --coordinates <group:artifact:oldVersion> --new-version <newVersion>
python3 ai_workflows/drivers/fix_java_run_fail.py --coordinates <group:artifact:oldVersion> --new-version <newVersion>
python3 ai_workflows/drivers/fix_ni_run.py --coordinates <group:artifact:oldVersion> --new-version <newVersion>
```

Strategies are declared in `strategies/predefined_strategies.json`. Prompt text
lives in `prompt_templates/`. Persisted output contracts live in `schemas/`.
§FS-workflow-strategy-registry

## Repository Layout

```console
forge/
├─ ai_workflows/
│  ├─ drivers/
│  ├─ core/
│  └─ agents/
├─ benchmarks/
├─ git_scripts/
├─ utility_scripts/
├─ docs/
├─ prompt_templates/
├─ schemas/
└─ strategies/
```

- `forge_metadata.py`: top-level issue and PR automation dispatcher.
- `do-work.sh`: stable wrapper around `do_up_to_date_work.sh`.
- `do_up_to_date_work.sh`: long-running up-to-date worker and queue processor.
- `ai_workflows/drivers/`: deterministic workflow entry points.
- `ai_workflows/core/`: registered workflow engines and shared orchestration.
- `ai_workflows/agents/`: backend-neutral agent adapters.
- `benchmarks/`: generation benchmark suites and runner. §FS-forge-generation-benchmarking
- `git_scripts/`: branch, commit, PR, and review helpers.
- `utility_scripts/`: shared support code.
- `docs/`: design notes, workflow specifications, and testing guidance.
§AR-forge-architecture

See `DEVELOPING.md` for command-level workflow details,
`docs/functional-spec/functional-spec.md` for the functional specification, and
`docs/architecture/architecture.md` for the architecture overview.
