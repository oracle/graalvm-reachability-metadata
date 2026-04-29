# Developing Metadata Forge

This document describes how to set up a local environment and run the key developer workflows in this repository. It is concise by design and complements the main README.

### Overview

This repo contains Python scripts and AI-powered pipelines to automate maintenance of the oracle/graalvm-reachability-metadata repository:

Repo layout:
- ai_workflows/ — Orchestrated AI workflows.
- git_scripts/ — Git/GitHub automation helpers.
- complete_pipelines/ — End-to-end wrappers that run an AI workflow and then open a PR.
- utility_scripts/ — Helper scripts for technical and operational tasks.

### Repository mode

Forge is located inside `graalvm-reachability-metadata/forge`. The parent checkout is the default reachability repository, and successful run metrics are written under `stats/<group>/<artifact>/<version>/execution-metrics.json`. Top-level automation still creates one detached metadata-repo worktree per issue run; the run's pending metrics root is the same worktree's `forge/` directory. The old `--in-metadata-repo` flag is accepted as a no-op for compatibility.

### Prerequisites

- Local clone of https://github.com/oracle/graalvm-reachability-metadata
- GraalVM distribution installed:
  - Either export GRAALVM_HOME and JAVA_HOME, or pass explicit paths to scripts.
  - JAVA_HOME should point to a GraalVM distribution; if omitted, the scripts fall back to GRAALVM_HOME or the current environment.
- Python 3 and the tooling required by the agent strategy you plan to run.
- Set `PYTHONPATH` so cross-package imports work (run from the `forge/` directory):
  ```bash
  export PYTHONPATH=$PWD
  ```
- Git and GitHub CLI (`gh`) configured if you plan to use _git_scripts/_ to open PRs.
- For style checking in this repository, install the development dependencies from the requirements.txt:
  ```console
  pip install -r requirements.txt
  ```
Tip: Use --help on any script for detailed usage and flags.

### Fix javac test failures for a new library version

Script: `ai_workflows/fix_javac_fail.py`

Purpose:
- Create/update the versioned test module in reachability-metadata.
- Run Gradle tests and, if native test fails, collect metadata and re-run.
- Keep the test meaningful (do not trivialize), while adapting to the new library version.

Usage:
```bash
python3 ai_workflows/fix_javac_fail.py \
  --coordinates <group:artifact:oldVersion> \
  --new-version <newVersion> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics-storage] \
  [--docs-path /path/to/additional_docs] \
  [--strategy-name NAME] \
  [-v]
```

Where:
- `--coordinates`: group:artifact:oldVersion (e.g., org.postgresql:postgresql:42.7.3)
- `--new-version`: target library version which needs a fix (e.g., 42.7.4)
- `--reachability-metadata-path`: optional path to a graalvm-reachability-metadata worktree. If omitted, the parent checkout is used.
- `--metrics-repo-path`: optional path to the metrics root. If omitted, the `forge/` directory in the selected worktree is used.
- `--docs-path`: optional directory with read-only docs for agent context (e.g., extracted Javadoc).

Example:
```bash
python3 ai_workflows/fix_javac_fail.py \
  --coordinates org.postgresql:postgresql:42.7.3 \
  --new-version 42.7.4 \
  --reachability-metadata-path /path/to/graalvm-reachability-metadata \
  --metrics-repo-path /path/to/metrics-storage \
  --docs-path /path/to/docs
```

Options:
- `--strategy-name NAME` select a predefined workflow strategy from `strategies/predefined_strategies.json`. Defaults to `javac_iterative_with_coverage_sources_pi_gpt-5.5`.
- `-v`, `--verbose` enable verbose agent output.

Notes:
- Runs Gradle tasks inside the reachability-metadata repo (`./gradlew ...`).
- On success, writes metrics into `stats/<group>/<artifact>/<version>/execution-metrics.json`.

### Add support for a new library

Script: ai_workflows/add_new_library_support.py_

Purpose:
- Iteratively generate a meaningful, cohesive JUnit test suite for library using AI.
- Keep indices up to date and create the versioned metadata directory.
- Generates metadata for the new library.
- Script results are written in the [`output/results.json`](output/results.json:1).

Usage:
```bash
python ai_workflows/add_new_library_support.py \
  --coordinates <group:artifact:version> \
  [--keep-tests-without-dynamic-access] \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics-storage] \
  [--docs-path /path_to_additional_docs] \
  [--strategy-name NAME] \
  [-v]
```

Where:
- `--coordinates`: group:artifact:version (e.g., org.example:lib:1.2.3)
- `--reachability-metadata-path`: optional path to a graalvm-reachability-metadata worktree. If omitted, the parent checkout is used.
- `--metrics-repo-path`: optional path to the metrics root. If omitted, the `forge/` directory in the selected worktree is used.
- `--docs-path`: optional directory with read-only docs for agent context (e.g., extracted Javadoc).

Example:
```bash
python3 ai_workflows/add_new_library_support.py \
  --coordinates org.example:lib:1.2.3 \
  --reachability-metadata-path /path/to/graalvm-reachability-metadata \
  --metrics-repo-path /path/to/metrics-storage \
  --docs-path /path/to/docs
```

Options:
- `--strategy-name NAME` select a predefined workflow strategy from `strategies/predefined_strategies.json`. Defaults to `basic_iterative_pi_gpt-5.4` (Pi agent).
- `--keep-tests-without-dynamic-access` keeps generated tests for dynamic-access workflows even if no dynamic-access call sites are covered.
- `-v`, `--verbose` enable verbose agent output.

Notes:
- Runs Gradle tasks inside the reachability-metadata repo (`./gradlew ...`).
- On success, writes metrics into `<metrics_repo_path>/new_library_support/results.json`.

### Open a PR with the fixes

Script: git_scripts/make_pr_javac_fix.py

Description: Push changes to your fork/branch and open a PR against oracle/graalvm-reachability-metadata.

Usage:
```bash
python3 git_scripts/make_pr_javac_fix.py \
  --coordinates <group:artifact:old_version> \
  --new-version <new_version> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics_repo_root]
```

Example:
```bash
python3 git_scripts/make_pr_javac_fix.py \
  --coordinates org.postgresql:postgresql:42.7.3 \
  --new-version 42.7.4 \
  --reachability-metadata-path /path/to/graalvm-reachability-metadata \
  --metrics-repo-path /path/to/metrics_repo_root
```

Requirements:
- gh CLI must be installed and authenticated (gh auth login).
- Run after the fix_javac_fail.py workflow has produced a successful result and metrics.

### Open a PR for new library support

Script: git_scripts/make_pr_new_library_support.py

Description: Push changes that add support for a new library and open a PR against oracle/graalvm-reachability-metadata.

Usage:
```bash
python3 git_scripts/make_pr_new_library_support.py \
  --coordinates <group:artifact:version> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-root /path/to/metrics_repo_root]
```

Example:
```bash
python3 git_scripts/make_pr_new_library_support.py \
  --coordinates org.example:lib:1.2.3 \
  --reachability-metadata-path /path/to/graalvm-reachability-metadata \
  --metrics-repo-root /path/to/metrics_repo_root
```

Requirements:
- gh CLI must be installed and authenticated (gh auth login).
- Run after the add_new_library_support.py workflow has produced a successful result and metrics written under `<metrics_repo_root>/new_library_support/results.json`.

### Pipeline: Fix javac test failures + PR creation

Script: complete_pipelines/fix_javac_create_pr.py

Description: Attempts to run the fix workflow and, if successful, opens a PR automatically. This pipeline is experimental; prefer the explicit two-step flow above for reliability.

Usage:
```bash
python3 complete_pipelines/fix_javac_create_pr.py \
  --coordinates <group:artifact:oldVersion> \
  --new-version <newVersion> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics_repo_path] \
  [--docs-path /path_to_additional_docs] \
  [--strategy-name NAME] \
  [-v]
```

### Pipeline: Add new library support + PR creation

Script: complete_pipelines/add_new_library_support_create_pr.py

Description: Runs the add-new-library workflow and, if it exits successfully, opens a PR automatically. This pipeline is experimental; prefer the explicit two-step flow above for reliability.

Usage:
```bash
python3 complete_pipelines/add_new_library_support_create_pr.py \
  --coordinates <group:artifact:version> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics_repo_path] \
  [--docs-path /path_to_additional_docs] \
  [--strategy-name NAME] \
  [-v]
```

### Benchmark runner

Script: `benchmarks/benchmark_runner.py`

Description:
- Run `ai_workflows/add_new_library_support.py` for a predefined set of libraries.
- Cleans up existing tests/metadata in the reachability-metadata repo for provided coordinates
- Record per-run metrics under the configured metrics repository (in `benchmark_run_metrics/`).

Configuration:
- Benchmark suites are defined in `benchmarks/benchmark_suite.json`. Each entry has:
  - `name`: benchmark identifier (e.g., `basic-iterative`).
  - `libraries`: list of `group:artifact:version` coordinates.
  - `strategy`: strategy name matching an entry in `strategies/predefined_strategies.json`.

Usage:
```bash
python3 benchmarks/benchmark_runner.py \
  --benchmark-name benchmark-name \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics-storage] \
  [-v]
```

Where:
- `--benchmark-name`: selects the benchmark configuration from `benchmarks/benchmark_suite.json`.
- `--reachability-metadata-path`: optional path to a `graalvm-reachability-metadata` worktree. If omitted, the parent checkout is used.
- `--metrics-repo-path`: optional path to the metrics root. If omitted, the `forge/` directory in the selected worktree is used.
- `-v`, `--verbose`: enable verbose output and pass `-v` through to the underlying workflow.

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

These are invoked automatically by scripts in the `ai_workflows/`, but can be run standalone for diagnostics.

### Agent session logs

Agents are registered via `Agent.register(...)` and selected per-strategy through the `agent` field in `strategies/predefined_strategies.json`. Supported agents:

- `pi` — default for the shipped strategies. Driven through `pi --mode rpc` by `PiAgent` (`ai_workflows/agents/pi_agent.py`). Per-turn transcripts are written to `logs/pi-<action>-<library>-<timestamp>.log` in this repository (see `utility_scripts/pi_logs.py`). Pi session files are stored under Pi's default session directory (`--session-dir` may override it via `PiRpcClient`). Select with strategy entries whose `agent` is `"pi"`; set `"provider": "openrouter"` to route through OpenRouter.
- `codex` — driven through `codex` by `CodexAgent`. Codex threads act as durable session identities.
  - Primary durable audit artifact: `~/.codex/sessions/...jsonl`
  - Recommended global transcript setting for `history.jsonl` retention:
    ```toml
    history.persistence = "save-all"
    ```
  - Use `~/.codex/sessions/...jsonl` rollout files as the primary downstream pipeline input. The `history.jsonl` stream is optional and intended for broader transcript search or aggregation.

Workflow-local logs are grouped by library first as `logs/<group>:<artifact>:<version>/<task-type>/...`, for example `logs/org.example:lib:1.2.3/add-new-library-support/`.

- Metrics writer:
  ```python
  import utility_scripts.metrics_writer as metrics_writer

  run_metrics = metrics_writer.create_run_metrics_output_json(
      repo_path="<path-to-reachability-repo>",
      package="org.example",
      artifact="lib",
      library_version="1.2.3",
      agent=None,  # Agent implementation exposing token counters
      global_iterations=3,
      tests_root="/abs/path/to/tests/root",
      strategy_name="basic_iterative",
      status="success",
  )
  metrics_writer.append_run_metrics(run_metrics, "output/results.json")
  ```

- Output JSON validation helper:
  Python usage:
  ```python
  from utility_scripts.schema_validator import validate_run_metrics

  validate_run_metrics("output/results.json")
  ```

  CLI usage:
  ```bash
    python3 utility_scripts/schema_validator.py <schema_name> <file_path>`)
  ```
  - `run_metrics_output` validates run metrics output files using `../stats/schemas/run_metrics_output_schema.json`:
    ```bash
    python3 utility_scripts/schema_validator.py run_metrics_output output/results.json
    ```
  - `benchmark_run_metrics` validates benchmark run metrics files using `schemas/benchmark_run_metrics_schema.json`:
    ```bash
    python3 utility_scripts/schema_validator.py benchmark_run_metrics /path/to/benchmark_run_metrics.json
    ```
  - `benchmark_suite` validates benchmark suite config files using `schemas/benchmark_suite_schema.json`:
    ```bash
    python3 utility_scripts/schema_validator.py benchmark_suite benchmarks/benchmark_suite.json
    ```
  - `strategy` validates strategy config files using `schemas/strategy_schema.json`:
    ```bash
    python3 utility_scripts/schema_validator.py strategy strategies/predefined_strategies.json
    ```

### Coding style and developing conventions

- Ensure scripts run with python3.
- License the repository under `CC0-1.0`; do not add proprietary per-file headers.
- Keep scripts small and composable. Prefer CLI flags rather than positional script arguments.
- For Git/PR automation: keep commit messages clear, include the target library coordinates, and reference the metrics produced by the workflow.
- Implement the standard help flags `-h`, `--help` for scripts. Output must include the script's purpose, a list of all options, and a usage example.
- When a script utilizes an AI agent or inner process, implement the verbose flag `-v`, `--verbose` for easier tracing and debugging.
- If a script performs technical changes to the graalvm-reachability-metadata repository (tests, metadata, Gradle tasks, CI logic), implement and contribute that logic in that repository.
- After changes always run `pylint .`, to check style.
- If you change any `.json` file in this project, always run `utility_scripts/schema_validator.py` against the corresponding schema.

### Adding new workflow strategies

The project uses registry-based workflow and agent abstractions. Each workflow strategy is a Python class that inherits from `WorkflowStrategy` and is registered via a decorator. Agents are registered in the same way via `Agent.register(...)`. Strategies are configured through `strategies/predefined_strategies.json`, which defines the workflow, agent, model, optional provider, prompts, and parameters for each strategy.

Depending on what you need, there are three levels of customization:

#### Adding a variant of an existing strategy (different model or parameters)

If you want to run an existing workflow with a different model or tuned parameters, add a new entry to `strategies/predefined_strategies.json`. Reuse the same `workflow` and `agent` as the base strategy, and differentiate by appending a space and a descriptor to the name:

```json
{
  "name": "basic_iterative aggressive",
  "description": "Iterative strategy with higher iteration limits",
  "agent": "pi",
  "workflow": "basic_iterative",
  "model": "oca/gpt-5.4",
  "prompts": {
    "initial": "prompt_templates/initial/basic_initial.md",
    "after-successful-iteration": "prompt_templates/after-successful-iteration/basic_after_success.md",
    "after-failed-iteration": "prompt_templates/after-failed-iteration/basic_after_fail.md"
  },
  "parameters": {
    "max-test-iterations": 10,
    "max-failed-generations": 4,
    "max-successful-generations": 5
  },
  "mcps": []
}
```

The strategy name is just a lookup key for the JSON entry. The workflow and agent implementations are resolved from the `workflow` and `agent` fields, so `basic_iterative aggressive` can still point at the `basic_iterative` workflow without any code changes.

For coding agents that support selecting an LLM backend separately from the model identifier, add a top-level `provider` field to the strategy entry. For example, Pi strategies that should run through OpenRouter can set `"provider": "openrouter"`.

For different prompt behavior, create new prompt template files under `prompt_templates/` and reference them in the new strategy entry. Ensure that the context given to strategy workflows has prompt template placeholders. Prompt templates support Python string formatting with context variables (e.g., `{library}`, `{new_version}`) that are substituted at run time from the workflow context. Be sure that the workflow context contains placeholder keys.

#### Creating a new workflow pipeline

If your use case requires a fundamentally different execution flow:

1. **Add a strategy entry** to `strategies/predefined_strategies.json` with the matching workflow, agent, prompts, and parameters.

2. **Create a new strategy class** in `ai_workflows/workflow_strategies/` that inherits from `WorkflowStrategy`:
   ```python
   from ai_workflows.workflow_strategies.workflow_strategy import WorkflowStrategy

   @WorkflowStrategy.register("my_new_strategy")
   class MyNewStrategy(WorkflowStrategy):
       """Description of the new strategy."""

       REQUIRED_PROMPTS = ["initial"]
       REQUIRED_PARAMS = ["max-test-iterations"]

       def run(self, agent, **kwargs):
           # Implement strategy-specific workflow logic
           ...
   ```
`register` parameter is the workflow implementation name referenced by the strategy config's `workflow` field.

3. **Register the import** in `ai_workflows/workflow_strategies/__init__.py`:
   ```python
   from ai_workflows.workflow_strategies.my_new_strategy import MyNewStrategy
   ``` 
   
Importing the new module in `__init__.py` triggers the `@WorkflowStrategy.register` decorator, which adds the strategy to the internal registry so it can be looked up at run time.

### Dynamic-access iterative workflow

The `dynamic_access_iterative` workflow improves metadata accuracy by using the `generateDynamicAccessCoverageReport` Gradle task to identify which dynamic-access call sites (reflection, JNI, resources, serialization) are not yet covered by the generated tests. Instead of generating tests blindly, the workflow iterates class-by-class, sending the agent targeted prompts with the exact uncovered call sites for each class. After each attempt, the report is regenerated and coverage deltas are computed to decide whether to retry the same class or move on.

If the library has no dynamic access, the workflow falls back to the standard `basic_iterative` strategy automatically.

#### Source context

Dynamic-access strategies can optionally download library source code, upstream tests, and/or documentation as read-only agent context. This is configured per strategy via the `source-context-types` parameter in `predefined_strategies.json`. Available types are `main`, `test`, and `documentation`. The artifacts are downloaded from URLs in the library's `index.json` (populated by the `populateArtifactURLs` Gradle task) and extracted to `local_repositories/source_context/<group>/<artifact>/<version>/`.

#### Available strategies

Multiple predefined strategies exist in `strategies/predefined_strategies.json`, each using a different source-context combination:

| Strategy name | Agent | Source context |
|---|---|---|
| `dynamic_access_main_sources_pi_gpt-5.4` | `pi` | Library source code |
| `dynamic_access_main_sources_pi_codex-5.3` | `pi` | Library source code |
| `dynamic_access_main_sources_codex_gpt-5.4` | `codex` | Library source code |
| `dynamic_access_main_sources_codex_codex-5.3` | `codex` | Library source code |
| `dynamic_access_test_sources_codex_gpt-5.4` | `codex` | Upstream test sources |
| `dynamic_access_test_sources_codex_codex-5.3` | `codex` | Upstream test sources |
| `dynamic_access_documentation_sources_codex_gpt-5.4` | `codex` | Documentation artifacts |
| `dynamic_access_documentation_sources_codex_codex-5.3` | `codex` | Documentation artifacts |
| `dynamic_access_main_sources_with_tests_codex_gpt-5.4` | `codex` | Source code + tests |
| `dynamic_access_main_sources_with_tests_codex_codex-5.3` | `codex` | Source code + tests |
| `dynamic_access_main_sources_with_documentation_codex_gpt-5.4` | `codex` | Source code + docs |
| `dynamic_access_main_sources_with_documentation_codex_codex-5.3` | `codex` | Source code + docs |
| `dynamic_access_test_sources_with_documentation_codex_gpt-5.4` | `codex` | Tests + docs |
| `dynamic_access_test_sources_with_documentation_codex_codex-5.3` | `codex` | Tests + docs |
| `dynamic_access_main_sources_with_tests_and_documentation_codex_gpt-5.4` | `codex` | All three |
| `dynamic_access_main_sources_with_tests_and_documentation_codex_codex-5.3` | `codex` | All three |

Composite strategies (run a primary workflow, then refine dynamic-access coverage):

| Strategy name | Agent | Primary workflow |
|---|---|---|
| `javac_iterative_with_coverage_sources_pi_gpt-5.4` | `pi` | `javac_iterative` |
| `optimistic_dynamic_access_iterative_pi_gpt-5.4` | `pi` | `optimistic_dynamic_access` |

#### Running a dynamic-access strategy

```bash
python3 ai_workflows/add_new_library_support.py \
  --coordinates <group:artifact:version> \
  --strategy-name dynamic_access_main_sources_pi_gpt-5.4 \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics-storage] \
  [-v]
```

Or via the complete pipeline:

```bash
python3 complete_pipelines/add_new_library_support_create_pr.py \
  --coordinates <group:artifact:version> \
  --strategy-name dynamic_access_main_sources_pi_gpt-5.4 \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics-storage] \
  [-v]
```

For the detailed function-level notes and sequence diagram, see `docs/dynamic-access-strategy.md`.

### Quick reference

- Fix tests for a new version:
  python3 ai_workflows/fix_javac_fail.py --coordinates <group:artifact:old> --new-version <newVersion> [--reachability-metadata-path <reach-meta-repo>] [--metrics-repo-path <metrics-repo>] [--docs-path <docs>] [--strategy-name NAME]
- Add support for a new library:
  python3 ai_workflows/add_new_library_support.py --coordinates <group:artifact:version> [--reachability-metadata-path <reach-meta-repo>] [--metrics-repo-path <metrics-repo>] [--docs-path <docs>]
- Open PR with metrics:
  python3 git_scripts/make_pr_javac_fix.py --coordinates <group:artifact:old> --new-version <newVersion> [--reachability-metadata-path <reach-meta-repo>] [--metrics-repo-path <metrics-repo-root>]
- Open PR for new library support:
  python3 git_scripts/make_pr_new_library_support.py --coordinates <group:artifact:version> [--reachability-metadata-path <reach-meta-repo>] [--metrics-repo-root <metrics-repo-root>]
- Complete javac fix pipeline:
  python3 complete_pipelines/fix_javac_create_pr.py --coordinates <group:artifact:old> --new-version <newVersion> [--reachability-metadata-path <reach-meta-repo>] [--metrics-repo-path <metrics-repo>] [--docs-path <docs>] [--strategy-name NAME]
- Complete add-new-library pipeline:
  python3 complete_pipelines/add_new_library_support_create_pr.py --coordinates <coordinates> [--reachability-metadata-path <reach-meta-repo>] [--metrics-repo-path <metrics-repo>] [--docs-path <docs>] [--strategy-name NAME]
- Count reachability entries:
  python3 utility_scripts/count_reachability_entries.py /path/to/reachability-metadata.json
- Count legacy native-image config entries:
  python3 utility_scripts/count_native_image_config_entries.py /path/to/versioned/dir
- Parse JaCoCo XML coverage:
  python3 utility_scripts/jacoco_parser.py /path/to/jacocoTestReport.xml
