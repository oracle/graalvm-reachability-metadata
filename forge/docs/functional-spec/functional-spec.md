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
`library-update-request`, `fails-javac-compile`, `fails-java-run`,
`fails-native-image-build`, or `fails-native-image-run`. §FS-forge-scope enumerates the queues Forge resolves,
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
6. **Native-image build fixes** (`fails-native-image-build`) — repair the
   test project, its allowed native-image flags, or the reachability metadata
   so the native test image builds against the new library version.
7. **Native-image runtime fixes** (`fails-native-image-run`) — update
   reachability metadata so `nativeTest` passes against the new library
   version, using the native metadata exploration phase.

Successful fixes use the matching PR labels (`library-new-request`,
`library-update-request`, `fixes-javac-fail`, `fixes-java-run-fail`,
`fixes-native-image-build-fail`, or `fixes-native-image-run-fail`) so the
launcher's review recognizes the pull request as Forge's
(§FS-published-pr-review.1).

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
That automation also assigns each `fails-native-image-build` issue to a
maintainer (§root/FS-library-version-update-automation.4), and Forge fetches
only unassigned issues (§AR-forge-workflow-pipeline.1), so a maintainer hands
one to Forge by removing the assignment.

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
| **Fix request** | The handoff a failed check writes for the `fix` state: the commands, their output, the files it may edit, and the code to return to; it states facts, never hints. The fix runs once, best effort; the sending program reruns the check and lets that result decide (§AR-forge-workflow-prompts). |
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

## FS-forge-agent-runtime-selection: Two agent inputs

§GOAL-forge-direction §root/PRCPL-prefer-algorithmic

A Forge run has exactly two agent inputs, and nothing else about a run is a
choice: strategies are fixed per issue label (§AR-forge-workflow-pipeline.7).

- **`setup_agent`** prepares the library before any test is written: it fills
  the coordinate's missing artifact URLs, declares the companion libraries and
  Docker images the tests will need, and touches nothing else. It is the only
  agent that needs the web — finding a test-sources path or the documentation of
  an unfamiliar library takes search and reading — so it must be a Claude Code
  target, whose WebSearch and WebFetch tools are built in. The default is
  `claude-code[medium]:sonnet`.
- **`worker_agent`** writes the tests that cover dynamic-access call sites and
  fixes whatever check a program reports failing. Both jobs only read the
  repository, edit files, and run Gradle, so one input serves both. The default
  is `pi[high]:openai-codex/gpt-5.6-luna`.

The launcher has one more agent input that no run sees: **`review_agent`**
reviews the published pull requests (§FS-published-pr-review.4). Telling a
metadata miss from a Native Image regression takes the web and the GraalVM
history, so it is always a Claude Code target, and the launcher rejects any
other agent before it reviews anything. The default is
`claude-code[high]:opus`.

A target is `<agent>[<mode>]:<model>`, where the agent is `pi` or `claude-code`
and the mode selects the reasoning level from the template's agent profiles;
OpenAI models run through `pi` with the `openai-codex` provider. Codex and
OpenCode are not supported. The launcher validates both targets before it
claims an issue, and the host gate checks that each selected agent is installed
and authenticated (§FS-forge-host-requirements). Each target reaches its agent
states unchanged, so the agent and model of every edit are the ones the
operator selected and the run's metrics record them.

### 1. File-system scope

The run's agent states, `setup`, `cover`, and `fix`, walk or search the file
system only inside the run's own trees: the run workspace, which holds the run
note and the reports; the worktree; the main sources, a tree of their own
because the worktree ignores them and a walk that honors its ignore files never
reaches them; the Gradle user home and the read-only dependency cache the run
builds with; and the GraalVM home. The run note lists each as an absolute search root that
exists when it is written (§AR-forge-workflow-prompts.2). Outside those trees an
agent opens only a file whose path it was given, such as a jar under the local
Maven repository that a build's output names. It never walks a shared host
location such as `/tmp`, the home directory, or `/`, even to reach a run tree
that lies inside one: anything another process left there can stall a walk or
exhaust the memory every run on the host shares, and one file that never ends
is enough. Nothing the run needs lies there unnamed.

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

A release counts only once GitHub confirms it: the issue reads back in `Todo`
with no assignee. GitHub may be down at the very moment a release is needed,
since an outage is often why the run failed, so a release whose GitHub calls
fail is retried a few times. One GitHub still cannot confirm is reported as
unconfirmed, naming the issue to release by hand, never as released; one GitHub
answered but shows still claimed is not retried, since a retry could clear a
claim made since. An unconfirmed release must not stop the launcher or hide the
failure that led to it.

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

An operator may explicitly enable blocked-issue claiming for one invocation or
worker configuration. The override is disabled by default and bypasses only the
open-blocker condition: every other live claimability condition above remains
mandatory.

The branch supplying the running Forge implementation does not select the
repository state an issue targets. The issue-form gate decides against freshly
fetched `origin/master`, and `prepare` fetches again, pins that commit as the
run's base, and creates the isolated worktree from it; a base that cannot be
resolved fails the run before anything is changed. Every repository-dependent
precondition runs inside the run against that worktree: a
`chunked-dynamic-access` issue must resolve its exhaust report and find the
preceding chunk merged (§AR-dynamic-access-exhaust-report), and a
`library-new-request` must request a version the base does not support yet
(see 2.2). A failed
precondition ends the run, the launcher releases the claim back to `Todo`, and
its classification decides what happens to the issue
(§FS-human-intervention-policy). An infrastructure failure takes no issue
action, so a later cycle retries it. A bad input would fail identically on every
retry: Forge posts a comment naming the reason and labels the issue
`human-intervention`, which takes it out of the queue until a maintainer
resolves it.

An issue can still close between the claim and `prepare`'s own live read.
Such an issue is not a bad input: it has left the queue, and nothing is left
for a maintainer to do. `prepare` ends the run on it, and the launcher only
clears the Forge assignee the claim set. It adds no label, posts no comment,
and leaves the board item where closing the issue put it.

#### 2.1 An issue blocked on GraalVM is not claimed

An issue labeled `graalvm-bug` waits on a GraalVM defect
(§root/FS-graalvm-blocked-work.2) that no run can fix, so every attempt
would spend a claim and an agent budget on the same failure. The label is
excluded twice: from the queue fetch, so the issue costs nothing while it
waits, and from the live claim-time re-read, so a label added after the fetch
still holds. Neither `resumable` nor the blocked-issue override lifts it:
`resumable` lifts no hold, and the override concerns open GitHub blockers,
while this label marks a defect outside the repository that only a
maintainer's release clears (§root/FS-graalvm-blocked-work.3).

#### 2.2 A new library the base already supports is a bad input

A `library-new-request` asks for support the repository lacks
(§FS-forge-scope). When an entry of the artifact's `index.json` on the pinned
base already lists the requested version, in `tested-versions` or as its
`metadata-version`, the version's metadata, tests, and stats already ship. A
run would spend its agent budget re-creating them, only to publish a duplicate
or to end with nothing to publish. `prepare` therefore stops such a run before
any agent state, as a bad input whose comment names the commit that added the
version. A maintainer then closes the issue as a duplicate, or relabels it a
`library-update-request` when the reporter asks for more than the shipped
version covers. A `default-for` range does not list a version, and a version
the index does not list passes, whatever other versions are supported.

A chunked issue is the exception (§FS-forge-chunked-dynamic-access): its
earlier chunk merged the version, and the next chunk resumes from the
`chunked-dynamic-access` label or the coordinate-local exhaust report. That
report lives in the version's test project, not in `index.json`, so the rule
is a precondition of the run rather than an issue-form rule (see 3).

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
`jvm` and `native-repair` find out whether it already passes
(§AR-forge-workflow-pipeline.3).

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

## FS-unsupportable-version-diagnosis: Unsupportable-version verdict in setup

For `fails-javac-compile`, `fails-java-run`, `fails-native-image-build`, and
`fails-native-image-run` issues, the `setup` state answers one more question before any repair starts:
does covering the library's dynamic-access calls require behavior Native Image
does not support (§root/FS-test-contract.4.5)? The inputs — the issue's failure
evidence and the library's sources for this and newer versions — all exist
before any test is written, so an unsupportable verdict costs part of one setup
turn and saves the whole repair. Without it, the disposition of
§root/FS-contribution-contract.5.4 is reachable only through a reviewer: a run
that fails honestly ends in `human-intervention` and publishes nothing. Issues
without failure evidence are never asked: a `library-new-request` has no index
entry to record a skip into, and a `library-update-request` carries no failure
to diagnose. `run.md` names the bar only for the four `fails-*` labels
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

The setup tail keeps `skipped-versions` additions on the four `fails-*` labels
and reverts them on every other label (§AR-forge-workflow-pipeline.4.1). When
the index gained an entry for the target version, `setup-check` turns the run into
the skip record before anything else. The coordinate's paths return to the
pinned base — the version `prepare` added, its tests, metadata, and stats are
dropped, as §root/FS-contribution-contract.5.4 requires of a skip record — the
entries are written onto the base index, `validateIndexFiles` and the
pre-publication gate (§FS-local-ci-equivalent-verification.2) accept it, and
the commit is checkpointed. `setup-check` then exits `21` and the run goes
straight to `publish`: no repair, exploration, native check, or finalization
runs.

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
once, after `jvm` and `native-repair` have made the version work, counts the
classes with uncovered calls less those an earlier chunk processed, and makes
the whole decision from that count:

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

The exhaust record is a result of its own, so `publish` writes it before
deciding whether the coordinate changed. Exhausting a class spends every
attempt class mode gives it: a chunk that drops the record leaves the next
claim to repeat those attempts, and a last chunk that drops it never closes its
issue. A chunk that is not the last therefore publishes the classes it completed
or exhausted with `Refs: #<issue>` even when the report is its only change, and
the last chunk removes the report an earlier chunk left and publishes that with
`Fixes: #<issue>`, whether or not it processed a class itself. A chunk whose
record changes nothing — one that is not the last and completed or exhausted no
class, or a last chunk with no report to remove — publishes only what else it
changed (§FS-forge-run-status).

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
| `fails-javac-compile`, `fails-java-run`, `fails-native-image-build`, `fails-native-image-run` | one shared route: repair, then exploration |
| `code-coverage-improvement` | its own template (§AR-code-coverage-improvement) |

Exploration always tries bulk passes before class passes, and `analyze` picks
the mode from the measured report (§AR-forge-workflow-pipeline.7).

The implementation roadmap orders the first known Forge spec gaps to close; see
§ROADMAP-forge-implementation.
