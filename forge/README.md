# Metadata Forge

This project contains scripts and AI-powered workflows that fully automate the collection and maintenance of reachability metadata for community libraries. The workflows compose registered agents with registered workflow strategies.

Repo link: https://github.com/oracle/graalvm-reachability-metadata/

## Project goals
Our automation for maintaining and evolving the reachability‑metadata repository centers on three primary tasks:
- Generate new tests for libraries that are not currently supported.
- Automatically fix test failures introduced by updating a library version using an LLM.
- Increase the metadata coverage of the current libraries.

## Project layout

```console
metadata-forge/
├─ ai_workflows/
├─ git_scripts/
├─ complete_pipelines/
└─ utility_scripts/
```

- `ai_workflows/` — Pipelines that compose workflow strategies with registered agents.
- `git_scripts/` — Git automation helpers.
- `complete_pipelines/` — End-to-end wrappers that run an AI workflow and then open a PR.
- `utility_scripts/` — Small helper scripts for technical and operational tasks.

## Merged repository mode

When this project is checked out as `graalvm-reachability-metadata/metadata-forge`, pass `--in-metadata-repo` to workflow, benchmark, top-level automation, and PR scripts. In this mode:
- The default reachability-metadata repository is the parent checkout (`../` from `metadata-forge/`).
- Each automated issue run still creates a separate detached worktree of the metadata repository.
- The metrics root defaults to that run worktree's `metadata-forge/` directory, with metrics written under `metadata-forge/script_run_metrics/` or `metadata-forge/benchmark_run_metrics/`.
- Generated PR branches include the in-repo metrics file changes instead of pushing to a separate `metadata-forge-metrics` repository.

## Prerequisites

- Local clone of `oracle/graalvm-reachability-metadata`
- GraalVM distribution installed:
  - Set `GRAALVM_HOME` or pass graalvm_home when running the workflow.
- Python 3 with a virtual environment. Set up and install dependencies:
  ```console
  python3 -m venv .venv
  source .venv/bin/activate
  pip install -e .
  ```
  This installs all project dependencies (defined in `pyproject.toml`) and makes the project packages importable.
- Install the tooling required by the strategy you plan to run. The default strategies are Pi-based and require the `pi` CLI; Codex strategies require the `codex` CLI.
- Git and GitHub CLI (`gh`) configured if you intend to use scripts in `git_scripts/`.

## Add support for a new library

The `ai_workflows/add_new_library_support.py` workflow uses an LLM to iteratively design and implement a cohesive JUnit test suite for a brand-new library, keep indices in sync, and generate reachability metadata. The target is to get unit tests green while native tests indicate where metadata is required, then collect that metadata.

- How to run:
```console
python3 ai_workflows/add_new_library_support.py \
  --coordinates <group:artifact:version> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics-storage] \
  [--docs-path /path/to/additional_documentation] \
  [--strategy-name NAME] \
  [-v]
```

If the reachability-metadata repository is not present at the default path, it is cloned from `github.com/oracle/graalvm-reachability-metadata` (pass `--reachability-metadata-path` to use an existing checkout). The metrics repository defaults to a local git repository under `local_repositories/metadata-forge-metrics`.

The optional repo path flags act as overrides:
- `--reachability-metadata-path` overrides the default path of the local clone of `oracle/graalvm-reachability-metadata`.
- `--metrics-repo-path` overrides the default path of the local clone of the metrics repository.

In this documentation, `metrics_repo_path` refers to the repository that is used for storing script run metrics and benchmark results.

Options:
- `-h` or `--help` for script invocation instructions
- `-v` or `--verbose` for the configured agent's verbose output
- `--strategy-name NAME` select a predefined workflow strategy from `strategies/predefined_strategies.json`. Defaults to `basic_iterative_pi_gpt-5.4` (Pi agent).

Note on GraalVM/Java environment variables:
- If `GRAALVM_HOME` points to a GraalVM distribution, it is used for both `GRAALVM_HOME` and `JAVA_HOME`.
- Otherwise, if `JAVA_HOME` points to a GraalVM distribution, it is used for both `JAVA_HOME` and `GRAALVM_HOME`.
- If neither environment variable satisfies this condition, the script exits with an error.

Additional documentation is optional. If provided, it will be used as read-only context files for the configured agent during each prompt (for example, selected portions of the library’s Javadoc downloaded from Maven Central).

Output:
- Each run writes a validated run metrics object to `<metrics_repo_path>/new_library_support/results.json`, based on the `schemas/evaluation_output_schema.json`.

## Automate fixes for Java compilation fails when bumping a library version

When testing support for a new library version in the reachability-metadata repository, we run the existing tests against the new version. This can result in Java compilation failures due to breaking changes in the library.

The `ai_workflows/fix_javac_fail.py` workflow uses an LLM to detect and fix common problems that arise during such version bumps.

- How to run:
```console
python3 ai_workflows/fix_javac_fail.py \
  --coordinates <group:artifact:oldVersion> \
  --new-version <newVersion> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics-storage] \
  [--docs-path /path/to/additional_documentation] \
  [--strategy-name NAME] \
  [-v]
```

If the reachability-metadata repository is not present at the default path, it is cloned from `github.com/oracle/graalvm-reachability-metadata` (pass `--reachability-metadata-path` to use an existing checkout). The metrics repository defaults to a local git repository under `local_repositories/metadata-forge-metrics`.

The optional repo path flags act as overrides:
- `--reachability-metadata-path` overrides the default path of the local clone of `oracle/graalvm-reachability-metadata`.
- `--metrics-repo-path` overrides the default path of the local clone of the metrics repository.

Options:
- `-h` or `--help` for script invocation instructions
- `-v` or `--verbose` for the configured agent’s verbose output
- `--strategy-name NAME` select a predefined workflow strategy from `strategies/predefined_strategies.json`. Defaults to `javac_iterative_with_coverage_sources_pi_gpt-5.5`.

Note on GraalVM/Java environment variables:
- If `GRAALVM_HOME` points to a GraalVM distribution, it is used for both `GRAALVM_HOME` and `JAVA_HOME`.
- Otherwise, if `JAVA_HOME` points to a GraalVM distribution, it is used for both `JAVA_HOME` and `GRAALVM_HOME`.
- If neither environment variable satisfies this condition, the script exits with an error.

Additional documentation is optional. If provided, it will be used as read-only context files for the configured agent during each prompt (for example, selected portions of the library’s Javadoc downloaded from Maven Central).

## Add support for a new library using dynamic-access guidance

The `dynamic_access_iterative` workflow is an alternative to `basic_iterative` that uses a coverage report to guide test generation. It identifies which dynamic-access call sites (reflection, JNI, resources, serialization) remain uncovered and sends the agent class-targeted prompts with the exact gaps to fill. If the library has no dynamic access, it falls back to `basic_iterative` automatically.

Strategies are available for different source-context combinations (library source, upstream tests, documentation). See `strategies/predefined_strategies.json` for the full list.

- How to run:
```console
python3 ai_workflows/add_new_library_support.py \
  --coordinates <group:artifact:version> \
  --strategy-name dynamic_access_main_sources_pi_gpt-5.4 \
  [--keep-tests-without-dynamic-access] \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics-storage] \
  [-v]
```

Options:
- `--strategy-name NAME` selects a predefined dynamic-access strategy (for example, `dynamic_access_main_sources_pi_gpt-5.4`, `dynamic_access_main_sources_pi_codex-5.3`, `dynamic_access_main_sources_codex_gpt-5.4`, or `dynamic_access_main_sources_with_tests_and_documentation_codex_codex-5.3`).
- `--keep-tests-without-dynamic-access` keeps generated tests even if dynamic-access call-site coverage does not improve during dynamic-access iterations.

For implementation details see `DEVELOPING.md` and `spec/dynamic-access-strategy.md`.

## Git scripts

Automation scripts for Git/GitHub actions.

Script: `git_scripts/make_pr_javac_fix.py` — Pushes changes that fix javac fails and opens a PR `oracle/graalvm-reachability-metadata`.

Usage:
```console
python3 git_scripts/make_pr_javac_fix.py \
  --coordinates <group:artifact:current_version> \
  --new-version <new_version> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics_repo_root]
```

Options:
- `-h` or `--help` for script invocation instructions

**NOTE:** Requires the 'gh' CLI configured and authenticated with access to the target repository.

Script: `git_scripts/make_pr_ni_run_fix.py` — Pushes changes that fix Native Image run failures and opens a PR to `oracle/graalvm-reachability-metadata`.

Usage:
```console
python3 git_scripts/make_pr_ni_run_fix.py \
  --coordinates <group:artifact:current_version> \
  --new-version <new_version> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata]
```

Options:
- `-h` or `--help` for script invocation instructions

**NOTE:** Requires the 'gh' CLI configured and authenticated with access to the target repository.

Script: `git_scripts/make_pr_new_library_support.py` — Pushes changes that add support for a new library and opens a PR to `oracle/graalvm-reachability-metadata`.

Usage:
```console
python3 git_scripts/make_pr_new_library_support.py \
  --coordinates <group:artifact:version> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-root /path/to/metrics_repo_root]
```

Options:
- `-h` or `--help` for script invocation instructions

Both path flags are optional overrides:
- `--reachability-metadata-path` overrides the default location of the local clone of `oracle/graalvm-reachability-metadata`.
- `--metrics-repo-root` overrides the default location of the metrics repository root.

By default, the script expects these repositories under `local_repositories/` relative to this repository:
- `local_repositories/graalvm-reachability-metadata`
- `local_repositories/metadata-forge-metrics`

In this documentation, `metrics_repo_root` refers to the directory that contains the `new_library_support/results.json` file which the script reads to populate the PR description.

**NOTE:** Requires the 'gh' CLI configured and authenticated with access to the target repository.

## Pipeline: AI javac fail fix script + PR creation

Run the end-to-end pipeline that first runs the AI fix script and then opens a GitHub PR.

- How to run:
```console
python3 complete_pipelines/fix_javac_create_pr.py \
  --coordinates <group:artifact:currentVersion> \
  --new-version <newVersion> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics_repo_path] \
  [--docs-path /path_to_additional_docs] \
  [--strategy-name NAME] \
  [-v]
```

Options:
- `-h` or `--help` for script invocation instructions
- `-v` or `--verbose` for the configured agent's verbose output

**NOTE:** this approach is not recommended for now, as the AI scripts are still in an experimental state.

## Pipeline: Fix Native Image run failures + PR creation

Run the end-to-end pipeline that runs the Gradle `fixTestNativeImageRun` task and then opens a GitHub PR.

- How to run:
```console
python3 complete_pipelines/fix_ni_run_create_pr.py \
  --coordinates <group:artifact:currentVersion> \
  --new-version <newVersion> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata]
```

Options:
- `-h` or `--help` for script invocation instructions

## Pipeline: Add new library support + PR creation

Run the end-to-end pipeline that first runs the AI new‑library workflow and then opens a GitHub PR.

- How to run:
```console
python3 complete_pipelines/add_new_library_support_create_pr.py \
  --coordinates <group:artifact:version> \
  [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
  [--metrics-repo-path /path/to/metrics_repo_path] \
  [--docs-path /path_to_additional_docs] \
  [--strategy-name NAME] \
  [-v]
```

Options:
- `-h` or `--help` for script invocation instructions
- `-v` or `--verbose` for the configured agent's verbose output
- `--strategy-name NAME` to provide the workflow strategy passed to the AI script

**NOTE:** this approach is not recommended for now, as the AI scripts are still in an experimental state.
