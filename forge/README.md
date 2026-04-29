# Metadata Forge

Metadata Forge automates reachability-metadata maintenance for community
libraries: generating new library support, fixing version-bump failures,
reviewing generated PRs, and recording run metrics.

This directory lives inside the `graalvm-reachability-metadata` checkout. The
reachability repository is the parent directory (`..`) and Forge metrics are
stored under this directory.

## Primary Entry Point

Use `do-work.sh` for unattended operation. It continuously updates this Forge
checkout, runs `do_up_to_date_work.sh`, processes the configured work queues,
and sleeps before the next cycle.

```console
./do-work.sh [options] [metadata-forge-branch]
```

Common options:

- `--branch BRANCH`: monitor a specific Forge branch on `origin`.
- `--new-limit N`: process up to `N` new-library tasks per cycle.
- `--javac-limit N`: process up to `N` Java compilation failure tasks per cycle.
- `--java-run-limit N`: process up to `N` JVM runtime failure tasks per cycle.
- `--ni-run-limit N`: process up to `N` Native Image runtime failure tasks per cycle.
- `--review-limit N`: process up to `N` PR review tasks per label per cycle.
- `--in-metadata-repo`: deprecated compatibility no-op; in-repo mode is always used.

Examples:

```console
./do-work.sh --new-limit 1 --javac-limit 1
DO_WORK_SLEEP_SECONDS=60 ./do-work.sh --branch master
FORGE_REVIEW_LABEL=library-new-request ./do-work.sh --review-limit 2
```

The same limits can be controlled with environment variables such as
`FORGE_WORK_LIMIT`, `FORGE_JAVAC_WORK_LIMIT`, `FORGE_JAVA_RUN_WORK_LIMIT`,
`FORGE_NI_RUN_WORK_LIMIT`, `FORGE_REVIEW_LIMIT`, and
`DO_WORK_SLEEP_SECONDS`.

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
- `do-work.sh`: long-running worker loop.
- `do_up_to_date_work.sh`: one-shot queue processor used by the worker loop.
- `ai_workflows/`: workflow entry points and strategy implementations.
- `git_scripts/`: branch, commit, PR, and review helpers.
- `utility_scripts/`: shared support code.
- `docs/`: design notes, workflow specifications, and testing guidance.

See `DEVELOPING.md` for command-level workflow details and
`docs/overview.md` for the architecture overview.
