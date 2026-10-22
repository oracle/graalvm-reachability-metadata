# FS-forge-functional-spec: Forge functional specification

This spec realizes the Forge direction set out in §GOAL-forge-direction, in
service of §GOAL-maximize-library-coverage and
§GOAL-shorten-issue-to-shipped-metadata. Repository-specific terms used by the
spec are defined in §FS-forge-glossary.

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
`fixes-native-image-run-fail`) so the launcher's CI pass recognizes the pull
request as Forge's (§FS-published-pr-ci-failures.1).

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
| **Forge publication descriptor** | Versioned, schema-validated JSON committed at `stats/<group>/<artifact>/<version>/forge-publication.json`. It is the durable, branch-controlled data handoff from locally verified Forge generation to the trusted Actions publisher (§AR-publication-descriptor). Benchmark-result publications carry no descriptor: their branch diff is the handoff (§FS-code-coverage-benchmarking.3). |
| **Forge publication ID** | A run-unique identity derived before the publication commit and recorded in the descriptor. Chunked runs also record it and the unique head branch in their exhaust report so a later run can resolve the preceding PR without committing a GitHub-assigned PR number after publication (§AR-chunked-linking). |
| **Forge Actions publisher** | Default-branch code triggered through a successful unprivileged Branch Ready run. It treats the feature branch as data, revalidates the exact head SHA and descriptor, renders the PR, and performs publication-related GitHub mutations with a short-lived GitHub App token (§AR-actions-publication). |
| **Coordinate** | Maven coordinate of the target library, formatted `group:artifact:version`. |
| **Agent** | LLM-driven code editor registered through [ai_workflows/agents/](../../ai_workflows/agents/). Each implements `send_prompt`, `run_test_command`, and `clear_context` behind one editing contract (§FS-forge-agent-runtime-selection); the concrete API and its adapters are documented by §AR-agent-api. |
| **Forge run** | One Rhei workspace of the `forge-metadata` template for one claimed issue. Programs do the deterministic work and route on exit codes; agent states do the judgment (§AR-forge-workflow-pipeline). |
| **Program state** | A state that runs `forge_run.py <subcommand>`. It owns setup, build runs, routing decisions, and publication; no agent decides that plumbing (§AR-forge-workflow-pipeline.4). |
| **Agent state** | A state that gives the setup or worker agent one instruction and a handoff: `setup`, `cover`, or `fix` (§AR-forge-workflow-pipeline.8). |
| **Fix request** | The handoff a failed check writes for the `fix` state: the commands, their output, the files it may edit, one brief per error, and the code to return to. The fix runs once, best effort; the sending program reruns the check and lets that result decide (§AR-forge-workflow-prompts). |
| **Human intervention** | A maintainer follow-up signal applied through the `human-intervention` issue or PR label when Forge has evidence that generated work, repository automation, or library execution semantics require human judgment. It is distinct from post-generation intervention, which is an automated recovery step. The policy is defined in §FS-human-intervention-policy. |
| **Dynamic access** | Reflection, JNI, resource access, serialization, or proxy use that GraalVM `native-image` cannot determine statically. |
| **Dynamic-access report** | JSON written by Gradle task `generateDynamicAccessCoverageReport` to `tests/src/<group>/<artifact>/<version>/build/reports/dynamic-access/dynamic-access-coverage.json`, listing classes and per-class call sites that require dynamic-access metadata, marked covered/uncovered. |
| **Dynamic-access exhaust report** | Durable coordinate-scoped JSON state for chunked dynamic-access work. It records the coordinate, issue number, class threshold, completed/skipped/exhausted/failed classes, and latest publication ID/branch. It is stored with the target test suite so orchestration can find it from the coordinate. It does not predefine chunks; each resume regenerates the report and filters processed classes. Legacy PR-number/commit fields remain readable during migration. Specified by §AR-dynamic-access-exhaust-report. |
| **Chunked dynamic-access workflow** | Dynamic-access generation mode for oversized `library-new-request` issues and `library-update-request` issues routed to dynamic-access coverage improvement. `analyze` defers exploration past the configured class boundary, the run publishes the chunk, and a later run resumes once the chunk PR merges (§AR-forge-workflow-pipeline.7). PR linking rules are in §AR-chunked-dynamic-access-pr-linking. |
| **Source context** | Read-only files supplied to the agent. Types: `main` (library source), `test` (upstream tests), `documentation` (Javadoc). The setup tail downloads `main` for every run (§AR-forge-workflow-pipeline.4.1). |
| **Library update target** | The metadata and test directories `prepare` selects for a non-new coordinate (§FS-library-update-tested-version-split). Resolution records the requested coordinate, match type (`tested-version`, `metadata-version`, `default-for`, or `new-version`), matched index entry, resolved metadata version, resolved test version, and edit directories. |

## FS-forge-requirements: Requirement gates

Forge validates two kinds of requirement, split by the question each answers.
**Host requirements** (§FS-forge-host-requirements) are what the machine must
provide before any work is claimed — tools, credentials, Java lanes, filesystem,
network, environment, and the repository checkouts Forge runs in. They are the
same for every issue. **Run requirements** (§FS-forge-run-requirements) are what
one work cycle must resolve for itself — the agent targets it will run, the
issue it may claim, the form of that issue, and the run context `prepare` builds. Each
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
  CLI, the agent backends the enabled roles select, the Docker CLI, the pinned
  `grype` release, and the selected
  repository's Gradle wrapper.
- **A usable GitHub account**: the CLI authenticated, `Contents`, `Issues`, and
  `Pull requests` write on the repository, and `Projects` write on the tracked
  project board — read access alone is not enough, because Forge assigns and
  labels issues, comments, pushes generated branches, submits reviews, and
  merges eligible pull requests. The local process never holds the Forge
  publisher App credentials: it neither opens pull requests nor creates
  publication follow-up issues.
- **Agents that are usable, not merely installed**: each selected backend
  authenticated against its provider with unattended tool approval and a
  writable state directory. Validation covers only the executables, models, and
  state roots the enabled roles actually select
  (§FS-forge-agent-runtime-selection) — supporting a backend is not a reason to
  require it.
- **Every Java lane**, each pointing at a GraalVM distribution that provides
  Native Image, allows its `bin/java` to load the `native-image-agent`, and
  contains the reachability-metadata schema the checked-out repository requires:
  `GRAALVM_HOME` at the latest published GraalVM GA
  release, `GRAALVM_HOME_LATEST_EA` at the newest published Oracle GraalVM EA
  build, and `GRAALVM_HOME_25_0` at the repository-pinned 25.0.x release in
  `graalvm-versions.json`. The agent probe writes to a disposable output
  directory, so it verifies the JVM capability metadata generation depends on
  without leaving generated configuration behind. The operator updates the
  pinned value deliberately;
  GA and EA freshness are resolved from their authoritative release metadata on
  every run. An Oracle EA release label can advance without the GraalVM runtime
  revision its binaries report changing, so the gate accepts a published
  label-to-runtime revision exception while still requiring the matching Graal
  and JDK version tuple. `GRAALVM_HOME` and `JAVA_HOME` are aligned to one
  distribution, and `GRADLE_JAVA_HOME` and Gradle's `org.gradle.java.home`
  system property are pinned to that same distribution, so inherited host
  `GRADLE_OPTS` cannot silently hand Gradle test workers an agent-less JVM. A
  review-only host needs a working JDK on `JAVA_HOME` instead of the lanes.
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
  so a proxied host is not reported as offline. Every configured artifact
  repository is checked at its exact base URL with an HTTP `HEAD` request and
  must return `200`, so Forge proves the same DNS, proxy, TLS, and HTTP path the
  issue-form artifact lookup will use before it claims an issue.
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
run does not need the issue-work Java lanes or Docker; an issue run
needs everything its enabled queues select; a fixture-testing run needs no live
GitHub access, because it mutates no GitHub state. Only the release-version
comparison for the Java lanes is relaxable, and relaxing it never waives Native
Image, the `native-image-agent`, or the reachability-metadata schema.

Code-coverage work is a separate host-requirement mode because its operator
entry point does not pass through `forge_metadata.py`. It needs the build
capabilities used by issue work, but only one GraalVM distribution: use
`GRAALVM_HOME`, taking the value of `JAVA_HOME` when it is unset, and require
JDK 25 or newer with Native Image, native-image-agent, and the
reachability-metadata schema. It does not require the three release-comparison
lanes or `grype`.

The gate must always print an operator-facing report. Each entry names the
capability, whether the invoked mode requires it, the exact executable,
environment variable, path, host, or permission checked, and — on failure —
exactly one concrete `Fix:` instruction, with current and required values shown
explicitly and command output reduced to the relevant fact.

The gate runs at the start of every work-starting `forge_metadata.py`
invocation, not only from the worker loop, and because the worker re-execs
itself between cycles, every new worker process revalidates the host before
doing more work. A standalone work-starting entry point must invoke the same
gate before its first queue query; the code-coverage launcher does so in its
coverage mode before selecting an issue or creating a Rhei workspace. Any
failed required check stops the run with a non-zero exit before the first work
cycle. Capabilities the mode does not need are reported as not required, a
relaxed version mismatch does not fail startup, and commands that start no work
— stop, resume, help, cache maintenance — do not run the gate at all.

## FS-forge-agent-runtime-selection: Two agent inputs

§GOAL-forge-direction §root/PRCPL-prefer-algorithmic

A Forge run has exactly two agent inputs, and nothing else about a run is a
choice: strategies are fixed per issue label (§AR-forge-workflow-pipeline.7).

- **`setup_agent`** prepares the library before any test is written: it fills
  the coordinate's missing artifact URLs, declares the companion libraries and
  Docker images the tests will need, and touches nothing else. It is the only
  agent that needs the web — finding a test-sources path or the documentation of
  an unfamiliar library takes search and reading — so it must be a Claude Code
  target, whose WebSearch and WebFetch tools are built in. The launcher's
  diagnosis of failing CI on a published pull request uses the same target for
  the same reason (§FS-published-pr-ci-failures.3). The default is
  `claude-code[medium]:sonnet`.
- **`worker_agent`** writes the tests that cover dynamic-access call sites and
  fixes whatever check a program reports failing. Both jobs only read the
  repository, edit files, and run Gradle, so one input serves both. The default
  is `pi[high]:openai-codex/gpt-5.6-luna`.

A target is `<agent>[<mode>]:<model>`, where the agent is `pi` or `claude-code`
and the mode selects the reasoning level from the template's agent profiles;
OpenAI models run through `pi` with the `openai-codex` provider. Codex and
OpenCode are not supported. The launcher validates both targets before it
claims an issue, and the host gate checks that each selected agent is installed
and authenticated (§FS-forge-host-requirements). Each target reaches its agent
states unchanged, so the agent and model of every edit are the ones the
operator selected and the run's metrics record them.

## FS-forge-run-requirements: Run requirements


A satisfied host says nothing about whether a given cycle may do work. Before a
run does any work, four things must be resolved and verified, each
before the side effect it protects, and each by deterministic code rather than
by an agent (§root/PRCPL-prefer-algorithmic, §root/PRCPL-verify-inputs). 
A run requirement that cannot be satisfied must fail where it is checked, naming what was missing or ambiguous,
and must leave GitHub in the state it found: a rejection after a claim releases
the claim and returns the issue to `Todo` rather than failing the issue. The
issue form is the one requirement that then ends the issue rather than leaving
it queued, because its defect is in the issue itself and no later cycle can
repair it (see 3 below).

### 1. Agent targets

Both agent targets must be valid before the launcher fetches a queue: each is
a `pi` or `claude-code` target, and `setup_agent` is a `claude-code` target
(§FS-forge-agent-runtime-selection). An invalid target is a worker configuration
error: the launcher exits before it claims an issue, not one issue at a time.

### 2. Issue claimability

Claiming is exclusive, and the decision must be made against live GitHub state
rather than the fetch that led to it (§AR-forge-workflow-pipeline.1). The issue
payload is re-read at claim time and must still be open, still carry the queue
label, be unassigned or assigned only to the authenticated user, carry no open
blockers, and sit in project status `Todo`. Once those live conditions hold, the
issue is assigned and moved to `In Progress`.

The branch supplying the running Forge implementation does not select the
repository state an issue targets. The issue-form gate decides against freshly
fetched `origin/master`, and `prepare` fetches again, pins that commit as the
run's base, and creates the isolated worktree from it; a base that cannot be
resolved fails the run before anything is changed. Every repository-dependent
precondition runs inside the run against that worktree: a
`chunked-dynamic-access` issue must resolve its exhaust report and find the
preceding chunk merged (§AR-dynamic-access-exhaust-report). A failed
precondition ends the run, the launcher releases the claim back to `Todo`, and
its classification decides what happens to the issue
(§FS-human-intervention-policy). An infrastructure failure takes no issue
action, so a later cycle retries it. A bad input would fail identically on every
retry: Forge posts a comment naming the reason and labels the issue
`human-intervention`, which takes it out of the queue until a maintainer
resolves it.

### 3. Issue form

The queue label decides the route, so the issue must be unambiguous before a
run starts (§AR-forge-control-plane). Every rule below is decided from the
issue payload and the freshly fetched base's `index.json` alone, so all of them
are checked by one deterministic gate after the exclusive claim and before the
run is instantiated (§root/PRCPL-prefer-algorithmic,
§root/PRCPL-verify-inputs). The claim makes the terminal decision exclusive,
so two workers cannot both reject the same issue.

- The issue carries **exactly one workflow label**. An issue carrying two queue
  labels is processed once per queue that matches it, so the same issue can be
  claimed, worked, and published more than once, each time by a different
  route working from different assumptions about what the issue asks for.
- The **title resolves to Maven coordinates** `group:artifact:version`.
- A `fails-*` issue **resolves a current `latest` metadata version** for the
  coordinate, because a repair workflow is defined as a move from the currently
  supported version to the requested one.
- A `fails-*` issue **requests a version strictly above that `latest`**.
- The **coordinate is fetchable**, not merely parseable.

The rules are decided in that order, and the gate stops at the first failure.
Everything decidable from the issue payload and the checked-out repository is
decided before the one rule that costs a request to a remote repository, so a
malformed issue is rejected without a network round trip.

A `library-update-request` has one route, so this gate decides nothing about
how it runs: `prepare` adds the version beside its nearest supported one, and
`repair` finds out whether it already passes (§AR-forge-workflow-pipeline.3).

**Coordinates resolve when the artifact is fetchable.** A title that parses as
`group:artifact:version` has satisfied a regular expression, not the
requirement. The run needs an artifact the build can actually resolve, so the
gate confirms the coordinate is published in one of the repositories the
harness resolves against — Maven Central, then the Confluent fallback
(§root/AR-build-infrastructure.1). A typo in a group, an artifact that was
never published, or a version that does not exist upstream is decidable from
the repository's own layout, so it is decided here rather than surfacing later
as a Gradle resolution error after Forge has created a worktree and started a
run (§root/PRCPL-verify-inputs). Only the artifact's existence is checked —
its content is the run's concern (§AR-forge-workflow-pipeline.4).

The answer is three-valued: published, absent from every configured
repository, or undecided because a repository could not be reached. Only
*absent* rejects an issue. An unreachable repository is an external condition
outside Forge's boundary, so the gate releases the claim without taking any
terminal issue action and the issue waits in `Todo` for a later cycle
(§FS-human-intervention-policy).

**A rejection is reported on the issue, and the issue is closed.** Every rule
above is decided separately, so when one fails the gate knows exactly which
rule failed and what value failed it. That is what the reporter needs and what
a worker log does not give them. The failed rule selects one predefined
comment, which names the rule, quotes the offending value, and states what the
issue must carry instead. While the exclusive claim is still held, the rejection
posts the comment and closes the issue; only after the issue is closed does it
clear the Forge assignee. It must not release the issue back to `Todo` before
closing, because doing so would let another worker claim the same issue inside
the rejection sequence. A form defect is not repaired by waiting — nothing
about the issue changes until a person changes it — so leaving it open only
guarantees it is rescanned and re-rejected forever. Closing takes it out of
every queue and puts the next move with the reporter, who reopens or files a
corrected issue.

**The comment is posted once.** Closing is the primary guard: a closed issue
leaves every queue, so an unchanged issue is never rescanned and never
re-commented. What remains is the reopened issue — reopening without editing
puts the same defect back in the queue — so the comment carries a marker keyed
on the failed rule and the offending value, and a rejection whose marker is
already on the issue closes it again without posting a second comment. An
edited title changes the value, so it changes the marker and is judged afresh.

The live worker output must make the terminal action equally explicit: name the
failed rule and offending value, say whether the matching comment was posted or
skipped as a duplicate, and say that the issue is being closed
(§FS-forge-run-output-legibility).

A form rejection is not a workflow failure: it is an input defect outside
Forge's generation boundary, so it carries no `human-intervention` label and
preserves no branch (§FS-human-intervention-policy).

### 4. Run context

`prepare` builds the run context once, and no later state resolves, clones, or
re-derives any part of it: the freshly fetched and pinned `origin/master`
commit, resolved coordinates, the route, the isolated reachability worktree
created from that commit, the issue context, and one GraalVM environment for the
whole run (§AR-forge-workflow-pipeline.4). The pinned commit keeps
repository-dependent checks and generated work on one base even if `master`
advances during the run. The worktree is also the boundary of the run's Gradle
daemons. Every worktree builds in its own Gradle user home, so the daemons it
starts serve that worktree alone: a daemon can neither pick up the build logic
of another worktree nor be stopped by another worktree's run, and removing the
worktree stops its daemons and discards its home with it. A daemon that keeps
one worktree's build logic loaded can therefore fail only that worktree's
Gradle tasks, and the finalization gates recover from such a stale daemon on
their own (§FS-local-ci-equivalent-verification.1). What stays shared is the
downloaded dependencies, so plugin and dependency resolution is still paid once
per checkout rather than once per issue: the checkout keeps one long-lived
Gradle user home, and as a Forge process creates its first worktree it warms
that home by configuring the build of that worktree, never of the monitored
checkout, and publishes a copy of the home's dependency cache that worktrees
read through Gradle's read-only dependency cache, downloading only what the
copy lacks. The copy is never written while worktrees use it. Warming and
publishing are best effort: when either fails, the failure is reported, the run
continues, and worktrees merely download more. A worktree home whose worktree
no longer exists is an orphan that a later Forge process stops and removes
before it publishes. The `setup` state and the setup tail own library setup
inside that context (§AR-forge-workflow-pipeline.4.1); neither owns queue
policy or repository resolution.

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
  where `<suffix>` names the route and encodes the coordinate (e.g.
  `add-lib-support-<group>-<artifact>-<version>` for new-library support,
  `library-update-request-…`, `fixes-javac-fail-…`, `fixes-java-run-fail-…`,
  `fixes-native-image-run-fail-…`). Publication branches are
  unique per publication ID and are pushed directly to
  `oracle/graalvm-reachability-metadata` so the repository's `push` workflow can
  observe them.
- **Pull request** created asynchronously by the trusted Forge Actions
  publisher after Branch Ready accepts the exact pushed SHA.

### FS-forge-run-output-legibility: Legible run output

The run's own output is one of the run outputs defined by §FS-forge-outputs.
Whoever is running generation watches it live, so it must answer two questions
at a glance: which concrete step the run is in right now, and — when the run
stops — what exactly failed. Clarity is the requirement, not volume: a wall of
text that has to be read backwards to locate the current step fails this section
as surely as silence does. This is the live counterpart of the durable record
required by §FS-durable-generation-logs, and it keeps the loop short as called
for by §GOAL-shorten-issue-to-shipped-metadata.

#### 1. Every step locates its state

On entering a pipeline step, the normal output prints one concise line naming
the phase, the work and its operand, plus the step's derived position as
`(n/total)` (`setup/neural_setup`, `explore/native_trace_gate`,
`finalization/local_ci_check`, …). A completed step or unit adds one outcome
line under the same position instead of narrating its intermediate work. The
reader must never have to infer the current step from incidental output such as
a Gradle banner or an agent's prose.

#### 2. Every failure names its location and its cause

A failing run states the phase, the step, the operand, and the concrete reason
it stopped, in that order, before any surrounding detail — the same pair
required everywhere a failure surfaces by
§FS-forge-run-location-reporting.3. A failure reported only as a
status, a stack trace, or a generic message does not satisfy this requirement.

#### 3. The inside of a step stays quiet

Work within a step is reported by outcome, not by narration: one line per
completed unit (a generated class, a passed gate, a trace cycle) and nothing per
intermediate operation. Repeated or retried work says that it is a retry and
which attempt it is, so a stalled loop is visible as a loop.

#### 4. Detail lives in the logs, not in the terminal

Full agent conversations, Gradle output, and native-image output are written to
the durable logs; the run output prints the path to the relevant log instead of
reproducing it, so a maintainer can escalate from the summary to the evidence in
one step (§FS-durable-generation-logs).

#### 5. Verbose mode restores narration

`--verbose` adds the intermediate operations hidden by normal output, including
the registered method-style step announcement and deterministic setup details.
It never replaces the concise progress and outcome lines. A failed gate prints
the detail needed to diagnose that failure even when verbose mode is off.

Inside a `forge-metadata` run each state writes its own log, and the dashboard
names the state; a program prints one line per check it runs and its outcome,
and command output, branch commands, source downloads, and checkpoints stay in
the state's log (§AR-forge-workflow-pipeline.3). A failed command remains
visible with its cause and durable log path.

For `fix` and `explore`, normal output names the diagram step and only the unit
that advances it. Test generation identifies either the selected class and its
`current/total` class position, a bulk iteration, or an unguided generation
attempt. Beneath that state, tests report their bounded attempt, terminal task,
and whether `nativeTest` was reached; an agent retry is one indented `feedback
fix` state. The native-trace gate reports start, terminal status, and a nested
`agent_fix` state only when deterministic tracing cannot converge. Agent session
announcements, static start/completion lines, Gradle commands, report refreshes,
trace directories, metadata paths, cycle internals, and failure-log excerpts
are verbose narration. The in-place agent heartbeat remains the normal live
timer. Terminal failures still name their cause and durable log.

For `finalization`, normal output shows the terminal native-trace gate, then
the three native-test lanes with their `current/total` lane position, toolchain,
and Native Image mode. The remaining deterministic production work is one
`final repository checks` state per coordinate, followed by the finalization
outcome. An agent entered by a failed native lane, metadata check, or Checkstyle
check is reported as the separate `agent_fix` step with its attempt and target.
Metadata splitting, individual validation and style tasks, statistics and
metrics generation, schema validation, Gradle commands, paths, and timings are
verbose narration. A failed command or agent remains visible with its cause and
durable log path.

## FS-forge-run-metrics: Per-run metrics record
§GOAL-minimize-generation-cost

Every run must persist a per-library metrics record to
`stats/<group>/<artifact>/<version>/execution-metrics.json` so cost, token,
coverage, and status evidence stays attached to the library version it describes
(§GOAL-maximize-library-coverage). `publish` builds the record from the run's
own token accounting just before it writes the publication descriptor
(§AR-forge-workflow-pipeline.4).
Run metrics may identify the generated metadata artifact, but must not record a
single test-file artifact: the version-controlled test tree and pull-request
diff are the authoritative set of test files for the run.
A run Rhei resumes keeps its workspace, so its accounting already holds the
usage of the attempts before the resume.

## FS-durable-generation-logs: Durable generation and session logs

Every Forge session and generation step must be logged and saved to a stable
path because durable logs are the primary debugging surface for generated work.
This includes agent prompts and responses, session or thread identifiers,
persistent-instruction state, Gradle command output, metadata-fix output,
native check output, fix requests and fix reports, and local verification
output.

Logs must be scoped by task and coordinate where possible, and the workflow
must print or record the log path so a maintainer can inspect the exact
conversation or command that produced a generated artifact; the live run output
that points at those paths is specified by §FS-forge-run-output-legibility.
A workflow must not rely on transient terminal output as the only record of a
generation step.
If a run fails or times out, the saved logs are part of the diagnostic artifact
set that allows maintainers or a later Forge run to continue from evidence,
keeping the loop short as called for by §GOAL-shorten-issue-to-shipped-metadata.

## FS-forge-run-location-reporting: Run location in progress and failure output

A Forge run must always be able to say **where** it is: the phase it is in and
the step inside it. That location is one vocabulary used twice — to narrate
progress while the run works, and to report the location of a failure — so a
reader of a live run and a reader of a failed run name the same thing the same
way, serving §GOAL-shorten-issue-to-shipped-metadata. Inside a `forge-metadata`
run the location is the Rhei state, which the dashboard and the state's log
already name (§AR-forge-workflow-pipeline.3); the vocabulary below is the
launcher's and the code coverage workflow's.

### 1. Vocabulary

A **phase** is one banded segment of the run: `claim`, `setup`, `fix`,
`explore`, `finalization`, or `publication`. `claim` is the launcher-side
segment that runs before a run exists, and the phases are declared once, in the
run-location table (§AR-forge-run-location).

A **step** is the method that is executing, written with its parentheses —
for example `check_host_requirements()`, `claim_issue()`, `generate_tests()`,
`native_trace_gate()`, `agent_fix()`, `local_ci_check()`, `publish_branch()`. A step
is registered under the phase that runs it, and a phase's registered steps are
ordered by the order the run enters them, so a step has a position `<n>` within
a total `<total>`. A step name may belong to more than one phase when more than
one phase runs it, but the registry is the only place the binding is written: a
step the run enters must be registered, and `<total>` is derived from the
registry rather than counted by hand.

Only steps the pipeline actually enters are registered. A pipeline method that
is still folded into a larger one is registered when it becomes a step of its
own, so `<n>/<total>` never counts a step that never runs.

A step may carry an **operand**: the one value the step was working on when it
ran — the class being generated, the gate command that returned non-zero, the
coordinate being published. A location renders as `<phase>/<step>` without an
operand and `<phase>/<step>[<operand>]` with one.

### 2. Progress output

Entering a phase prints a bounded, visually distinct banner naming the phase and
the run it belongs to, so a phase transition is findable by eye in a long run
log even when runs are interleaved (§AR-forge-run-location). The
banner marks a transition: re-entering the phase the run is already in prints
nothing.

Entering a step prints exactly one line:

```
Running step <step> (<n>/<total>) of phase <phase>
```

with ` on <operand>` appended when the step has an operand. The words "Running
step" and "of phase" are fixed: this line is the anchor a reader and a log
search look for.

### 3. Failure output

A terminal failure prints one line before its error detail:

```
run failed in <phase>/<step>
```

The same pair must appear, unchanged, in every place the failure surfaces: the
worker's terminal output and run log, the run status the workflow returns, and
the human-intervention comment, which leads with it
(§FS-human-intervention-policy).

The location is reported once per run — the failure that ends a run is one
failure, and the first boundary to report it owns the line — and the innermost
step that failed is the one reported, however many intermediate handlers the
failure passes through. A failure that returns a status code instead of raising
records its location at the point it gives up, so both routes out reach the
lifecycle boundary with one location.

A user interrupt is not a run failure: it carries no location, records none, and
prints none.

No failure path may report a phase without a step. A failure raised outside
every step boundary is a defect in Forge, not a formatting gap: it is reported
with the step `<unlocated-step>` and an explicit defect notice on stderr, so it
is visible rather than silently tolerated.

### 4. Failure output stays short

Failure output is the location, the error, and the preserved-work link. The
human-intervention handoff itself is unchanged
(§FS-human-intervention-policy) — the work is still preserved on a branch, the
comment is still posted, the label is still applied — but its narration is
debugging detail, not failure output: branch switches, staging, commits, pushes,
and claim-revert bookkeeping are logged at debug level and are off unless debug
logging is enabled. Errors, the failure location, and the preserved branch URL
are never suppressed.

## FS-forge-run-status: Run status semantics

Every `forge-metadata` run ends in one final state, and the launcher acts on it
(§AR-forge-workflow-pipeline.1):

| Final state | Meaning |
| --- | --- |
| `completed` | Every check and the local CI-equivalent verification passed (§FS-local-ci-equivalent-verification) and the publication branch is pushed. A chunk that is not the last is published the same way, with `RUN_STATUS_CHUNK_READY` in its metrics; the issue must not be resumed until that chunk's PR has merged. A skip record is published the same way (§FS-unsupportable-version-diagnosis.3). |
| `nothing-to-publish` | The coordinate's paths are unchanged; no branch is pushed. |
| `human-intervention` | A check still failed after its fix, or the fix reported that nothing within the rules helps; no branch is pushed (§FS-human-intervention-policy). |
| `failed` | A bad input or an infrastructure failure; no branch is pushed. |

A run whose checks pass only after a fix request is still `completed`: the fix
request and report stay in the workspace, and the PR body carries the run's
metrics like any other.

## FS-unsupportable-version-diagnosis: Unsupportable-version verdict in setup

For `fails-javac-compile`, `fails-java-run`, and `fails-native-image-run`
issues, the `setup` state answers one more question before any repair starts:
does covering the library's dynamic-access calls require behavior Native Image
does not support (§root/FS-test-contract.4.5)? The inputs — the issue's failure
evidence and the library's sources for this and newer versions — all exist
before any test is written, so an unsupportable verdict costs part of one setup
turn and saves the whole repair. Without it, the disposition of
§root/FS-contribution-contract.5.4 is reachable only through a reviewer: a run
that fails honestly ends in `human-intervention` and publishes nothing. Issues
without failure evidence are never asked: a `library-new-request` has no index
entry to record a skip into, and a `library-update-request` carries no failure
to diagnose. `run.md` names the bar only for the three `fails-*` labels
(§AR-forge-workflow-prompts.2).

### 1. The bar

The verdict applies the evidence bar of §root/FS-contribution-contract.5.4 in
five steps, in order, and an unsupportable verdict requires all of them:

1. **Failure.** State the failing operation from the issue's failure evidence
   alone.
2. **Trace.** Follow the failing symbol into the library's own source and quote
   the code performing the operation.
3. **Classify.** Show the operation is one Native Image does not support:
   runtime bytecode generation or class definition, runtime lambda definition,
   Java agent self-attach, class redefinition or instrumentation, native-image
   substitution paths, or URL, plugin, or OSGi class loader paths that
   introduce classes not in the image. Anything reachability metadata can
   register is repairable.
4. **Sole path.** Prove every public API route the metadata would justify
   passes through this operation, with no configuration, property, or fallback
   branch around it. One viable fallback means repairable.
5. **Range.** List the newer released versions whose sources were verified to
   share the same mechanism, and only those; when no newer version can be
   verified, the range is the target version alone.

The verdict never rests on a red test, a library's own error type, or its
error message — only on the mechanism traced in the library's source. The
default is strict: any uncertainty at any step means repairable, because a
missed skip costs one repair while a wrong skip freezes versions that were
never attempted. An under-skipped range heals itself, since the next version's
run asks again.

### 2. The verdict is an edit

On an unsupportable verdict, `setup` adds `skipped-versions` entries to the
coordinate's entry in `index.json`, each with the `version` and a `reason`
written for a reader who has not seen the analysis, because reasons are
published verbatim in the index and the pull request. The entries include the
target version and cover only the range of step 5. `setup` then makes no
dependency or image edit and changes nothing else, and it writes no report. On
a repairable verdict it adds no entry.

### 3. The skip record is the run's outcome

The setup tail keeps `skipped-versions` additions on the three `fails-*` labels
and reverts them on every other label (§AR-forge-workflow-pipeline.4.1). When
the index gained an entry for the target version, `repair` turns the run into
the skip record before anything else. The coordinate's paths return to the
pinned base — the version `prepare` added, its tests, metadata, and stats are
dropped, as §root/FS-contribution-contract.5.4 requires of a skip record — the
entries are written onto the base index, `validateIndexFiles` and the
pre-publication gate (§FS-local-ci-equivalent-verification.2) accept it, and
the commit is checkpointed. `repair` then exits `21` and the run goes straight
to `publish`: no repair, exploration, native check, or finalization runs.

`publish` pushes the index-only tree with its descriptor and writes no
execution metrics for the skipped version, because the stats gate checks
execution metrics against tested versions. The trusted publisher renders the
skip record from the tree (§FS-forge-publication-readiness), so the record is
the run's successful outcome.

A verdict that cannot be applied — a rejected index, or a target version
already recorded — is reverted, and the run falls through to the normal repair,
so a redundant or malformed verdict costs nothing beyond the setup turn.

## FS-forge-chunked-dynamic-access: Chunked dynamic-access semantics

The class boundary keeps each chunk a reviewable pull request
(§FS-forge-issue-resolution-goal). It is the run input `defer_classes`, default
`15` (§AR-forge-workflow-pipeline.7). `analyze` measures the dynamic-access report
once, after `repair` has made the version work, counts the classes with
uncovered calls less those an earlier chunk processed, and makes the whole
decision from that count:

- A `fails-*` issue above the boundary does not explore. Before the verified
  push, `publish` creates or reuses a `library-update-request` for the fixed
  version, parks it until the repair merges, and records its number as a typed
  follow-up fact in the descriptor; the Actions publisher only references it.
  That issue then explores as an ordinary library update.
- A `library-new-request` or `library-update-request` issue above the boundary,
  or one that already carries the `chunked-dynamic-access` label or a
  coordinate-local exhaust report, is chunked. A chunked run spends at most the
  boundary's number of classes — completed and exhausted classes both count —
  and the next class ends the chunk.
- Every other issue explores without a boundary, and its PR is never a chunk PR.

A run is a chunk only once it publishes: `publish` records the chunk in the
exhaust report and applies the `chunked-dynamic-access` label when the chunk is
not the last. A run that ends before publishing leaves no label and no exhaust
report behind, so the next claim decides afresh. A chunk is the last when its
run left no target uncovered and unexhausted; a bulk pass only lowers the count,
so a library at or below the boundary never needs a later chunk.

Chunked mode is automatic once the issue carries the `chunked-dynamic-access`
label. The normal project status remains the run-state signal: `Todo` means
Forge may claim the next chunk, `In Progress` means a chunk is being generated
or reviewed, and the final PR's `Fixes: #<issue>` transition moves the issue to
`Done`. The exhaust report's location is derived from the coordinate and loaded
by `prepare`, as specified by §AR-dynamic-access-exhaust-report.

When a non-final chunk PR merges, Forge releases the linked issue for the next
chunk by moving it to `Todo`, clearing its assignees, and removing the
`human-intervention` and `resumable` labels when present. It retains the
`chunked-dynamic-access` label so the next claim stays in chunked mode.

Chunk PRs use `Refs: #<issue>` until the final chunk. Only the final chunk PR
may use `Fixes: #<issue>` and move the issue to `Done`. Non-final chunk PRs
must commit enough exhaust-report state for the next run to skip classes already
completed, skipped, exhausted, or failed in earlier chunks
(§AR-chunked-dynamic-access-pr-linking).

Before the single verified push, every chunk also records its publication ID
and unique publication branch in the exhaust report. The publisher repeats the
ID in a machine-readable PR-body trailer. A later chunk loads the merged report,
resolves the preceding PR by the exact head branch, verifies the matching
publication ID and merged state, and checks that the merge commit is an ancestor
of its new base. Forge must not create a second post-publication commit merely
to store a GitHub-assigned PR number.

## FS-forge-workflow-spec-catalog: Workflow specifications

Every supported queue (§FS-forge-scope) is one route of the `forge-metadata`
Rhei template (§AR-forge-workflow-pipeline.3). The issue label picks the route:

| Queue | Route |
| --- | --- |
| `library-new-request` | scaffold, then exploration; no repair |
| `library-update-request` | add the version, repair, then exploration |
| `fails-javac-compile`, `fails-java-run`, `fails-native-image-run` | one shared route: repair, then exploration |
| `code-coverage-improvement` | its own template (§AR-code-coverage-improvement) |

Exploration always tries bulk passes before class passes, and `analyze` picks
the mode from the measured report (§AR-forge-workflow-pipeline.7).

The implementation roadmap orders the first known Forge spec gaps to close; see
§ROADMAP-forge-implementation.
