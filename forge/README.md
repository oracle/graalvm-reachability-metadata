# Metadata Forge

Metadata Forge automates reachability-metadata maintenance for community
libraries: generating new library support, fixing version-bump failures,
reviewing generated PRs, and recording run metrics.

This directory lives inside the `graalvm-reachability-metadata` checkout. The
reachability repository is the parent directory (`..`) and Forge metrics are
stored under this directory.

## Primary Entry Point

Use `do-work.sh` for unattended operation. It is a stable wrapper that forwards
all arguments to `do_up_to_date_work.sh`; the up-to-date worker owns argument
parsing, self-updates, queue processing, sleeping, and re-execing the latest
script before the next cycle.

```console
./do-work.sh [options] [metadata-forge-branch]
```

Common options:

- `--branch BRANCH`: monitor a specific Forge branch on `origin`.
- `--new-limit N`: process up to `N` new-library tasks per cycle.
- `--javac-limit N`: process up to `N` Java compilation failure tasks per cycle.
- `--java-run-limit N`: process up to `N` JVM runtime failure tasks per cycle.
- `--ni-run-limit N`: process up to `N` Native Image runtime failure tasks per cycle.
- `--parallelism N`: run up to `N` issue workflows in parallel. Maximum: 4.
- `--review-limit N`: process up to `N` PR review tasks per label per cycle.
- `--random-offset`: start new-library issue scans at a random offset instead of the newest issues first.
- `--once`: run a single update/work cycle through `do_up_to_date_work.sh` and exit.
- `--stop`: ask all Forge `do-work` loops for the current user to exit by creating `~/.metadata-forge-stop`.
- `--stop --branch BRANCH`: ask only loops monitoring `BRANCH` to exit, using a branch-scoped marker such as `~/.metadata-forge-stop.master`.
- `--clear-stop`: remove the matching global or branch-scoped stop marker so future `do-work` loops can run.
- `--clear-issue-caches`: delete local issue claim/search caches used by work-queue scanning and exit.

Examples:

```console
./do-work.sh --new-limit 1 --javac-limit 1
./do-work.sh --parallelism 2
DO_WORK_SLEEP_SECONDS=60 ./do-work.sh --branch master
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
`FORGE_BULK_UPDATE_REVIEW_LIMIT`, and `DO_WORK_SLEEP_SECONDS`. Set
`FORGE_DO_WORK_STOP_FILE` to override the shared stop marker path.

## Setup

Run commands from this directory unless a command says otherwise.

```console
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
```

Required local tools depend on the work queue being processed:

- `gh` for issue, PR, and review automation.
- `pi` for Pi-agent strategies and automated style recovery.
- `codex` for Codex-agent strategies and metadata fixups.
- GraalVM available through `GRAALVM_HOME` or `JAVA_HOME`.

Local Forge automation must run without `sudo`. Local CI verification fails
fast instead of prompting for an administrator password if a command or script
would require elevated privileges.

## Manual Workflows

The top-level worker delegates to these lower-level entry points. Use them
directly when debugging a single task or reproducing a failure.

```console
python3 forge_metadata.py --help
python3 ai_workflows/add_new_library_support.py --coordinates <group:artifact:version>
python3 ai_workflows/fix_javac_fail.py --coordinates <group:artifact:oldVersion> --new-version <newVersion>
python3 ai_workflows/fix_java_run_fail.py --coordinates <group:artifact:oldVersion> --new-version <newVersion>
python3 ai_workflows/fix_ni_run.py --coordinates <group:artifact:oldVersion> --new-version <newVersion>
```

Strategies are declared in `strategies/predefined_strategies.json`. Prompt text
lives in `prompt_templates/`. Persisted output contracts live in `schemas/`.

## Repository Layout

```console
forge/
├─ ai_workflows/
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
- `ai_workflows/`: workflow entry points and strategy implementations.
- `git_scripts/`: branch, commit, PR, and review helpers.
- `utility_scripts/`: shared support code.
- `docs/`: design notes, workflow specifications, and testing guidance.

See `DEVELOPING.md` for command-level workflow details,
`docs/overview.md` for the functional specification, and
`docs/architecture.md` for the architecture overview.
