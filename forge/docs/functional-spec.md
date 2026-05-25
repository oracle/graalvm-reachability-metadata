# FS-forge-functional-spec: Forge functional specification

This spec realizes the Forge direction set out in §GOAL-forge-direction, in
service of §GOAL-maximize-library-coverage and
§GOAL-shorten-issue-to-shipped-metadata.

## 1. Purpose

### FS-forge-issue-resolution-goal: Forge issue resolution goal

The `forge/` directory exists to automate the end-to-end resolution of supported
GitHub issue queues in the
[`oracle/graalvm-reachability-metadata`](https://github.com/oracle/graalvm-reachability-metadata)
repository (hereafter "the reachability repo"). A supported issue starts from a
work label such as `library-new-request`, `library-update-request`,
`fails-javac-compile`, `fails-java-run`, or `fails-native-image-run`.

Every `fails-*` queue originates from a failed version update, not from a human
report. The reachability repo's scheduled library version compatibility
automation bulk-tests newer upstream versions of already-supported libraries; it
records the versions that pass in a single `library-bulk-update` PR and files one
`fails-*` tracking issue per `(library, version)` whose update failed, labeled by
the stage that broke (`fails-javac-compile`, `fails-java-run`,
`fails-native-image-build`, or `fails-native-image-run`). Forge claims those
issues; it never opens them. The producer's contract is the reachability repo's
[Library version update automation](../../docs/FUNCTIONAL_SPEC.md#fs-library-version-update-automation-library-version-update-automation)
(root-namespace ID `FS-library-version-update-automation`).

For those issues, Forge composes LLM-based code-generation agents with
the reachability repo's own build, test, metadata-generation, and reporting
pipelines to generate or repair the library tests and GraalVM reachability
metadata needed to satisfy the issue. A successful automated run produces a
locally verified pull request with metrics and enough context for maintainer
review; review and repository CI remain the final merge boundary. This
shortens the path described in §GOAL-shorten-issue-to-shipped-metadata.

## 2. Scope

The project covers these supported issue-resolution queues
(§FS-forge-issue-resolution-goal), in service of
§GOAL-maximize-library-coverage.

1. **New library support** (`library-new-request`) — generate a JUnit (or
   Kotlin/Scala) test suite, produce reachability metadata, and open a PR for
   a previously unsupported library.
2. **Coverage improvement** — increase dynamic-access coverage of
   already-supported libraries (via the `library-update-request` pipeline).
3. **Code coverage improvement** — planned workflow that creates new tests for
   already-supported libraries and uses GraalVM PGO runtime profiles to broaden
   ordinary API and execution coverage beyond dynamic-access call coverage, as
   defined by §FS-forge-code-coverage-improvement.
4. **Java compilation fixes** (`fails-javac-compile`) — repair test sources that
   no longer compile against a bumped library version.
5. **Java runtime fixes** (`fails-java-run`) — repair JVM-mode test
   failures that surface when raising a tested library to a new version.
6. **Native-image runtime fixes** (`fails-native-image-run`) — update
   reachability metadata so `nativeTest` passes against the new library
   version, using the native metadata exploration phase.

Successful fixes use the matching PR labels (`library-new-request`,
`library-update-request`, `fixes-javac-fail`, `fixes-java-run-fail`, or
`fixes-native-image-run-fail`) so review automation can validate the result
(§FS-automated-pr-review).

Out of scope: anything that does not produce reachability metadata or its
supporting tests for the reachability repo.

### FS-forge-code-coverage-improvement: Code coverage improvement

Forge should support a planned workflow that improves ordinary library runtime
code coverage, not only dynamic-access coverage (see
§WF-code-coverage-improvement), broadening the library coverage outcome
described in §GOAL-maximize-library-coverage. The workflow creates or improves
tests for libraries that are already present
in the reachability repo, uses GraalVM PGO runtime profiles as the execution
signal, and targets the whole practical public API surface over repeated runs.
Those tests must live in a separate code coverage test suite, not in the test
suite used to generate or validate reachability metadata.

This functionality is distinct from the `library-update-request`
dynamic-access coverage workflow defined in §WF-improve-library-coverage.
Dynamic-access
coverage asks whether tests exercise calls that require reachability metadata;
code coverage improvement asks whether tests exercise the broader library API
and runtime surface. A strategy may chain both workflows, but each workflow
must keep separate metrics and review evidence so maintainers can distinguish
metadata-relevant coverage from broader behavior coverage.

## 3. Glossary

| Term | Definition |
| --- | --- |
| **Reachability metadata** | JSON describing reflection, JNI, resource, serialization, and proxy access for a library, consumed by GraalVM `native-image`. |
| **Reachability repo** | Local checkout or worktree of `oracle/graalvm-reachability-metadata`. The build and metadata-generation Gradle tasks run inside it. The parent checkout of `forge/` is used by default. |
| **Forge metrics directory** | The `forge/` subdirectory of the reachability checkout, used as the transient staging area for a run's in-flight metrics (`.pending_metrics.json`) until the PR step reads them. Durable per-library run metrics persist to `stats/<group>/<artifact>/<version>/execution-metrics.json` (§FS-forge-run-metrics). |
| **Coordinate** | Maven coordinate of the target library, formatted `group:artifact:version`. |
| **Agent** | LLM-driven code editor (Codex or Pi) registered through [ai_workflows/agents/](../ai_workflows/agents/). Each implements `send_prompt`, `run_test_command`, and `clear_context`; the concrete API and Pi adapter are documented by §AR-agent-api. |
| **Workflow driver** | Deterministic script in the `drivers/` subdirectory of [ai_workflows/](../ai_workflows/) that prepares the working environment, directories, branch/context, strategy bundle, workflow engine, agent, and metrics for one claimed unit of work. The driver runs Forge plumbing; Codex or another LLM agent should not decide that setup during a generated run. Specified by §WF-forge-workflow-drivers. |
| **Workflow engine** | A registered state-machine-like workflow implementation among the core workflow objects in `ai_workflows/core/` (today's [ai_workflows/workflow_strategies/](../ai_workflows/workflow_strategies/), relocated by §ROADMAP-forge-ai-workflows-structure), such as `basic_iterative` or `dynamic_access_iterative`. The engine owns prompts, command execution, retries, transitions, and terminal status selection for one run. |
| **Predefined strategy** | Named configuration bundle in [strategies/predefined_strategies.json](../strategies/predefined_strategies.json). It selects the workflow engine, agent backend, model, prompts, workflow parameters, optional MCPs, and optional persistent instructions. Selected via `--strategy-name`. |
| **Post-generation intervention** | The built-in recovery sequence the workflow base class runs when the post-iteration `./gradlew test` still fails during finalization. It is a fixed Codex-then-Pi lane, not a pluggable registry and not selected per strategy: a Codex metadata fix runs first (using the `fix-missing-reachability-metadata` skill, pinned to the run's GraalVM); only if that does not recover does Pi remove the offending failing tests as a last resort. When recovery makes the post-generation test pass, the run reports `SUCCESS_WITH_INTERVENTION_STATUS` and the intervention record (stage, intervention file, analysis) is saved for the run metrics and PR body. The base class runs this lane once per GraalVM test mode (current defaults and `future-defaults-all`). |
| **Human intervention** | A maintainer follow-up signal applied through the `human-intervention` issue or PR label when Forge has evidence that generated work, repository automation, or library execution semantics require human judgment. It is distinct from post-generation intervention, which is an automated recovery step. The policy is defined in §FS-human-intervention-policy. |
| **Dynamic access** | Reflection, JNI, resource access, serialization, or proxy use that GraalVM `native-image` cannot determine statically. |
| **Dynamic-access report** | JSON written by Gradle task `generateDynamicAccessCoverageReport` to `tests/src/<group>/<artifact>/<version>/build/reports/dynamic-access/dynamic-access-coverage.json`, listing classes and per-class call sites that require dynamic-access metadata, marked covered/uncovered. |
| **Dynamic-access exhaust report** | Durable coordinate-scoped JSON state for chunked dynamic-access work. It records the coordinate, issue number, class threshold, processed/exhausted/failed classes, and the latest published chunk PR/commit. It is stored in a stable location derived from the target test suite (for example under `tests/src/<group>/<artifact>/<version>/`) so orchestration scripts can find it from the coordinate alone. It does not predefine all chunks; each resume regenerates the current dynamic-access report and filters out exhausted classes. Specified by §WF-dynamic-access-exhaust-report. |
| **Chunked dynamic-access workflow** | Dynamic-access generation mode for oversized `library-new-request` and `library-update-request` issues. `forge_metadata.py` owns the class threshold decision and passes the current chunk size to the workflow. The workflow processes at most that many uncovered classes, publishes that chunk, then resumes after the chunk PR merges. PR linking rules are in §WF-chunked-dynamic-access-pr-linking. |
| **Source context** | Read-only files supplied to the agent. Types: `main` (library source), `test` (upstream tests), `documentation` (Javadoc). Selected by the strategy parameter `source-context-types`. |
| **Library update target** | The metadata and test directories selected for a `library-update-request` coordinate (§WF-improve-library-coverage.3). Resolution records the requested coordinate, match type (`tested-version`, `metadata-version`, `default-for`, or `new-version`), matched index entry, resolved metadata version, resolved test version, and edit directories. |

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

### 4.2 CLI inputs (common to all workflow drivers)

- `--coordinates <group:artifact:version>` — required.
- `--reachability-metadata-path` — overrides default reachability-repo clone.
  The Forge metrics directory is derived from it as the `forge/` subdirectory.
- `--strategy-name <name>` — selects an entry from `predefined_strategies.json`.
- `--docs-path` — additional read-only files for agent context.
- `-v` / `--verbose` — verbose agent output.
- `--keep-tests-without-dynamic-access` — only honored by dynamic-access
  workflows (§WF-dynamic-access-workflow).

The predefined strategy configuration contract — the required shape of every
`strategies/predefined_strategies.json` entry — is owned by the strategy
configuration architecture (§STRAT-forge-predefined-strategy-contract), not by
this functional spec.

### 4.3 Environment

- Either `GRAALVM_HOME` or `JAVA_HOME` must point to a GraalVM distribution.
  Both variables are then aligned to that distribution. If neither does, the
  workflow exits with an error.
- Agent repair steps must use the exact same GraalVM distribution as the
  Gradle or Native Image failure that triggered them. This is a hard
  requirement: Forge must pass the selected `GRAALVM_HOME`, `JAVA_HOME`, and
  full `native-image --version` output into Codex instructions, run Codex with
  `GRAALVM_HOME` and `JAVA_HOME` pinned to that same distribution, and fail
  instead of reproducing or verifying with a different GraalVM installation.
- `gh` CLI authenticated against the reachability repo is required for any
  script in `git_scripts/`.
- `FORGE_PARALLELISM` controls how many issue workflows the top-level worker
  may run concurrently. Valid values are `1` through `4`; the default is `1`.
- `FORGE_DO_WORK_STOP_FILE` overrides the shared stop marker path used by
  `do-work` loops. The default is `~/.metadata-forge-stop`.
- `FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD` configures the class-count threshold
  used by `forge_metadata.py` for `library-new-request` and
  `library-update-request` issues. The implementation-defined default is `5`.
  If the current dynamic-access report has more uncovered classes than this
  threshold, Forge uses chunked mode. The value is not passed through as a
  generic workflow policy; `forge_metadata.py` computes the concrete chunk size
  for each run.

### 4.4 Repository availability for test and metadata artifacts

- Every Forge workflow artifact that runs reachability-repo Gradle tasks for
  testing, dynamic-access reporting, native metadata exploration, metadata
  generation, or final verification must have the whole reachability repo
  available. The runnable artifact must be rooted in a complete
  `graalvm-reachability-metadata` checkout or worktree, not in a copied
  per-library directory, extracted dependency artifact, or otherwise partial
  tree.
- Complete repo availability includes access to the repo root, `gradlew`,
  Gradle build logic, shared test infrastructure, `metadata/`, `tests/`, and
  `forge/` when the workflow records metrics, logs, or resumable state.
- If Forge creates isolated worktrees, resumable artifacts, archives, or other
  execution contexts for parallel work, tests, or metadata collection, each
  context must preserve the complete reachability repo before running any
  Gradle-backed test or metadata collection step. Missing repo context is a
  hard setup failure.

## 5. Outputs

- **Per-run metrics record** persisted to
  `stats/<group>/<artifact>/<version>/execution-metrics.json`, as required by
  §FS-forge-run-metrics.
- **Durable session and generation logs** for every agent session,
  generation attempt, deterministic Gradle command, metadata fixup, native
  trace cycle, and local verification gate that contributes to the run, as
  required by §FS-durable-generation-logs.
- **Generated tests + metadata + index.json** committed on the feature branch
  `ai/add-lib-support-<group>-<artifact>-<version>` in the reachability repo.
- **Pull request** (only when invoked through `git_scripts/make_pr_*.py`).

### FS-forge-run-metrics: Per-run metrics record
§GOAL-minimize-generation-cost

Every run must persist a per-library metrics record to
`stats/<group>/<artifact>/<version>/execution-metrics.json` so cost, token,
coverage, and status evidence stays attached to the library version it describes
(§GOAL-maximize-library-coverage). During a run, in-flight metrics are staged
transiently in the Forge metrics directory as `.pending_metrics.json` and
consumed by the PR step; they are not a durable output. Benchmark-mode runs
instead record durable, schema-validated metrics under
`benchmarks/benchmark_results/` (§BENCH-forge-generation-benchmarking.4).

### FS-durable-generation-logs: Durable generation and session logs

Every Forge session and generation step must be logged and saved to a stable
path because durable logs are the primary debugging surface for generated work.
This includes agent prompts and responses, session or thread identifiers,
persistent-instruction state, Gradle command output, metadata-fix output,
native tracing output, post-generation intervention output, and local
verification output.

Logs must be scoped by task and coordinate where possible, and the workflow
must print or record the log path so a maintainer can inspect the exact
conversation or command that produced a generated artifact. A workflow must
not rely on transient terminal output as the only record of a generation step.
If a run fails or times out, the saved logs are part of the diagnostic artifact
set that allows maintainers or a later Forge run to continue from evidence,
keeping the loop short as called for by §GOAL-shorten-issue-to-shipped-metadata.

## 6. Local CI-Equivalent Verification

### FS-local-ci-equivalent-verification: Local CI-equivalent verification

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
uses, including `ci.json` and the Gradle task contracts. Where CI exercises more
than one native-image mode, local verification must too: Forge runs the
generated tests under both the current GraalVM defaults and the
`future-defaults-all` native-image mode (selected by `GVM_TCK_NATIVE_IMAGE_MODE`),
so a regression that only appears under future defaults is caught before a PR.

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

Local verification must also reject legacy test-only Native Image configuration
for the coordinate: if uncommitted or changed `META-INF/native-image` test
configuration files appear under the generated test sources, the run fails
rather than publishing them, because that config form is no longer accepted and
the reachability metadata belongs in the coordinate's `metadata/` directory.

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
paths that require maintainer review, following §FS-human-intervention-policy.

### FS-human-intervention-policy: Human intervention policy

The `human-intervention` label is a maintainer follow-up signal, not a generic
failure label. Forge must apply it only when the available evidence shows that
the work cannot be safely completed or trusted without human judgment about the
generated code, repository automation, metadata, or library behavior.

Valid human-intervention cases are semantic or generation failures inside
Forge's responsibility boundary, including:

- Generated tests, metadata, or workflow edits fail local verification in a way
  that points to the generated artifact or repository automation rather than a
  transient external service.
- The workflow cannot converge after its configured generation, retry, and
  recovery limits, and the saved logs point to a real library/test/metadata
  problem that needs maintainer analysis.
- Dynamic-access coverage remains missing or suspiciously low after a
  successful generation path, making the result misleading without manual
  follow-up.
- Local CI-equivalent verification passes only after shared repository files
  changed, so a maintainer must review repository-level effects before merge.
- Publication detects a severe metadata, test, or coverage anomaly that makes
  the PR unsafe to auto-review as a normal generated result.

Forge must not use `human-intervention` for failures that are only external or
transient infrastructure conditions. Connection errors, Maven repository
download failures, GitHub API/status failures, rate limits, runner outages,
temporary registry unavailability, and similar environmental failures must be
reported as infrastructure failures, retried or preserved with diagnostics as
appropriate, and left without the `human-intervention` label unless later
evidence shows a semantic Forge, repository, generation, or library problem.

The label can appear on issues or pull requests. On an issue, it means Forge
could not safely produce a PR-ready result and posted enough diagnostics for a
maintainer to continue. On a pull request, it means Forge produced a reviewable
artifact, but some part of the result needs explicit maintainer judgment before
normal review automation may treat it as safe. The companion
`human-intervention-fixed` PR label means a maintainer has addressed the manual
follow-up and review automation may resume after normal merge gates pass.

Post-generation intervention is different: it is an automated recovery step
inside a workflow. `SUCCESS_WITH_INTERVENTION_STATUS` is PR-eligible when the
automated intervention changed the working tree and the local CI-equivalent
verification still passed. That status should not by itself add the
`human-intervention` label unless the result also meets one of the policy cases
above.

### FS-automated-pr-review: Automated pull request review

Forge review automation processes open pull requests by their PR labels after
CI has completed. It is a PR review workflow, not an issue-resolution workflow:
it must inspect an already published PR, use an isolated review worktree,
compare the PR diff and status checks against the label-specific review rules,
and submit either an approval or a requested-changes review on GitHub.

Review labels select the review rule set. `library-new-request`,
`library-update-request`, `fixes-javac-fail`, `fixes-java-run-fail`,
`fixes-native-image-run-fail`, and bulk-update review labels each have their
own review expectations. The review prompt or skill must apply the rules for
the PR's label rather than using generic code-review judgment alone.

Review automation must skip PRs already labeled `human-intervention`. That
label means maintainer judgment is required before normal automated review may
continue, per §FS-human-intervention-policy. A PR labeled
`human-intervention-fixed` is the explicit maintainer signal that manual
follow-up has been completed; review automation may then dismiss stale
requested-changes reviews, approve, and merge only after normal merge gates
pass.

Automated review may add or request the `human-intervention` PR label only when
the applicable label-specific review rules say the result cannot be handled by
a normal approval or requested-changes review. Review uncertainty, transient CI
noise, GitHub status/API failures, Maven download failures, or other external
infrastructure errors must not be converted into `human-intervention` unless
the review rules identify a semantic generated-result, repository-automation,
metadata, or library-execution problem that requires maintainer judgment
(§FS-human-intervention-policy).

### FS-index-validation-safeguard: PR review and merge index validation

Forge review queues may merge approved pull requests only after GitHub reports
that the pull request is mergeable and all status gates are green. For any pull
request that changes a library index file matching
`metadata/<group>/<artifact>/index.json`, Forge must run a final local index
validation against the tree that would be merged into the current
`master` branch.

The final validation must use a fresh `origin/master`, apply the reviewed pull
request head without committing it, verify that the fetched head still matches
the reviewed head SHA, and run:

```bash
./gradlew validateIndexFiles -Pcoordinates=all --stacktrace
```

PR review automation must instruct the reviewer to run this final validation
for index-changing pull requests before approving (§FS-automated-pr-review). If
validation fails because tested versions are in the wrong metadata-version
bucket or duplicated across buckets, the reviewer must use the
`fix-index-file-inconsistencies` skill to repair only the affected `index.json`
files, commit the repair, push it to the same pull request branch, and rerun
full index validation before submitting the review. Forge must not add
`human-intervention` for these fixable index bucket inconsistencies
(§FS-human-intervention-policy).

Forge also runs the final index validation immediately before merging an
approved index-changing pull request as a safety net. If the reviewer did not
repair the pull request and this merge-time validation still fails, Forge must
not merge the pull request and the review pass may fail. Infrastructure
failures while preparing the validation candidate, such as fetch failures,
merge conflicts, or a changed pull request head SHA, remain hard automation
failures.

## 7. Run Status Semantics

Every workflow records one of these statuses:

| Status | Meaning |
| --- | --- |
| `RUN_STATUS_SUCCESS` | All generation gates and the local CI-equivalent verification passed; metadata and tests committed (see §FS-local-ci-equivalent-verification). |
| `SUCCESS_WITH_INTERVENTION_STATUS` | Tests succeeded after the built-in post-generation recovery modified the working tree (a Codex metadata fix, then Pi removing failing tests as a last resort), and the local CI-equivalent verification (§FS-local-ci-equivalent-verification) passed. The intervention's record is included in the run-metrics and PR description. PR-eligible; distinct from the `human-intervention` label unless §FS-human-intervention-policy separately requires that label. |
| `RUN_STATUS_CHUNK_READY` | A chunked dynamic-access run reached a reviewable class boundary and §FS-local-ci-equivalent-verification passed for the current part. The current part is PR-eligible, and the issue must not be resumed until the part PR has merged. |
| `RUN_STATUS_FAILURE` | The workflow could not converge or a quality gate failed; the feature branch is reset to the scaffold checkpoint and no PR is opened. |

The exit code is `0` for PR-eligible statuses and `1` for failure.

## 8. Chunked Dynamic-Access Semantics

For `library-new-request` and `library-update-request` issues, `forge_metadata.py`
must run or refresh the dynamic-access report before choosing the execution
mode. If the current report has more uncovered dynamic-access classes than the
configured class threshold, `forge_metadata.py` must invoke the matching
orchestration script with chunking flags: issue number and current chunk size.
The current chunk size is normally equal to the threshold; when fewer
unexhausted classes remain than the threshold, it is equal to the remaining
class count. For example, with threshold `5` and `7` uncovered classes, the
first chunk size is `5` and the second chunk size is `2`.

Chunked mode is automatic after the issue is marked with the
`chunked-dynamic-access` label. The normal project status remains the run-state
signal: `Todo` means Forge may claim the next chunk, `In Progress` means a chunk
is currently being generated or reviewed, and the final PR's `Fixes: #<issue>`
transition moves the issue to `Done`. Forge must not require an explicit
resume-state CLI flag; the exhaust report location must be derived from the
coordinate and loaded automatically by the orchestration scripts, as specified
by §WF-dynamic-access-exhaust-report.

Chunk PRs use `Refs: #<issue>` until the final chunk. Only the final chunk PR
may use `Fixes: #<issue>` and move the issue to `Done`. Non-final chunk PRs
must commit enough exhaust-report state for the next run to skip classes already
completed, skipped, exhausted, or failed in earlier chunks
(§WF-chunked-dynamic-access-pr-linking).

## FS-forge-workflow-spec-catalog: Workflow specifications

Forge workflow contracts (§WF-forge-workflow-system) are split by behavior:
dynamic-access generation (§WF-dynamic-access-workflow), native metadata
tracing and verification (§WF-native-metadata-tracing, gated by
§WF-native-test-verification-gate), Java failure repair
(§WF-java-fail-fix-workflow), native-image run repair
(§WF-native-image-run-fix-workflow), dynamic-access coverage improvement
(§WF-improve-library-coverage), and planned code coverage improvement
(§WF-code-coverage-improvement). Each is bound to a named configuration bundle
defined by §STRAT-forge-predefined-strategy-contract.

- (Future) Basic iterative workflow.

Forge E2E testing is a top-level functional test contract because it validates
the whole issue-processing process through `forge_metadata.py`, not one workflow
engine in isolation, as described in §E2E-forge-workflow-testing.

Forge benchmarking is a top-level benchmark contract because it compares
generation strategies across multiple `library-new-request` targets
(§BENCH-forge-generation-benchmarking) and records cost (in service of
§GOAL-minimize-generation-cost), token, iteration, LOC, coverage (in service
of §GOAL-maximize-library-coverage), dynamic-access, and metadata metrics.

The implementation roadmap orders the first known Forge spec gaps to close,
starting with fixture-backed E2E coverage for the orchestration boundary; see
§ROADMAP-forge-implementation.
