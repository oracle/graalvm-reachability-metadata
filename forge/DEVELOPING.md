# Developing Forge

This document describes how to set up a local environment and work on Forge. It is concise by design and complements the main README.

### Overview

Forge automates maintenance of the oracle/graalvm-reachability-metadata repository (§FS-forge-issue-resolution-goal). Every issue run is one Rhei workspace of the `forge-metadata` template (§AR-forge-workflow-pipeline).

Repo layout (§AR-forge-architecture):
- `forge_metadata.py`, `run-forge.sh` — the launcher: fetch, claim, form check, one Rhei run.
- `.agent-grounds/rhei/templates/` — the `forge-metadata`, code coverage, and scanner templates.
- `.agent-grounds/rhei/programs/` — the programs the `forge-metadata` states run, with their tests.
- `dispatcher/` — the claim, the issue-form gate, the board, and the pull-request review.
- `ai_workflows/agents/` — the Pi and Claude Code adapters the code coverage workflow drives.
- `git_scripts/` — Git/GitHub automation helpers.
- `utility_scripts/` — Helper scripts for technical and operational tasks.

### Repository mode

Forge is located inside `graalvm-reachability-metadata/forge`. The parent checkout is the default reachability repository. `prepare` spawns one reachability-repo worktree per issue run from the pinned base, and `publish` writes the run's metrics under `stats/<group>/<artifact>/<version>/execution-metrics.json` in that worktree (§AR-forge-workflow-pipeline.4, §FS-durable-generation-logs).

### Prerequisites

- Local clone of https://github.com/oracle/graalvm-reachability-metadata
- A GraalVM of JDK 25 or newer in `GRAALVM_HOME` or `JAVA_HOME`.
- Python 3, `rhei`, `pi`, and `claude`.
- Set `PYTHONPATH` so cross-package imports work (run from the `forge/` directory):
  ```bash
  export PYTHONPATH=$PWD
  ```
- Git and GitHub CLI (`gh`) configured.
- For style checking in this repository, install the development dependencies from the requirements.txt (§FS-local-ci-equivalent-verification):
  ```console
  pip install -r requirements.txt
  ```

Tip: Use --help on any script for detailed usage and flags.

### Working on the forge-metadata template

The states, routes, and agent instructions live in
`.agent-grounds/rhei/templates/forge-metadata/states.yaml`; the programs they run
live in `.agent-grounds/rhei/programs/` (§AR-forge-workflow-pipeline.3). Run the
program tests, which drive the whole state machine against a fake harness, and
validate the template after every change:

```console
(cd .agent-grounds/rhei/programs && python3 -m unittest discover -s tests)
rhei instantiate forge-metadata --values .agent-grounds/rhei/templates/forge-metadata/.example-values.yaml --dry-run
```

The template's `README.md` shows how to run one already-claimed issue by hand.

### Utilities

- Count reachability entries (new JSON format):
  ```bash
  python3 utility_scripts/count_reachability_entries.py /path/to/reachability-metadata.json
  ```
- Count native-image config entries (legacy layout):
  ```bash
  python3 utility_scripts/count_native_image_config_entries.py /path/to/metadata/versioned/dir
  ```
- JaCoCo XML coverage parser:
  ```bash
  python3 utility_scripts/jacoco_parser.py /path/to/jacocoTestReport.xml
  ```

### Agent session logs

Agents are registered via `Agent.register(...)`; see [docs/architecture/agent.md](docs/architecture/agent.md) for the agent API and Pi adapter architecture. The adapters serve the code coverage workflow; `forge-metadata` runs its agents through Rhei, which keeps their transcripts in the workspace (§AR-forge-workflow-pipeline.8). Supported agents:

- `pi` — the default. Driven through `pi --mode rpc` by `PiAgent` (`ai_workflows/agents/pi_agent.py`). Per-turn transcripts are written to `logs/pi-<action>-<library>-<timestamp>.log` in this repository (see `utility_scripts/pi_logs.py`).
- `claude-code` — driven through `claude` by `ClaudeCodeAgent`.

Workflow-local logs are grouped by library first as `logs/<group>:<artifact>:<version>/<task-type>/...`.

- Output JSON validation helper:
  ```bash
  python3 utility_scripts/schema_validator.py <schema_name> <file_path>
  ```
  - `run_metrics_output` validates run metrics output files using `../stats/schemas/run-metrics-output-schema-v1.0.1.json`:
    ```bash
    python3 utility_scripts/schema_validator.py run_metrics_output output/results.json
    ```

### Coding style and developing conventions

- Ensure scripts run with python3.
- License the repository under `CC0-1.0`; do not add proprietary per-file headers.
- Keep scripts small and composable. Prefer CLI flags rather than positional script arguments.
- Implement the standard help flags `-h`, `--help` for scripts. Output must include the script's purpose, a list of all options, and a usage example.
- If a script performs technical changes to the graalvm-reachability-metadata repository (tests, metadata, Gradle tasks, CI logic), implement and contribute that logic in that repository.
- After changes always run `pylint .`, to check style.
- If you change any `.json` file in this project, always run `utility_scripts/schema_validator.py` against the corresponding schema.
