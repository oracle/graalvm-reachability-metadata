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
2. **Coverage improvement** — increase dynamic-access coverage of
   already-supported libraries (via the `library-update-request` pipeline).
3. **Java compilation fixes** (`fixes-javac-fail`) — repair test sources that
   no longer compile against a bumped library version.
4. **Java runtime fixes** (`fixes-java-run-fail`) — repair JVM-mode test
   failures that surface when raising a tested library to a new version.
5. **Native-image runtime fixes** (`fixes-native-image-run-fail`) — update
   reachability metadata so `nativeTest` passes against the new library
   version, using the native metadata exploration phase.
6. **Bulk version updates** (`library-bulk-update`) — record newly tested
   upstream versions for already-supported libraries when no fix is required.
7. **Coverage improvement** (`library-update-request`) — increase
   dynamic-access coverage of already-supported libraries.
8. **Docker image updates** (`docker`) — refresh the allowed Docker images
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

### 4.1 Top-level worker bootstrap

- `do-work.sh` is a fixed bootstrap script and must not be changed for worker
  behavior updates. It forwards `argv` unchanged to
  `do_up_to_date_work.sh`, where every option and environment concern is
  handled.
- `do_up_to_date_work.sh` owns argument parsing, environment normalization,
  Forge self-updates, queue dispatch, sleep timing, and re-execing the latest
  worker script.
- `do_up_to_date_work.sh --stop` creates a shared stop marker for the current
  user at `~/.metadata-forge-stop` by default. Passing `--branch BRANCH` or a
  positional branch creates a branch-scoped marker next to it, such as
  `~/.metadata-forge-stop.master`. Running `--clear-stop` removes the matching
  marker. Existing worker loops check the global marker and the marker for
  their monitored branch between queue operations and during sleep, then exit
  without claiming additional work.

### 4.2 CLI inputs (common to all entry scripts)

- `--coordinates <group:artifact:version>` — required.
- `--reachability-metadata-path` — overrides default reachability-repo clone.
- `--metrics-repo-path` — overrides default metrics-repo clone.
- `--strategy-name <name>` — selects an entry from `predefined_strategies.json`.
- `--docs-path` — additional read-only files for agent context.
- `-v` / `--verbose` — verbose agent output.
- `--keep-tests-without-dynamic-access` — only honored by dynamic-access
  strategies; see [dynamic-access-workflow.md](dynamic-access-workflow.md).

### 4.3 Predefined strategy schema

Each entry in `strategies/predefined_strategies.json` must provide:

- `name` — unique identifier passed via `--strategy-name`.
- `agent` — registered agent name (`aider`, `codex`, `pi`).
- `workflow` — registered workflow strategy name.
- `model` — agent-visible model identifier.
- `prompts` — map of prompt-key → template path. Required keys depend on the
  workflow (e.g., `dynamic_access_iterative` requires
  `dynamic-access-iteration`).
- `persistent-instructions` — optional prompt-template path for durable,
  workflow-wide rules that should be passed to the agent backend's persistent
  instruction layer instead of a normal user prompt. The template is rendered
  with the same substitution context as workflow prompts.
- `parameters` — workflow-specific parameters (iteration limits,
  `source-context-types`, etc.).
- `mcps` — optional list of MCP server names.
- `post-generation-intervention` — optional. Selects the recovery step
  invoked when the post-iteration `./gradlew test` fails. Shape:
  `{ "name": "<registered intervention>", "parameters": { … } }`. Omitted
  entries default to `codex_then_pi`. See
  [workflow-strategies.md §5](workflow-strategies.md#5-post-generation-interventions).

### 4.4 Environment

- Either `GRAALVM_HOME` or `JAVA_HOME` must point to a GraalVM distribution.
  Both variables are then aligned to that distribution. If neither does, the
  workflow exits with an error.
- `gh` CLI authenticated against the reachability repo is required for any
  script in `git_scripts/` or `complete_pipelines/`.
- `FORGE_PARALLELISM` controls how many issue workflows the top-level worker
  may run concurrently. Valid values are `1` through `4`; the default is `1`.
- `FORGE_DO_WORK_STOP_FILE` overrides the shared stop marker path used by
  `do-work` loops. The default is `~/.metadata-forge-stop`.

## 5. Outputs

- **Per-run metrics record** appended to
  `<metrics_repo>/{script_run_metrics,benchmark_run_metrics}/<workflow>.json`,
  schema-validated. Successful run metrics are written under
  `stats/<group>/<artifact>/<version>/execution-metrics.json`.
- **Generated tests + metadata + index.json** committed on the feature branch
  `ai/add-lib-support-<group>-<artifact>-<version>` in the reachability repo.
- **Pull request** (only when invoked through `complete_pipelines/` or
  `git_scripts/make_pr_*.py`).

## 6. Local CI-Equivalent Verification

Every Forge task must be fully tested locally in the same way it will be
tested by CI before it is allowed to produce a PR-eligible result. This is a
hard requirement, not an optimization or best-effort check.

For the task's affected coordinate set, Forge must run the same validation
surface that the corresponding CI workflow will run, including metadata
validation, style checks, compilation, JVM tests, native-image build/run
tests, Docker image pre-pull requirements, vulnerability checks when Docker
images change, stats validation, and any workflow-specific gates. The local
commands, coordinates filter, native-image mode, JDK/GraalVM selection, and
Docker/image setup must be derived from the same repo configuration that CI
uses, including `ci.json` and the Gradle task contracts.

Local verification runs must be non-privileged. Forge must not invoke `sudo`,
must not run scripts that invoke `sudo`, and must not prompt for an
administrator password during local automation. CI-only host mutation steps
that require elevated privileges, such as changing system Docker networking,
must be replaced by no-sudo local gates or omitted from local execution while
preserving the rest of the CI-equivalent validation surface. A command that
would require `sudo` is a local verification failure, not an interactive
prompt. For Docker-backed tests, local verification must fail if tests create
Docker images after the `pullAllowedDockerImages` gate, because that indicates
the local run may have passed by pulling images that CI's disabled-network
phase would reject.

Forge must record the exact local verification commands and their outcomes in
the run metrics and PR description. A task must not open a PR, mark a project
item `Done`, return `RUN_STATUS_SUCCESS`, return
`SUCCESS_WITH_INTERVENTION_STATUS`, or return `RUN_STATUS_CHUNK_READY` until
that local CI-equivalent verification has passed. If Forge cannot reproduce
the CI-equivalent validation locally, the workflow must return
`RUN_STATUS_FAILURE` and preserve enough diagnostics for human follow-up.

If local CI-equivalent verification fails, Forge may run a bounded fixup step
before retrying the full verification. The fixup may repair generated
library-scoped files or shared repository files when the failure is caused by
the repository itself. After verification passes, Forge must algorithmically
compare the final PR diff with the expected library-scoped paths. If any
shared repository file changed, the PR must be labeled `human-intervention`
and the verification metrics and PR description must list the repository-level
paths that require maintainer review.

## 7. Failure Semantics

Every workflow records one of these statuses:

| Status | Meaning |
| --- | --- |
| `RUN_STATUS_SUCCESS` | All generation gates and the local CI-equivalent verification passed; metadata and tests committed. |
| `SUCCESS_WITH_INTERVENTION_STATUS` | Tests succeeded after the configured `PostGenerationIntervention` modified the working tree (e.g. `codex_then_pi` removing failing tests via Pi), and the local CI-equivalent verification passed. The intervention's record is included in the run-metrics and PR description. PR-eligible. |
| `RUN_STATUS_CHUNK_READY` | A large-library series reached a reviewable chunk boundary and the local CI-equivalent verification passed for the current part. The current part is PR-eligible and the issue remains labeled for continuation. |
| `RUN_STATUS_FAILURE` | The workflow could not converge or a quality gate failed; the feature branch is reset to the scaffold checkpoint and no PR is opened. |

The exit code is `0` for PR-eligible statuses and `1` for failure.

For dynamic-access workflows, Forge passes the issue number and configured
large-library chunk limits to the workflow on the first run. If the initial
dynamic-access report exceeds a configured class or call chunk limit, the
workflow creates a durable progress state, returns `RUN_STATUS_CHUNK_READY`
at the first chunk boundary, and Forge applies the large-library continuation
labels after publishing the part PR.

## 8. Workflow Specifications

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
- [Native test verification gate](native-test-verification.md) —
  reusable component that asserts `./gradlew nativeTest` passes for a
  coordinate by composing the trace loop with codex/Pi recovery. Used as
  the per-class gate in the dynamic-access workflow and the terminal
  gate in the fix-native-run workflow.
- [Java compilation / runtime failure fix workflow](fix-java-run-fail.md)
  — version-bump fix workflow shared by `fix_javac_fail` and
  `fix_java_run_fail`. Strategies in this family route through the
  configured post-generation intervention when `nativeTest` still fails.
- [Coverage improvement workflow](improve-library-coverage.md) — generates
  additional tests to improve dynamic-access coverage on an existing library
  version, without modifying existing tests or bumping versions. Triggered by
  the `library-update-request` pipeline label.
- (Future) Basic iterative workflow.
