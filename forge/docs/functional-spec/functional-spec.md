# FS-forge-functional-spec: Forge functional specification

This spec realizes the Forge direction set out in §GOAL-forge-direction, in
service of §GOAL-maximize-library-coverage and
§GOAL-shorten-issue-to-shipped-metadata.

## 1. Purpose

### FS-forge-issue-resolution-goal: Forge issue resolution goal

The `forge/` directory automates the end-to-end resolution of supported GitHub
issue queues in the
[`oracle/graalvm-reachability-metadata`](https://github.com/oracle/graalvm-reachability-metadata)
repository (hereafter "the reachability repo"), so its supported-library surface
can grow faster than maintainers can investigate each request by hand
(§GRUND-forge-motivation). For a claimed issue, Forge composes LLM-based
code-generation agents with the reachability repo's own build, test,
metadata-generation, and reporting pipelines to generate or repair the library
tests and GraalVM reachability metadata the issue needs. A successful run
produces a verified `ai/**` branch with metrics and a durable publication
descriptor. Trusted GitHub Actions publisher code from the default branch then
creates the bot-authored pull request with enough context for maintainer review;
review and repository CI remain the final merge boundary.
This shortens the path described in §GOAL-shorten-issue-to-shipped-metadata.

A supported issue starts from a work label such as `library-new-request`,
`library-update-request`, `fails-javac-compile`, `fails-java-run`, or
`fails-native-image-run`. Section 2 (Scope) enumerates the queues Forge
resolves, and its "Origin of the `fails-*` queues" subsection records where the
`fails-*` work comes from. Forge drains each pipeline by priority tier rather
than by scan batch: every `high-priority` issue first, then every `priority`
issue, then the remaining issues.

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
   ordinary API and execution coverage beyond dynamic-access call coverage.
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

### Origin of the `fails-*` queues

Every `fails-*` queue originates from a failed version update, not from a human
report. The reachability repo's scheduled library version compatibility
automation bulk-tests newer upstream versions of already-supported libraries; it
records the versions that pass in a single `library-bulk-update` PR and files one
`fails-*` tracking issue per `(library, version)` whose update failed, labeled by
the stage that broke (`fails-javac-compile`, `fails-java-run`,
`fails-native-image-build`, or `fails-native-image-run`). Forge claims those
issues; it never opens them. The producer's contract is the reachability repo's
Library version update automation (§root/FS-library-version-update-automation).

## 3. Glossary

| Term | Definition |
| --- | --- |
| **Reachability metadata** | JSON describing reflection, JNI, resource, serialization, and proxy access for a library, consumed by GraalVM `native-image`. |
| **Reachability repo** | Local checkout or worktree of `oracle/graalvm-reachability-metadata`. The build and metadata-generation Gradle tasks run inside it. The parent checkout of `forge/` is used by default. |
| **Forge metrics directory** | The `forge/` subdirectory of the reachability checkout, used as the transient staging area for a run's in-flight metrics (`.pending_metrics.json`) until local finalization writes the publication descriptor. Durable per-library run metrics persist to `stats/<group>/<artifact>/<version>/execution-metrics.json` (§FS-forge-run-metrics). |
| **Forge publication descriptor** | Versioned, schema-validated JSON committed at `stats/<group>/<artifact>/<version>/forge-publication.json`. It is the durable, branch-controlled data handoff from locally verified Forge generation to the trusted Actions publisher (§GIT-publication-descriptor). |
| **Forge publication ID** | A run-unique identity derived before the publication commit and recorded in the descriptor. Chunked runs also record it and the unique head branch in their exhaust report so a later run can resolve the preceding PR without committing a GitHub-assigned PR number after publication (§GIT-chunked-linking). |
| **Forge Actions publisher** | Default-branch code triggered through a successful unprivileged Branch Ready run. It treats the feature branch as data, revalidates the exact head SHA and descriptor, renders the PR, and performs publication-related GitHub mutations with a short-lived GitHub App token (§GIT-actions-publication). |
| **Coordinate** | Maven coordinate of the target library, formatted `group:artifact:version`. |
| **Agent** | LLM-driven code editor (Codex or Pi) registered through [ai_workflows/agents/](../../ai_workflows/agents/). Each implements `send_prompt`, `run_test_command`, and `clear_context`; the concrete API and Pi adapter are documented by §AR-agent-api. |
| **Workflow driver** | Deterministic script in the `drivers/` subdirectory of [ai_workflows/](../../ai_workflows/) that prepares the working environment, directories, branch/context, strategy bundle, workflow engine, agent, and metrics for one claimed unit of work. The driver runs Forge plumbing; Codex or another LLM agent should not decide that setup during a generated run. Specified by §WF-forge-workflow-drivers. |
| **Workflow engine** | A registered state-machine-like workflow implementation among the core workflow objects in [ai_workflows/core/](../../ai_workflows/core/), such as `basic_iterative` or `dynamic_access_iterative`. The engine owns prompts, command execution, retries, transitions, and terminal status selection for one run. |
| **Predefined strategy** | Named configuration bundle in [strategies/predefined_strategies.json](../../strategies/predefined_strategies.json). It selects the workflow engine, agent backend, model, prompts, workflow parameters, optional MCPs, and optional persistent instructions. Selected via `--strategy-name`. |
| **Post-generation intervention** | The built-in recovery sequence the workflow base class runs when the post-iteration `./gradlew test` still fails during finalization. It is a fixed Codex-then-Pi lane, not a pluggable registry and not selected per strategy: a Codex metadata fix runs first (using the `fix-missing-reachability-metadata` skill, pinned to the run's GraalVM); only if that does not recover does Pi remove the offending failing tests as a last resort. When recovery makes the post-generation test pass, the run reports `SUCCESS_WITH_INTERVENTION_STATUS` and the intervention record (stage, intervention file, analysis) is saved for the run metrics and PR body. The base class runs this lane once per GraalVM test lane (current defaults and `future-defaults-all` on the latest GraalVM, plus current defaults on the GraalVM 25 toolchain). |
| **Human intervention** | A maintainer follow-up signal applied through the `human-intervention` issue or PR label when Forge has evidence that generated work, repository automation, or library execution semantics require human judgment. It is distinct from post-generation intervention, which is an automated recovery step. The policy is defined in §FS-human-intervention-policy. |
| **Dynamic access** | Reflection, JNI, resource access, serialization, or proxy use that GraalVM `native-image` cannot determine statically. |
| **Dynamic-access report** | JSON written by Gradle task `generateDynamicAccessCoverageReport` to `tests/src/<group>/<artifact>/<version>/build/reports/dynamic-access/dynamic-access-coverage.json`, listing classes and per-class call sites that require dynamic-access metadata, marked covered/uncovered. |
| **Dynamic-access exhaust report** | Durable coordinate-scoped JSON state for chunked dynamic-access work. It records the coordinate, issue number, class threshold, completed/skipped/exhausted/failed classes, and latest publication ID/branch. It is stored with the target test suite so orchestration can find it from the coordinate. It does not predefine chunks; each resume regenerates the report and filters processed classes. Legacy PR-number/commit fields remain readable during migration. Specified by §WF-dynamic-access-exhaust-report. |
| **Chunked dynamic-access workflow** | Dynamic-access generation mode for oversized `library-new-request` issues and `library-update-request` issues routed to dynamic-access coverage improvement. `forge_metadata.py` owns the class threshold decision and passes the current chunk size to the workflow. The workflow processes at most that many uncovered classes, publishes that chunk, then resumes after the chunk PR merges. PR linking rules are in §WF-chunked-dynamic-access-pr-linking. |
| **Source context** | Read-only files supplied to the agent. Types: `main` (library source), `test` (upstream tests), `documentation` (Javadoc). Selected by the strategy parameter `source-context-types`. |
| **Library update target** | The metadata and test directories selected for a `library-update-request` coordinate (§WF-improve-library-coverage.3). Resolution records the requested coordinate, match type (`tested-version`, `metadata-version`, `default-for`, or `new-version`), matched index entry, resolved metadata version, resolved test version, and edit directories. |

## 4. Requirements

Forge validates two kinds of requirement, split by the question each answers.
**Host requirements** (§FS-forge-host-requirements) are what the machine must
provide before any work is claimed — tools, credentials, Java lanes, filesystem,
network, environment, and the repository checkouts Forge runs in. They are the
same for every issue. **Run requirements** (§FS-forge-run-requirements) are what
one work cycle must resolve for itself — the strategy it will run, the issue it
may claim, the form of that issue, and the run context a driver is handed. Each
is a gate, and they run in that order (§AR-forge-workflow-pipeline).

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
- `FORGE_PARALLELISM` controls how many issue workflows the top-level worker
  may run concurrently. Valid values are `1` through `4`; the default is `1`.
- `FORGE_DO_WORK_STOP_FILE` overrides the shared stop marker path used by
  `do-work` loops. The default is `~/.metadata-forge-stop`.
- `FORGE_USER_REQUESTED_ISSUES_ONLY=1` restricts issue queue scans to
  user-requested issues by excluding configured automation and maintainer
  authors locally before claim processing (§ORCH-forge-orchestration-spec).

### FS-forge-host-requirements: Host requirements

§GOAL-shorten-issue-to-shipped-metadata §root/PRCPL-verify-inputs

Before Forge performs a self-update, queries queue state, claims an issue,
creates a review worktree, or invokes an agent, it must validate every host
capability the invoked mode needs, and it must do so without invoking a model or
mutating GitHub. This section is the complete list: what `forge_metadata.py`
needs in order to work is what the host requirements gate checks, and the
requirement has exactly one definition, so a repeated check elsewhere resolves
to the same rule rather than a second one.

The host must provide:

- **Executables on `PATH`**: the selected Python interpreter, `git`, the GitHub
  CLI, Pi, Codex, the Docker CLI, the pinned `grype` release, and the selected
  repository's Gradle wrapper.
- **A usable GitHub account**: the CLI authenticated, `Contents`, `Issues`, and
  `Pull requests` write on the repository, and `Projects` write on the tracked
  project board — read access alone is not enough, because Forge assigns and
  labels issues, comments, pushes generated branches, submits reviews, and
  merges eligible pull requests. The local process never holds the Forge
  publisher App credentials: it neither opens pull requests nor creates
  publication follow-up issues.
- **Agents that are usable, not merely installed**: Pi authenticated against its
  provider with unattended tool approval, Codex configured for unattended runs,
  and a writable state directory for each.
- **Every Java lane**, each pointing at a GraalVM distribution that provides
  Native Image and contains the reachability-metadata schema the checked-out
  repository requires: `GRAALVM_HOME` at the latest published GraalVM GA
  release, `GRAALVM_HOME_LATEST_EA` at the newest published Oracle GraalVM EA
  build, and `GRAALVM_HOME_25_0` at the repository-pinned 25.0.x release in
  `graalvm-versions.json`. The operator updates the pinned value deliberately;
  GA and EA freshness are resolved from their authoritative release metadata on
  every run. `GRAALVM_HOME` and `JAVA_HOME` are aligned to one distribution, and
  a review-only host needs a working JDK on `JAVA_HOME` instead of the lanes.
  Every agent repair step runs against the exact distribution whose Gradle or
  Native Image failure triggered it — the selected `GRAALVM_HOME`, `JAVA_HOME`,
  and full `native-image --version` output go into the agent's instructions and
  its environment — and Forge fails rather than reproducing or verifying against
  a different installation.
- **Access to the Docker daemon**, not just the client.
- **Filesystem write access** to the Forge checkout, the `.git` directory of
  every checkout the run writes branches into, `local_repositories`, and
  temporary Gradle state.
- **Network reachability** on 443 to GitHub, the agent provider, the Gradle
  distribution and plugin services, Maven Central, and the Docker registry and
  its auth endpoint. Reachability is checked over the route Forge's own tools
  take — directly, or through the proxy the environment configures for HTTPS —
  so a proxied host is not reported as offline.
- **Git transport** to the monitored branch from every checkout the run uses.
- **A complete reachability repository** for anything that runs its Gradle
  tasks: testing, dynamic-access reporting, native metadata exploration,
  metadata generation, and final verification must run inside a whole
  `graalvm-reachability-metadata` checkout or worktree — repository root,
  `gradlew`, Gradle build logic, shared test infrastructure, `metadata/`,
  `tests/`, and `forge/` when the run records metrics, logs, or resumable
  state — never in a copied per-library directory, an extracted dependency
  artifact, or any partial tree. Isolated worktrees, resumable artifacts, and
  archives must each preserve that whole context; missing repository context is
  a hard setup failure.

Two path sets are validated for what each owns. Forge-owned paths — the Forge
checkout, its `local_repositories`, the pinned GraalVM configuration, and the
self-update remote — always resolve from the checkout that contains Forge.
Repository-owned paths — the Gradle wrapper, the Git metadata generated branches
are written into, and the origin remote they are pushed to — resolve from the
repository the run selected, which may be a different checkout. The selected
repository is therefore resolved before the gate runs, so a run against another
checkout can neither pass on a broken target nor be rejected because an
unrelated parent checkout is broken.

Requirements are scoped to what the invoked mode actually does: a review-only
run does not need the issue-work Java lanes, Codex, or Docker; an issue run
needs everything its enabled queues select; a fixture-testing run needs no live
GitHub access, because it mutates no GitHub state. Only the release-version
comparison for the Java lanes is relaxable, and relaxing it never waives Native
Image or the reachability-metadata schema.

The gate must always print an operator-facing report. Each entry names the
capability, whether the invoked mode requires it, the exact executable,
environment variable, path, host, or permission checked, and — on failure —
exactly one concrete `Fix:` instruction, with current and required values shown
explicitly and command output reduced to the relevant fact.

The gate runs at the start of every work-starting `forge_metadata.py`
invocation, not only from the worker loop, and because the worker re-execs
itself between cycles, every new worker process revalidates the host before
doing more work. Any failed required check stops the run with a non-zero exit
before the first work cycle. Capabilities the mode does not need are reported as
not required, a relaxed version mismatch does not fail startup, and commands
that start no work — stop, resume, help, cache maintenance — do not run the gate
at all.

### FS-forge-run-requirements: Run requirements


A satisfied host says nothing about whether a given cycle may do work. Before a
run reaches a workflow driver, four things must be resolved and verified, each
before the side effect it protects, and each by deterministic code rather than
by an agent (§root/PRCPL-prefer-algorithmic, §root/PRCPL-verify-inputs). 
A run requirement that cannot be satisfied must fail where it is checked, naming what was missing or ambiguous,
and must leave GitHub in the state it found: a rejection after a claim releases
the claim and returns the issue to `Todo` rather than failing the issue.

#### 1. Strategy and model

Every enabled queue must resolve its configured strategy name against the
registry before scanning begins, and a strategy bundle must be rejected on load
unless it names a model and carries the prompts and parameters its workflow
engine declares as required (§STRAT-forge-predefined-strategy-contract). An
unresolvable strategy or model is a worker configuration error: the worker must
exit before it scans a queue, not fail one issue at a time.

#### 2. Issue claimability

Claiming is exclusive, and the decision must be made against live GitHub state
rather than the scan results that led to it (§ORCH-forge-orchestration-spec).
The issue payload is re-read at claim time and must still be open, still carry
the queue label, be unassigned or assigned only to the authenticated user, carry
no open blockers, and sit in project status `Todo`. A `resumable` issue
additionally requires a valid continuation marker on a preserved branch
(§FS-forge-run-continuation), and a `chunked-dynamic-access` issue requires its
exhaust report (§WF-dynamic-access-exhaust-report). Only once every condition
holds may the issue be assigned and moved to `In Progress`.

#### 3. Issue form

The queue label decides the workflow, so the issue must be unambiguous before a
driver starts (§WF-forge-workflow-drivers.2). The title must resolve to Maven
coordinates and those coordinates must resolve to a published artifact; a
`fails-*` issue must resolve a current `latest` metadata version for the
coordinate and must request a version strictly above it; a
`library-update-request` must resolve to exactly one driver, recorded so
publication reports the workflow that actually ran; and an issue must carry
exactly one workflow label. The last three are contract that no code enforces
today, which §ROADMAP-forge-issue-form-enforcement closes.

**Coordinates resolve when the artifact is fetchable.** A title that parses as
`group:artifact:version` has satisfied a regular expression, not the
requirement. The run needs an artifact the build can actually resolve, so the
gate confirms the coordinate is published in one of the repositories the
harness resolves against — Maven Central, then the Confluent fallback
(§root/AR-build-infrastructure.1). A typo in a group, an artifact that was
never published, or a version that does not exist upstream is decidable from
the repository's own layout, so it is decided here rather than surfacing later
as a Gradle resolution error inside a run that already holds a claim, a project
transition, and a worktree (§root/PRCPL-verify-inputs). Only the artifact's
existence is checked — its content is a driver concern, and Native Image
eligibility remains where it is (§WF-forge-workflow-drivers.2).

**A rejection is reported on the issue.** Every rule above is decided from the
issue payload and the repository, so when one fails the gate knows exactly
which rule failed and what value failed it. That is what the reporter needs and
what a worker log does not give them: an issue rejected in silence is rescanned
and re-rejected every cycle, and nothing about it changes because nobody was
told. The rejection therefore posts one predefined comment naming the failed
rule, quoting the offending value, and stating what the issue must carry
instead. The comment is per (rule, offending value), so a rescan of an
unchanged issue posts nothing and an edited title is judged afresh.

A form rejection is not a workflow failure: it is an input defect outside
Forge's generation boundary, so it carries no `human-intervention` label and
preserves no branch (§FS-human-intervention-policy). Like every requirement
here it leaves GitHub as it found it — a rejection reached after a claim
releases the claim and returns the issue to `Todo`.

#### 4. Run context

A driver must be handed a complete run context and must not resolve, clone, or
re-derive any part of it: the resolved coordinates, validated strategy,
isolated reachability worktree, scratch metrics repository, setup-evidence
directory, issue context, one GraalVM environment pinned for the whole run, and
the continuation marker (§FS-forge-run-continuation). The driver owns normal
and neural setup inside that context, including the persisted library
preparation result; it does not own queue policy or repository resolution. The
gap between this requirement and the current implementation is
§ROADMAP-forge-dispatcher-owned-run-preconditions.

## 5. Outputs

- **Per-run metrics record** persisted to
  `stats/<group>/<artifact>/<version>/execution-metrics.json`, as required by
  §FS-forge-run-metrics.
- **Durable session and generation logs** for every agent session,
  generation attempt, deterministic Gradle command, metadata fixup, native
  trace cycle, and local verification gate that contributes to the run, as
  required by §FS-durable-generation-logs.
- **Generated tests + metadata + index.json** committed on a per-run feature
  branch in the reachability repo, named `ai/<authenticated-login>/<suffix>`
  where `<suffix>` is workflow-specific and encodes the coordinate (e.g.
  `add-lib-support-<group>-<artifact>-<version>` for new-library support,
  `improve-coverage-…`, `fix-javac-…`, `fix-java-run-…`,
  `fix-native-image-run-…`, `not-for-native-image-…`). Publication branches are
  unique per publication ID and are pushed directly to
  `oracle/graalvm-reachability-metadata` so the repository's `push` workflow can
  observe them.
- **Pull request** created asynchronously by the trusted Forge Actions
  publisher after Branch Ready accepts the exact pushed SHA.

### FS-forge-run-metrics: Per-run metrics record
§GOAL-minimize-generation-cost

Every run must persist a per-library metrics record to
`stats/<group>/<artifact>/<version>/execution-metrics.json` so cost, token,
coverage, and status evidence stays attached to the library version it describes
(§GOAL-maximize-library-coverage). During a run, in-flight metrics are staged
transiently in the Forge metrics directory as `.pending_metrics.json` and
consumed while constructing the durable publication descriptor; they are not a
durable output. Benchmark-mode runs
instead record durable, schema-validated metrics under
`benchmarks/benchmark_results/` (§BENCH-forge-generation-benchmarking.4).
Run metrics may identify the generated metadata artifact, but must not record a
single test-file artifact: the version-controlled test tree and pull-request
diff are the authoritative set of test files for the run.
When a failed run is preserved for continuation, `.pending_metrics.json` carries
the accumulated token, cached-token, cost, and iteration counters for the
preserved work. Any later resumed run for the same library must add its current
counters to the pending counters before writing durable metrics or publication
metrics, so repeated continuation attempts do not reset usage accounting.

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

## 6. Local Pre-Publication Verification

### FS-local-ci-equivalent-verification: Local pre-publication verification

Every Forge task must pass local verification before it is allowed to produce a
PR-eligible result. Verification is split into two tiers along the boundary
between what a single-library generation can settle on its own and what it
cannot: a library-scoped tier that runs during generation and finalization
(§FS-local-ci-equivalent-verification.1), and a cross-cutting gate that runs
before publication (§FS-local-ci-equivalent-verification.2).

Local verification runs must be non-privileged in both tiers. Forge must not
invoke `sudo`, must not run scripts that invoke `sudo`, and must not prompt for
an administrator password during local automation. CI-only host mutation steps
that require elevated privileges, such as changing system Docker networking,
must be replaced by no-sudo local gates or omitted from local execution. A
command that would require `sudo` is a local verification failure, not an
interactive prompt.

Forge must record local verification commands and outcomes in run metrics and
the publication descriptor so the trusted renderer includes them in the PR
description. A task must not push a publication descriptor, mark a project item
`Done`, return `RUN_STATUS_SUCCESS`, return `SUCCESS_WITH_INTERVENTION_STATUS`,
or return `RUN_STATUS_CHUNK_READY` until both tiers have passed. If Forge cannot
complete either tier, the workflow must return `RUN_STATUS_FAILURE` and preserve
enough diagnostics for human follow-up.

#### 1. Generation and finalization

Library-scoped correctness is established during generation and finalization,
because a single-library generation owns and can fully settle these checks.
Forge runs the generated tests for the coordinate under the CI native-image
surface: current GraalVM defaults and the `future-defaults-all` mode on the
latest GraalVM, plus current defaults on the GraalVM 25 toolchain (selected by
`GRAALVM_HOME_25_0`). Each native lane may run a bounded metadata/intervention
fixup and retry, so a regression that only appears under future defaults or on
GraalVM 25 is caught before publication. Finalization then validates the
coordinate's metadata with `checkMetadataFiles`, derives missing
`allowed-packages`, applies and checks style, regenerates library stats, and
rejects legacy test-only Native Image configuration: if uncommitted or changed
`META-INF/native-image` test configuration files appear under the generated test
sources, the run fails rather than publishing them, because that config form is
no longer accepted and the reachability metadata belongs in the coordinate's
`metadata/` directory.

#### 2. Pre-publication gate

Before a task may produce a PR-eligible result, Forge must also pass a
pre-publication gate. This tier exists because the generated branch is rebased
onto the current `master` before publication, and some checks can then fail for
reasons no single-library generation could have settled: they depend on
repository state that only exists once the branch sits on top of the latest
`master`. An index-file bucket that another merged PR has since changed, a newly
flagged Docker image, or a stray edit outside the coordinate's library scope can
break the whole PR even though the generated metadata and tests are themselves
valid. The gate therefore runs exactly the post-rebase, cross-cutting checks:
index-file validation (`validateIndexFiles`, an aggregate over the rebased
`master` state), Docker-image vulnerability scanning when allowed-image files
change, and the human-intervention classification that detects changes outside
the coordinate's library scope.

The gate intentionally does not re-run the library-scoped checks that
finalization (§FS-local-ci-equivalent-verification.1) already established, and by
default it does not re-run the native test matrix or the Spring AOT smoke tests,
which the generation lanes and repository CI cover. Forge must, however, expose
an opt-in full-reproduction option (`reproduce_full_ci`) for operators and
programmatic callers that need to reproduce the expensive per-PR CI surface
locally: when enabled, the gate additionally expands the changed-metadata native
test matrix, pre-pulls Docker images for coordinates that declare them, runs the
generated tests under the CI native-image mode matrix, and runs Spring AOT smoke
verification when metadata changes affect Spring AOT projects.

If the gate fails, Forge must run one bounded agent repair before the run is
handed off, on the same terms as every other agent repair in the pipeline: the
agent is reached only after a check has failed, is given that check's records as
its evidence, gets one attempt, and the gate then re-runs. The repair may touch
generated library-scoped files, or shared repository files when the failure is
caused by the repository itself. The outcome is decided by the deterministic
re-run and never by the agent's account of what it repaired
(§root/PRCPL-verify-inputs); a re-run that still fails is a handoff under
§FS-human-intervention-policy. The same discipline applies to a non-approval
from the pre-push review, except that a repair which does not pass the re-run
resets to the verified pre-repair commit and publishes with the verdict
recorded, because a review finding must not destroy an otherwise publishable
run (§ROADMAP-forge-local-branch-review). After the gate passes, Forge must
algorithmically compare the final PR diff with the expected library-scoped
paths. If any shared repository file changed, the PR must be labeled
`human-intervention` and the verification metrics and PR description must list
the repository-level paths that require maintainer review, following
§FS-human-intervention-policy.

### FS-library-update-tested-version-split: Library-update tested-version split

A `library-update-request` entry often lists several tested versions of the same
library at once (for example `["1.1", "1.2", "1.3"]`). When a coverage-improvement
run regenerates the JVM tests for such an entry, the new tests can pass on the
entry's own version yet stop compiling or running against a *later* tested
version. Forge must catch that break before the branch becomes PR-eligible and
split the entry, so the PR keeps the regenerated progress for the versions that
still pass while the repository keeps its existing support for the rest.

**Version sweep.** Before publication (§FS-local-ci-equivalent-verification),
Forge runs a Java-only sweep. It runs `javaTest` for the changed coordinate once
per tested version, walking the entry's `tested-versions` in order with
`GVM_TCK_LV` set to each version, and stops at the first version that fails. The
sweep is deliberately narrower than full CI — it skips the native-image matrix —
because it only needs to catch JVM test code that no longer works on a later
version.

**Progress output.** Forge must report the sweep on the CLI: the changed
coordinate, how many versions it will check, each version as it starts, the log
path for that version, and whether the version passed or failed. When every
version passes, the output must say plainly that no split is needed.

**Outcome.** If the *first* version fails there is no passing prefix to keep, so
Forge fails publication instead of splitting. If a *later* version fails, Forge
splits the index entry at that first failing version into two entries:

| | `metadata-version` | `tested-versions` | `latest` | contents |
|---|---|---|---|---|
| **Current entry** | unchanged | the passing prefix | kept unless it moves to the successor | the regenerated metadata and tests from this PR |
| **Successor entry** | the first failing version | the failing version and every later one | inherited when the split entry had `latest: true` | baseline metadata and tests copied from the PR base commit |

**Successor contents.** The successor entry must preserve the repository's
pre-generation support for the failing range. Forge copies the metadata and test
directories from the PR base commit entry that originally covered the failing
version — using that entry's `metadata-version` and `test-version` when present —
into `metadata/<group>/<artifact>/<failing-version>` and
`tests/src/<group>/<artifact>/<failing-version>`. The PR then ships the new
generated progress for the passing prefix and keeps baseline support for the
successor range. Forge regenerates library stats for the failing version after
creating the successor entry and publishes them under
`stats/<group>/<artifact>/<failing-version>`.

**Follow-up issue.** On every split, Forge also opens a `library-update-request`
issue for the successor metadata version and holds it in `In Progress` so the
queue cannot claim it early. The PR references this issue but does not close it,
through the `Refs:` line and `Forge-Unblocks-Issue:` trailer of §GIT-pr-body.
Once the PR merges, Forge releases the issue — clearing its assignees and moving
its project status to `Todo` — so the successor update enters normal processing.

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
transient infrastructure conditions. The issue-side classification is by failure
origin, not by log keywords: a workflow failure is **logical** — and gets the
label — when it comes from the driver script, the core workflow, or the local
CI-equivalent verification (compile, test, native-test, and finalization gates),
which includes the rare case of an agent timeout. A workflow failure is
**external** — and must not get the label — only when it surfaces as a typed
exception from the dependency boundary Forge itself crosses: GitHub (`gh`: rate
limits and 5xx/network) as `GitHubError` / `GitHubRateLimitExceeded`, remote
git operations (push/pull/fetch/clone/ls-remote) as `GitTransportError`, and a
Gradle build that never left configuration as `GradleBootstrapFailure`. Maven
Central and Docker registry failures have no such boundary — Gradle owns them
inside `./gradlew test` and they reach Forge only as an opaque CI-check `rc != 0`,
indistinguishable from a real test failure once the in-workflow retries have run —
so they are not special-cased and fall through to the safe logical default. When a
failure is external, Forge takes no issue action: it applies no
`human-intervention` label and posts no comment, and silently releases the issue
claim (status back to `Todo`, assignees cleared) so the issue is retried later.
Rate limits and shared bootstrap failures additionally stop the current run for
a later retry.

The label can appear on issues or pull requests. On an issue, it means Forge
could not safely produce a PR-ready result and posted enough diagnostics for a
maintainer to continue. On a pull request, it means Forge produced a reviewable
artifact, but some part of the result needs explicit maintainer judgment before
normal review automation may treat it as safe. The companion
`human-intervention-fixed` PR label means a maintainer has addressed the manual
follow-up and review automation may resume after normal merge gates pass. The
resulting labeled backlog is drained by automated resolution, not per-item
manual triage. The preserved work branch
additionally carries a continuation marker, so a later run can resume the issue
from the phase that failed instead of regenerating from scratch
(§FS-forge-run-continuation).


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
pass, including the index validation safeguard for index-changing pull
requests.

Bot authorship does not disqualify the maintainer recorded as the descriptor's
`producer` from reviewing the PR. Review eligibility continues to exclude only
the authenticated review worker when that same account is the GitHub PR author;
it does not treat descriptor provenance as authorship.

Before launching a review agent, Forge must validate GitHub CLI authentication
in the orchestration process and use Pi's deterministic authentication check for
the configured review provider and model. Neither check may invoke a model.
The review agent must run in an execution environment that can use the
authenticated `gh` session without an interactive approval boundary; either
authentication failure must stop the queue before the agent starts.

Automated review may add or request the `human-intervention` PR label only when
the applicable label-specific review rules say the result cannot be handled by
a normal approval or requested-changes review. Review uncertainty, transient CI
noise, GitHub status/API failures, Maven download failures, or other external
infrastructure errors must not be converted into `human-intervention` unless
the review rules identify a semantic generated-result, repository-automation,
metadata, or library-execution problem that requires maintainer judgment
(§FS-human-intervention-policy).


## 7. Run Status Semantics

Every workflow records one of these statuses:

| Status | Meaning |
| --- | --- |
| `RUN_STATUS_SUCCESS` | All generation gates and the local CI-equivalent verification passed; metadata and tests committed (see §FS-local-ci-equivalent-verification). |
| `SUCCESS_WITH_INTERVENTION_STATUS` | Tests succeeded after the built-in post-generation recovery modified the working tree (a Codex metadata fix, then Pi removing failing tests as a last resort), and the local CI-equivalent verification (§FS-local-ci-equivalent-verification) passed. The intervention's record is included in the run-metrics and PR description. PR-eligible; distinct from the `human-intervention` label unless §FS-human-intervention-policy separately requires that label. |
| `RUN_STATUS_CHUNK_READY` | A chunked dynamic-access run reached a reviewable class boundary and §FS-local-ci-equivalent-verification passed for the current part. The current part is PR-eligible, and the issue must not be resumed until the part PR has merged. |
| `RUN_STATUS_FAILURE` | The workflow could not converge or a quality gate failed; the feature branch is reset to its workflow recovery checkpoint and no PR is opened. Iterative dynamic-access exploration advances that checkpoint after each committed class (§WF-dynamic-access-fallback-and-failure); other workflows retain their specified checkpoint behavior. |

The exit code is `0` for PR-eligible statuses and `1` for failure.

## 8. Chunked Dynamic-Access Semantics

- `FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD` configures the class-count threshold
  used by `forge_metadata.py` for `library-new-request` issues and
  `library-update-request` issues routed to dynamic-access coverage improvement,
  and the post-repair exploration decision for `fails-javac-compile` and
  `fails-java-run` issues.
  The implementation-defined default is `15`.
  If the current dynamic-access report has more uncovered classes than this
  threshold, Forge uses chunked mode for new-library and library-update work.
  Java-fix reports cannot be generated before their primary repair succeeds, so
  `forge_metadata.py` passes the threshold to their shared driver; the composite
  workflow evaluates it immediately after the repair and skips oversized
  exploration. Before the verified push, Forge creates or reuses a new
  `library-update-request` for the fixed version, parks it until the repair
  merges, and records the issue number as a typed follow-up fact in the
  descriptor; the Actions publisher only references it. That issue then enters
  the ordinary library-update workflow, where dispatcher-owned chunking
  regenerates the report and selects chunks. The skip is decided exactly once:
  the composite records it on the continuation marker's `explore` phase, and
  descriptor creation reads that phase instead of regenerating a report. The
  marker also records the created issue number, so retries of one publication
  ID reuse one follow-up issue. For ordinary chunked runs, `forge_metadata.py`
  still computes the concrete chunk size rather than passing a generic workflow
  policy.

For `library-new-request` issues and `library-update-request` issues routed to
dynamic-access coverage improvement, `forge_metadata.py` must run or refresh the
dynamic-access report before choosing the execution mode. If the current report
has more uncovered dynamic-access classes than the configured class threshold,
`forge_metadata.py` must invoke the matching orchestration script with chunking
flags: issue number and current chunk size.
The current chunk size is normally equal to the threshold; when fewer
unexhausted classes remain than the threshold, it is equal to the remaining
class count. For example, with threshold `15` and `22` uncovered classes, the
first chunk size is `15` and the second chunk size is `7`.

Chunked mode is automatic after the issue is marked with the
`chunked-dynamic-access` label. The normal project status remains the run-state
signal: `Todo` means Forge may claim the next chunk, `In Progress` means a chunk
is currently being generated or reviewed, and the final PR's `Fixes: #<issue>`
transition moves the issue to `Done`. If a non-final chunk PR has failed CI and
no failed-job rerun remains available, Forge must move the issue back to `Todo`
and mark that PR for human follow-up so a replacement chunk can be generated.
Forge must not require an explicit resume-state CLI flag; the exhaust report
location must be derived from the coordinate and loaded automatically by the
orchestration scripts, as specified by §WF-dynamic-access-exhaust-report. When
the issue is being resumed from a preserved failed-run continuation marker,
Forge may proceed without a coordinate-local exhaust report and use
`explore.exhaustedClasses` from the marker as the processed-class set for the
resumed run (§FS-forge-run-continuation.2).

Chunk PRs use `Refs: #<issue>` until the final chunk. Only the final chunk PR
may use `Fixes: #<issue>` and move the issue to `Done`. Non-final chunk PRs
must commit enough exhaust-report state for the next run to skip classes already
completed, skipped, exhausted, or failed in earlier chunks
(§WF-chunked-dynamic-access-pr-linking).

Before the single verified push, every chunk also records its publication ID
and unique publication branch in the exhaust report. The publisher repeats the
ID in a machine-readable PR-body trailer. A later chunk loads the merged report,
resolves the preceding PR by the exact head branch, verifies the matching
publication ID and merged state, and checks that the merge commit is an ancestor
of its new base. Forge must not create a second post-publication commit merely
to store a GitHub-assigned PR number.

## FS-forge-workflow-spec-catalog: Workflow specifications

The whole workflow system contract is §WF-forge-workflow-system. Each supported
queue (Scope, section 2) is entered by a deterministic workflow driver
(§WF-forge-workflow-drivers) that prepares one run and delegates to the workflow
engine governed by a workflow spec. The driver scripts and the workflow specs
they run:

| Queue | Driver script | Workflow spec |
| --- | --- | --- |
| `library-new-request` | `add_new_library_support.py` | new library support (§WF-add-new-library-support), which runs dynamic-access generation plus native metadata tracing and verification |
| `library-update-request` | `improve_library_coverage.py`, or the missing-version router | dynamic-access coverage improvement (§WF-improve-library-coverage), Java repair (§WF-java-fail-fix-workflow), or native-image run repair (§WF-native-image-run-fix-workflow) depending on the compatibility probe |
| `fails-javac-compile` | `fix_javac_fail.py` | Java failure repair (§WF-java-fail-fix-workflow) |
| `fails-java-run` | `fix_java_run_fail.py` | Java failure repair (§WF-java-fail-fix-workflow) |
| `fails-native-image-run` | `fix_ni_run.py` | native-image run repair (§WF-native-image-run-fix-workflow) |
| code coverage improvement (planned) | — | code coverage improvement (§WF-code-coverage-improvement) |

Each engine is bound to a named configuration bundle defined by
§STRAT-forge-predefined-strategy-contract. The `basic_iterative` engine is not a
separate queue: it is the most basic workflow and the fallback the dynamic-access
workflow delegates to when a library turns out to have no dynamic access
(§WF-basic-iterative).

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
