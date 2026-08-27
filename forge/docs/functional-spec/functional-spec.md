# FS-forge-functional-spec: Forge functional specification

This spec realizes the Forge direction set out in §GOAL-forge-direction, in
service of §GOAL-maximize-library-coverage and
§GOAL-shorten-issue-to-shipped-metadata.

## FS-forge-issue-resolution-goal: Forge issue resolution goal

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
`fails-native-image-run`. §FS-forge-scope enumerates the queues Forge resolves,
and §FS-forge-scope.1 records where the `fails-*` work comes from. Forge drains
each pipeline by priority tier rather than by scan batch: every `high-priority`
issue first, then every `priority` issue, then the remaining issues.

## FS-forge-scope: Supported issue queues

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

### 1. Origin of the `fails-*` queues

Every `fails-*` queue originates from a failed version update, not from a human
report. The reachability repo's scheduled library version compatibility
automation bulk-tests newer upstream versions of already-supported libraries; it
records the versions that pass in a single `library-bulk-update` PR and files one
`fails-*` tracking issue per `(library, version)` whose update failed, labeled by
the stage that broke (`fails-javac-compile`, `fails-java-run`,
`fails-native-image-build`, or `fails-native-image-run`). Forge claims those
issues; it never opens them. The producer's contract is the reachability repo's
Library version update automation (§root/FS-library-version-update-automation).

## FS-forge-glossary: Glossary

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
| **Workflow driver** | Deterministic script in the `drivers/` subdirectory of [ai_workflows/](../../ai_workflows/) that prepares the working environment, directories, branch/context, strategy bundle, workflow engine, agent, and metrics for one claimed unit of work. The driver runs Forge plumbing; Codex or another LLM agent should not decide that setup during a generated run. Specified by §AR-forge-drivers. |
| **Workflow engine** | A registered state-machine-like workflow implementation among the core workflow objects in [ai_workflows/core/](../../ai_workflows/core/), such as `basic_iterative` or `dynamic_access_iterative`. The engine owns prompts, command execution, retries, transitions, and terminal status selection for one run. |
| **Predefined strategy** | Named configuration bundle in [strategies/predefined_strategies.json](../../strategies/predefined_strategies.json). It selects the workflow engine, agent backend, model, prompts, workflow parameters, optional MCPs, and optional persistent instructions. Selected via `--strategy-name`. |
| **Post-generation intervention** | The built-in recovery sequence the workflow base class runs when the post-iteration `./gradlew test` still fails during finalization. It is a fixed Codex-then-Pi lane, not a pluggable registry and not selected per strategy: a Codex metadata fix runs first (using the `fix-missing-reachability-metadata` skill, pinned to the run's GraalVM); only if that does not recover does Pi remove the offending failing tests as a last resort. When recovery makes the post-generation test pass, the run reports `SUCCESS_WITH_INTERVENTION_STATUS` and the intervention record (stage, intervention file, analysis) is saved for the run metrics and PR body. The base class runs this lane once per GraalVM test lane (current defaults and `future-defaults-all` on the latest GraalVM, plus current defaults on the GraalVM 25 toolchain). |
| **Human intervention** | A maintainer follow-up signal applied through the `human-intervention` issue or PR label when Forge has evidence that generated work, repository automation, or library execution semantics require human judgment. It is distinct from post-generation intervention, which is an automated recovery step. The policy is defined in §FS-human-intervention-policy. |
| **Dynamic access** | Reflection, JNI, resource access, serialization, or proxy use that GraalVM `native-image` cannot determine statically. |
| **Dynamic-access report** | JSON written by Gradle task `generateDynamicAccessCoverageReport` to `tests/src/<group>/<artifact>/<version>/build/reports/dynamic-access/dynamic-access-coverage.json`, listing classes and per-class call sites that require dynamic-access metadata, marked covered/uncovered. |
| **Dynamic-access exhaust report** | Durable coordinate-scoped JSON state for chunked dynamic-access work. It records the coordinate, issue number, class threshold, completed/skipped/exhausted/failed classes, and latest publication ID/branch. It is stored with the target test suite so orchestration can find it from the coordinate. It does not predefine chunks; each resume regenerates the report and filters processed classes. Legacy PR-number/commit fields remain readable during migration. Specified by §WF-dynamic-access-exhaust-report. |
| **Chunked dynamic-access workflow** | Dynamic-access generation mode for oversized `library-new-request` issues and `library-update-request` issues routed to dynamic-access coverage improvement. `forge_metadata.py` owns the class threshold decision and passes the current chunk size to the workflow. The workflow processes at most that many uncovered classes, publishes that chunk, then resumes after the chunk PR merges. PR linking rules are in §WF-chunked-dynamic-access-pr-linking. |
| **Source context** | Read-only files supplied to the agent. Types: `main` (library source), `test` (upstream tests), `documentation` (Javadoc). Selected by the strategy parameter `source-context-types`. |
| **Library update target** | The metadata and test directories selected for a `library-update-request` coordinate (§AR-forge-driver-queues.2). Resolution records the requested coordinate, match type (`tested-version`, `metadata-version`, `default-for`, or `new-version`), matched index entry, resolved metadata version, resolved test version, and edit directories. |

## FS-forge-requirements: Requirement gates

Forge validates two kinds of requirement, split by the question each answers.
**Host requirements** (§FS-forge-host-requirements) are what the machine must
provide before any work is claimed — tools, credentials, Java lanes, filesystem,
network, environment, and the repository checkouts Forge runs in. They are the
same for every issue. **Run requirements** (§FS-forge-run-requirements) are what
one work cycle must resolve for itself — the strategy it will run, the issue it
may claim, the form of that issue, and the run context a driver is handed. Each
is a gate, and they run in that order (§AR-forge-workflow-pipeline).

## FS-forge-host-requirements: Host requirements

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

## FS-forge-run-requirements: Run requirements


A satisfied host says nothing about whether a given cycle may do work. Before a
run reaches a workflow driver, four things must be resolved and verified, each
before the side effect it protects, and each by deterministic code rather than
by an agent (§root/PRCPL-prefer-algorithmic, §root/PRCPL-verify-inputs). 
A run requirement that cannot be satisfied must fail where it is checked, naming what was missing or ambiguous,
and must leave GitHub in the state it found: a rejection after a claim releases
the claim and returns the issue to `Todo` rather than failing the issue.

### 1. Strategy and model

Every enabled queue must resolve its configured strategy name against the
registry before scanning begins, and a strategy bundle must be rejected on load
unless it names a model and carries the prompts and parameters its workflow
engine declares as required (§STRAT-forge-predefined-strategy-contract). An
unresolvable strategy or model is a worker configuration error: the worker must
exit before it scans a queue, not fail one issue at a time.

### 2. Issue claimability

Claiming is exclusive, and the decision must be made against live GitHub state
rather than the scan results that led to it (§ORCH-forge-orchestration-spec).
The issue payload is re-read at claim time and must still be open, still carry
the queue label, be unassigned or assigned only to the authenticated user, carry
no open blockers, and sit in project status `Todo`. A `resumable` issue
additionally requires a valid continuation marker on a preserved branch
(§FS-forge-run-continuation), and a `chunked-dynamic-access` issue requires its
exhaust report (§WF-dynamic-access-exhaust-report). Only once every condition
holds may the issue be assigned and moved to `In Progress`.

### 3. Issue form

The queue label decides the workflow, so the issue must be unambiguous before a
driver starts (§AR-forge-driver-queues). The title must resolve to Maven
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
eligibility remains where it is (§AR-forge-driver-queues).

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

### 4. Run context

A driver must be handed a complete run context and must not resolve, clone, or
re-derive any part of it: the resolved coordinates, validated strategy,
isolated reachability worktree, scratch metrics repository, setup-evidence
directory, issue context, one GraalVM environment pinned for the whole run, and
the continuation marker (§FS-forge-run-continuation). The driver owns normal
and neural setup inside that context, including the persisted library
preparation result; it does not own queue policy or repository resolution. The
gap between this requirement and the current implementation is
§ROADMAP-forge-dispatcher-owned-run-preconditions.

## FS-forge-outputs: Run outputs

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

### FS-forge-run-output-legibility: Legible run output

The run's own output is an output of the run. Whoever is running generation
watches it live, so it must answer two questions at a glance: which concrete
step the run is in right now, and — when the run stops — what exactly failed.
Clarity is the requirement, not volume: a wall of text that has to be read
backwards to locate the current step fails this section as surely as silence
does. This is the live counterpart of the durable record required by
§FS-durable-generation-logs, and it keeps the loop short as called for by
§GOAL-shorten-issue-to-shipped-metadata.

1. **Every step announces itself once.** On entering a pipeline step, the run
   prints one line naming the phase and the step (`setup/neural_setup`,
   `explore/native_trace_gate`, `finalization/local_ci_check`, …) and the
   operand it is working on. The reader must never have to infer the current
   step from incidental output such as a Gradle banner or an agent's prose.
2. **Every failure names its location and its cause.** A failing run states the
   phase, the step, the operand, and the concrete reason it stopped, in that
   order, before any surrounding detail — the same pair required everywhere a
   failure surfaces by §ROADMAP-forge-failure-locates-phase-and-step. A failure
   reported only as a status, a stack trace, or a generic message does not
   satisfy this requirement.
3. **The inside of a step stays quiet.** Work within a step is reported by
   outcome, not by narration: one line per completed unit (a generated class, a
   passed gate, a trace cycle) and nothing per intermediate operation. Repeated
   or retried work says that it is a retry and which attempt it is, so a
   stalled loop is visible as a loop.
4. **Detail lives in the logs, not in the terminal.** Full agent conversations,
   Gradle output, and native-image output are written to the durable logs; the
   run output prints the path to the relevant log instead of reproducing it, so
   a maintainer can escalate from the summary to the evidence in one step
   (§FS-durable-generation-logs).

## FS-forge-run-metrics: Per-run metrics record
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

## FS-durable-generation-logs: Durable generation and session logs

Every Forge session and generation step must be logged and saved to a stable
path because durable logs are the primary debugging surface for generated work.
This includes agent prompts and responses, session or thread identifiers,
persistent-instruction state, Gradle command output, metadata-fix output,
native tracing output, post-generation intervention output, and local
verification output.

Logs must be scoped by task and coordinate where possible, and the workflow
must print or record the log path so a maintainer can inspect the exact
conversation or command that produced a generated artifact; the live run output
that points at those paths is specified by §FS-forge-run-output-legibility.
A workflow must not rely on transient terminal output as the only record of a
generation step.
If a run fails or times out, the saved logs are part of the diagnostic artifact
set that allows maintainers or a later Forge run to continue from evidence,
keeping the loop short as called for by §GOAL-shorten-issue-to-shipped-metadata.

## FS-forge-run-status: Run status semantics

Every workflow records one of these statuses:

| Status | Meaning |
| --- | --- |
| `RUN_STATUS_SUCCESS` | All generation gates and the local CI-equivalent verification passed; metadata and tests committed (see §FS-local-ci-equivalent-verification). |
| `SUCCESS_WITH_INTERVENTION_STATUS` | Tests succeeded after the built-in post-generation recovery modified the working tree (a Codex metadata fix, then Pi removing failing tests as a last resort), and the local CI-equivalent verification (§FS-local-ci-equivalent-verification) passed. The intervention's record is included in the run-metrics and PR description. PR-eligible; distinct from the `human-intervention` label unless §FS-human-intervention-policy separately requires that label. |
| `RUN_STATUS_CHUNK_READY` | A chunked dynamic-access run reached a reviewable class boundary and §FS-local-ci-equivalent-verification passed for the current part. The current part is PR-eligible, and the issue must not be resumed until the part PR has merged. |
| `RUN_STATUS_FAILURE` | The workflow could not converge or a quality gate failed; the feature branch is reset to its workflow recovery checkpoint and no PR is opened. Iterative dynamic-access exploration advances that checkpoint after each committed class (§WF-dynamic-access-fallback-and-failure); other workflows retain their specified checkpoint behavior. |

The exit code is `0` for PR-eligible statuses and `1` for failure.

## FS-forge-chunked-dynamic-access: Chunked dynamic-access semantics

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
queue (§FS-forge-scope) is entered by a deterministic workflow driver
(§AR-forge-drivers) that prepares one run and delegates to the workflow
engine governed by a workflow spec. The driver scripts and the workflow specs
they run:

| Queue | Driver script | Workflow spec |
| --- | --- | --- |
| `library-new-request` | `add_new_library_support.py` | new library support (§AR-forge-driver-queues.1), which runs dynamic-access generation plus native metadata tracing and verification |
| `library-update-request` | `improve_library_coverage.py`, or the missing-version router | dynamic-access coverage improvement (§AR-forge-driver-queues.2), Java repair (§WF-java-fail-fix-workflow), or native-image run repair (§AR-forge-driver-queues.4) depending on the compatibility probe |
| `fails-javac-compile` | `fix_javac_fail.py` | Java failure repair (§WF-java-fail-fix-workflow) |
| `fails-java-run` | `fix_java_run_fail.py` | Java failure repair (§WF-java-fail-fix-workflow) |
| `fails-native-image-run` | `fix_ni_run.py` | native-image run repair (§AR-forge-driver-queues.4) |
| code coverage improvement (planned) | — | code coverage improvement (§CC-code-coverage-improvement) |

Each engine is bound to a named configuration bundle defined by
§STRAT-forge-predefined-strategy-contract. The `basic_iterative` engine is not a
separate queue: it is the most basic workflow and the fallback the dynamic-access
workflow delegates to when a library turns out to have no dynamic access
(§WF-basic-iterative).

Forge benchmarking is a top-level benchmark contract because it compares
generation strategies across multiple `library-new-request` targets
(§BENCH-forge-generation-benchmarking) and records cost (in service of
§GOAL-minimize-generation-cost), token, iteration, LOC, coverage (in service
of §GOAL-maximize-library-coverage), dynamic-access, and metadata metrics.

The implementation roadmap orders the first known Forge spec gaps to close; see
§ROADMAP-forge-implementation.
