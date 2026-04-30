# Metadata Forge — Functional Specification

> **See also:** [Architecture](architecture.md)

## 1. Purpose

Metadata Forge automates the production and maintenance of GraalVM
**reachability metadata** for community Java/Kotlin/Scala libraries. It lives
inside the [`oracle/graalvm-reachability-metadata`](https://github.com/oracle/graalvm-reachability-metadata)
repository (hereafter "the reachability repo") and composes LLM-based
code-generation agents with the repo's own build, test, and reporting
pipelines to produce metadata, tests, and PRs ready for human review.

## 2. Scope

The project covers three primary task families:

1. **New library support** (`library-new-request`) — generate a JUnit (or
   Kotlin/Scala) test suite, produce reachability metadata, and open a PR for
   a previously unsupported library.
2. **Java compilation fixes** (`fixes-javac-fail`) — repair test sources that
   no longer compile against a bumped library version.
3. **Java runtime fixes** (`fixes-java-run-fail`) — repair JVM-mode test
   failures that surface when raising a tested library to a new version.
4. **Native-image runtime fixes** (`fixes-native-image-run-fail`) — update
   reachability metadata so `nativeTest` passes against the new library
   version, using the native metadata exploration phase.
5. **Bulk version updates** (`library-bulk-update`) — record newly tested
   upstream versions for already-supported libraries when no fix is required.
6. **Coverage improvement** (`library-update-request`) — increase
   dynamic-access coverage of already-supported libraries.
7. **Docker image updates** (`docker`) — refresh the allowed Docker images
   used by tests, including vulnerability rescans and version bumps.

Out of scope: anything that does not produce reachability metadata or its
supporting tests for the reachability repo.

## 3. Glossary

| Term | Definition |
| --- | --- |
| **Reachability metadata** | JSON describing reflection, JNI, resource, serialization, and proxy access for a library, consumed by GraalVM `native-image`. |
| **Reachability repo** | Local checkout or worktree of `oracle/graalvm-reachability-metadata`. The build and metadata-generation Gradle tasks run inside it. The parent checkout of `forge/` is used by default. |
| **Metrics root** | Directory that stores per-run results validated against [schemas/](../schemas/). By default, this is the run worktree's `forge/` directory. |
| **Coordinate** | Maven coordinate of the target library, formatted `group:artifact:version`. |
| **Agent** | LLM-driven code editor (Aider, Codex, or Pi) registered through [ai_workflows/agents/](../ai_workflows/agents/). Each implements `send_prompt`, `run_test_command`, `clear_context`. |
| **Workflow strategy** | A registered control loop in [ai_workflows/workflow_strategies/](../ai_workflows/workflow_strategies/) (e.g. `basic_iterative`, `dynamic_access_iterative`). |
| **Predefined strategy** | Named, fully parameterized combination of agent + workflow strategy + prompts + parameters in [strategies/predefined_strategies.json](../strategies/predefined_strategies.json). Selected via `--strategy-name`. |
| **Post-generation intervention** | Recovery step a strategy invokes when the post-iteration `./gradlew test` still fails. Pluggable via the `PostGenerationIntervention` registry. Configured per predefined strategy (`post-generation-intervention.name`). Concrete implementations: `noop`, `codex_then_pi` (default), `native_trace_collect`. See [workflow-strategies.md §5](workflow-strategies.md#5-post-generation-interventions). |
| **Dynamic access** | Reflection, JNI, resource access, serialization, or proxy use that GraalVM `native-image` cannot determine statically. |
| **Dynamic-access report** | JSON written by Gradle task `generateDynamicAccessCoverageReport` to `tests/src/<group>/<artifact>/<version>/build/reports/dynamic-access/dynamic-access-coverage.json`, listing classes and per-class call sites that require dynamic-access metadata, marked covered/uncovered. |
| **Source context** | Read-only files supplied to the agent. Types: `main` (library source), `test` (upstream tests), `documentation` (Javadoc). Selected by the strategy parameter `source-context-types`. |

## 4. Configuration Contracts

### 4.1 CLI inputs (common to all entry scripts)

- `--coordinates <group:artifact:version>` — required.
- `--reachability-metadata-path` — overrides default reachability-repo clone.
- `--metrics-repo-path` — overrides default metrics-repo clone.
- `--in-metadata-repo` — deprecated compatibility no-op. Forge always runs
  from `graalvm-reachability-metadata/forge`, defaults the target repository
  to the parent checkout, and stores successful run metrics under
  `stats/<group>/<artifact>/<version>/execution-metrics.json`.
- `--strategy-name <name>` — selects an entry from `predefined_strategies.json`.
- `--docs-path` — additional read-only files for agent context.
- `-v` / `--verbose` — verbose agent output.
- `--keep-tests-without-dynamic-access` — only honored by dynamic-access
  strategies; see [dynamic-access-workflow.md](dynamic-access-workflow.md).

### 4.2 Predefined strategy schema

Each entry in `strategies/predefined_strategies.json` must provide:

- `name` — unique identifier passed via `--strategy-name`.
- `agent` — registered agent name (`aider`, `codex`, `pi`).
- `workflow` — registered workflow strategy name.
- `model` — agent-visible model identifier.
- `prompts` — map of prompt-key → template path. Required keys depend on the
  workflow (e.g., `dynamic_access_iterative` requires
  `dynamic-access-iteration`).
- `parameters` — workflow-specific parameters (iteration limits,
  `source-context-types`, etc.).
- `mcps` — optional list of MCP server names.
- `post-generation-intervention` — optional. Selects the recovery step
  invoked when the post-iteration `./gradlew test` fails. Shape:
  `{ "name": "<registered intervention>", "parameters": { … } }`. Omitted
  entries default to `codex_then_pi`. See
  [workflow-strategies.md §5](workflow-strategies.md#5-post-generation-interventions).

### 4.3 Environment

- Either `GRAALVM_HOME` or `JAVA_HOME` must point to a GraalVM distribution.
  Both variables are then aligned to that distribution. If neither does, the
  workflow exits with an error.
- `gh` CLI authenticated against the reachability repo is required for any
  script in `git_scripts/` or `complete_pipelines/`.

## 5. Outputs

- **Per-run metrics record** appended to
  `<metrics_repo>/{script_run_metrics,benchmark_run_metrics}/<workflow>.json`,
  schema-validated. Successful run metrics are written under
  `stats/<group>/<artifact>/<version>/execution-metrics.json`.
- **Generated tests + metadata + index.json** committed on the feature branch
  `ai/add-lib-support-<group>-<artifact>-<version>` in the reachability repo.
- **Pull request** (only when invoked through `complete_pipelines/` or
  `git_scripts/make_pr_*.py`).

## 6. Failure Semantics

Every workflow returns one of three statuses:

| Status | Meaning |
| --- | --- |
| `RUN_STATUS_SUCCESS` | All gates passed; metadata and tests committed. |
| `SUCCESS_WITH_INTERVENTION_STATUS` | Tests succeeded after the configured `PostGenerationIntervention` modified the working tree (e.g. `codex_then_pi` removing failing tests via Pi). The intervention's record is included in the run-metrics and PR description. PR-eligible. |
| `RUN_STATUS_FAILURE` | The workflow could not converge or a quality gate failed; the feature branch is reset to the scaffold checkpoint and no PR is opened. |

The exit code is `0` for the first two and `1` for failure.

## 7. Workflow Specifications

- [Workflow strategies](workflow-strategies.md) — registry of strategies
  and post-generation interventions, including the
  [`native_trace_collect`](workflow-strategies.md#53-native_trace_collect)
  intervention that drives the trace loop.
- [Dynamic access workflow](dynamic-access-workflow.md) — guided test
  generation driven by the dynamic-access coverage report.
- [Native metadata exploration](native-metadata-exploration.md) —
  specification for the iterative trace loop (Gradle task contract,
  convergence rule, status enum, codex hand-off). Implements the
  `native_trace_collect` intervention.
- [Java compilation / runtime failure fix workflow](fix-java-run-fail.md)
  — version-bump fix workflow shared by `fix_javac_fail` and
  `fix_java_run_fail`. Strategies in this family route through the
  configured post-generation intervention when `nativeTest` still fails.
- (Future) Basic iterative workflow.
