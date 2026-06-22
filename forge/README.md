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
§DW-do-work-loop

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
- `--random-offset`: start new-library issue scans at a random offset instead of the newest issues first.
- `--user-requested-only`: fetch only user-requested issue queue items, excluding configured automation and maintainer authors.
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
./do-work.sh --user-requested-only --new-limit 1
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
§DW-do-work-loop

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
- GraalVM available through `GRAALVM_HOME` or `JAVA_HOME`.
§STRAT-forge-predefined-strategy-contract

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

## Isolated execution with Incus (optional)

`forge_metadata.py --incus` runs each generation inside a fresh, single-use Incus
VM instead of on the host, so generated tests that open windows, fill `/tmp`,
write into `$HOME`, or start Docker containers cannot touch the operator's
machine. Forge never installs or configures Incus: prepare the host once with the
steps below, then pass `--incus`. With the flag set Forge preflights this setup
and fails fast if anything is missing. The runner itself is tracked in
§ROADMAP-forge-incus-vm-runner.
§FS-forge-vm-isolated-execution §AR-forge-vm-runner-boundary

### Install and initialize Incus (one-time, on the host)

```console
# Install Incus from your distribution's packages, then:
sudo incus admin init --minimal          # storage pool + default network
sudo usermod -aG incus-admin "$USER"     # drive Incus without sudo; re-login to apply
incus list                               # should print an empty table

# Apply the checked-in profile the runner launches every VM with:
incus profile create forge
incus profile edit forge < incus/forge.profile.yaml
```

### Build the base image (one-time, rebuilt only to refresh tooling)

The base image is a read-only template the per-run VMs are launched from (via
copy-on-write, so launching is cheap). It bakes in the expensive, generic,
non-secret setup so each run reuses it instead of rebuilding it: GraalVM, Docker,
warmed Gradle caches, and a reachability checkout at
`/root/graalvm-reachability-metadata` (override with `FORGE_INCUS_REPO_PATH`).
The image is built locally on each host and never shipped.

First record your machine's VM environment. Copy the template and set the
GraalVM homes generation needs — all of `GRAALVM_HOME`/`JAVA_HOME`,
`GRAALVM_HOME_25_0`, and `GRAALVM_HOME_LATEST_EA` are required by Forge's
pre-processing preflight — to your local installs. The build copies each
referenced installation into the image so the VM matches local generation
rather than downloading a separate version; `GRAALVM_HOME` and
`GRAALVM_HOME_25_0` typically share one JDK 25 path, while
`GRAALVM_HOME_LATEST_EA` is a distinct early-access build:

```console
cp incus/forge.env.example incus/forge.env
$EDITOR incus/forge.env                   # set the GraalVM homes; add any generation vars
```

`incus/build-base-image.sh` is the reproducible definition of the image. It
launches a throwaway builder VM, copies in your local GraalVM, bakes
`forge.env` into the VM's environment, provisions it, publishes the disk as the
`forge-base` alias, and deletes the builder:

```console
./incus/build-base-image.sh
incus image list forge-base              # "forge-base" should be listed
```

Everything `forge.env` defines becomes part of the VM's environment, so put any
other variable a generation run needs there. Override any of `FORGE_INCUS_IMAGE`,
`FORGE_INCUS_SOURCE_IMAGE`, `FORGE_INCUS_REPO_URL`, or `FORGE_INCUS_REPO_PATH` to
target a different image alias, base OS, or checkout. Re-run the script to
refresh the image when GraalVM, the checkout, or `forge.env` change.

### Credentials and logs

- Authenticate `gh` for the reachability repo on the host (`gh auth login`, or
  export `GH_TOKEN`/`GITHUB_TOKEN`); the runner reads that token and seeds it
  into each VM at launch with `gh auth login --with-token`.
- The runner mounts a per-run host directory into each VM at `/forge-logs` and
  points the configurable log destination (`FORGE_LOGS_DIR`) at it, so run logs
  land on the host under `<logs-root>/issue-<number>` and survive VM teardown.
  Metrics and preserved-work branches leave over the network, so they need no
  shared directory.

### Settings

The runner reads these optional environment variables; defaults match the setup
above.

- `FORGE_INCUS_IMAGE` — base image alias (default `forge-base`).
- `FORGE_INCUS_PROFILE` — profile applied at launch (default `forge`).
- `FORGE_INCUS_REPO_PATH` — baked checkout path in the VM
  (default `/root/graalvm-reachability-metadata`).
- `FORGE_INCUS_LOGS_ROOT` — host directory holding the per-run log mounts
  (default the Forge `logs/` root).
- `FORGE_INCUS_LAUNCH_TIMEOUT` — seconds to wait for the VM guest agent
  (default `300`).

### Run

```console
python3 forge_metadata.py --run-work-queues --incus
./do-work.sh --incus                     # same flag, forwarded by the loop wrapper
```

## Manual Workflows

The top-level worker delegates to these lower-level entry points. Use them
directly when debugging a single task or reproducing a failure.
§WF-forge-workflow-drivers

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
§STRAT-workflow-strategy-registry

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
- `benchmarks/`: generation benchmark suites and runner. §BENCH-forge-generation-benchmarking
- `git_scripts/`: branch, commit, PR, and review helpers.
- `utility_scripts/`: shared support code.
- `docs/`: design notes, workflow specifications, and testing guidance.
§AR-forge-architecture

See `DEVELOPING.md` for command-level workflow details,
`docs/functional-spec.md` for the functional specification, and
`docs/architecture.md` for the architecture overview.
