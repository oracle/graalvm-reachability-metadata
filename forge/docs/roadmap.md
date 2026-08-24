# ROADMAP-forge-implementation: Forge implementation roadmap

This roadmap lists the active implementation gaps to close in Forge against the
functional spec (§FS-forge-functional-spec), ordered by delivery priority. It
serves the overall Forge direction in §GOAL-forge-direction. Only open work is
listed: an item is deleted from this file once it ships, and the behavior it
introduced lives in the spec, workflow, or architecture declaration it cites.

1. Dispatcher-owned run preconditions (§ROADMAP-forge-dispatcher-owned-run-preconditions).
2. One metadata collection step everywhere (§ROADMAP-forge-native-finalization).
3. Native-image-run-fail revamp (§ROADMAP-forge-native-image-run-fail-revamp).
4. Pre-push local branch review (§ROADMAP-forge-local-branch-review).
5. Issue-form rules enforced before the claim (§ROADMAP-forge-issue-form-enforcement).
6. Publication descriptor off the tree (§ROADMAP-forge-descriptor-off-tree).
7. Failures name the phase and the step (§ROADMAP-forge-failure-locates-phase-and-step).
8. Algorithmic setup, then neural setup (§ROADMAP-forge-algorithmic-then-neural-setup).

# ROADMAP-forge-dispatcher-owned-run-preconditions: Dispatcher-owned run preconditions

Priority: first (part of §ROADMAP-forge-implementation).

`forge_metadata.py` must be the only way a workflow starts. Running a driver
under `ai_workflows/drivers/` directly is not a supported mode, and no driver may
carry the fallback code that makes standalone execution possible. Every
precondition a run depends on is checked and resolved once, in the dispatcher,
before any workflow is invoked (§AR-forge-control-plane,
§AR-forge-workflow-pipeline).

The dispatcher owns, and a driver may only consume:

- Host and tooling requirements, including agent availability and
  authentication (§FS-forge-host-requirements).
- The strategy bundle and its model, resolved and validated before scanning
  (§STRAT-forge-predefined-strategy-contract).
- Issue eligibility and issue form, including label routing to exactly one
  driver (§WF-forge-workflow-drivers.2).
- Repository locations: the reachability worktree, the scratch metrics
  repository, and the setup-evidence directory. Drivers must not resolve
  repository paths and must not clone a repository as a fallback.
- One pinned GraalVM environment for the whole run, inherited by every Gradle
  command, rather than an environment each driver re-derives per command.
- The issue context and setup-evidence directory used by the driver's neural
  setup. The driver persists a completed neural setup result in the
  continuation marker so a resumed run does not repeat it.

Acceptance: a driver invoked with an incomplete run context fails immediately
with a message naming the missing precondition, instead of resolving, cloning,
or re-deriving it; `resolve_workflow_repo_paths` and the per-driver clone
fallback are removed; every driver-side GraalVM lookup reads the environment the
dispatcher pinned. Fixture and E2E runs keep working through
`forge_metadata.py`, which is already the documented entry point for hermetic
testing (§E2E-forge-workflow-testing.2).

# ROADMAP-forge-native-finalization: One metadata collection step everywhere

This item of §ROADMAP-forge-implementation makes metadata collection a single
shared step — the native trace gate (§AR-forge-workflow-pipeline,
§WF-native-test-verification-gate) — used at every point a run produces
metadata: after a repair, after an exploration batch, and in finalization
(§WF-dynamic-access-workflow). The gate already is this contract in exploration;
the work is making repair and finalization use it instead of a subset of their
own.

The terminal case is the one that matters most: every workflow must end with the
gate, so no run publishes metadata that no native run has checked. Today the
gate exists only in the dynamic-access strategies, which leaves the `javac` and
native-image-run repairs, and the finalization step every workflow shares, ending
without it.

Collecting metadata is always the same ordered three steps, and never a subset:

1. `generateMetadata` — JVM-agent metadata for the coordinate, staged outside
   the durable `metadata/` tree.
2. Native tracing — `runNativeTraceImage` and the trace metadata it collects,
   which is the only source of metadata a JVM-mode agent run cannot observe
   (§WF-native-metadata-tracing).
3. Agent fix — invoked only if the coordinate still fails after 1 and 2, and
   only with the staged agent and trace metadata directories and the failing
   native-image log in hand (§WF-native-test-verification-gate).

The order is the requirement. An agent must never be asked to invent metadata a
deterministic step could have observed, so no call site may run
`generateMetadata` and then jump to the agent, and none may skip tracing because
the JVM-mode run looked healthy. Durable metadata is finalized only after a
passing validation path, and a residual failure returns `FAILED` only once the
agent has not converged. Pi is not used in this path
(§WF-native-test-verification-gate).

Current gaps this item closes:

- Finalization runs `generateMetadata` once and then hands each of its three
  lanes — current-defaults, `future-defaults-all`, and current-defaults on
  GraalVM 25 (§FS-local-ci-equivalent-verification.1) — a bare `./gradlew test`
  with a Codex-then-Pi ladder and no tracing anywhere. Each lane needs its own
  gate invocation, because the image mode and toolchain change which accesses
  the binary needs; metadata observed under one lane does not prove another.
  Pi's test deletion goes with the ladder: it erases the defect the gate exists
  to surface, and a lane it rescues still publishes, as
  `success_with_intervention`.
- Repair runs reach the full gate only in the `java-run` fix mode, so a `javac`
  repair ends without collecting native metadata at all.
- The native-image run fix collects its seed with no tracing at all and then
  repairs it with an agent, detailed in
  §ROADMAP-forge-native-image-run-fail-revamp.

Acceptance is that every call site routes through the same collection step, and
that a metadata fix prompt can be traced back to the trace run that produced its
evidence.

# ROADMAP-forge-native-image-run-fail-revamp: Native-image-run-fail revamp

Priority: third (part of §ROADMAP-forge-implementation).

The `fails-native-image-run` queue is the workflow that exists to make a native
image run, and it is the one workflow that never observes a native image run
while producing its metadata (§WF-native-image-run-fix-workflow). Two problems
have to be fixed together.

## 1. The seed is collected without tracing, and repaired by an agent that never saw a run

`fixTestNativeImageRun` is JVM-agent collection only: `-Pagent test` plus
`metadataCopy` and a durable merge, run once, retried once on a failing test
because the agent is non-deterministic, then thrown as a Gradle failure. No
`runNativeTraceImage` runs at any point, so metadata that only a native run can
reveal is never collected.

The failure path makes it worse. When the task fails but has already written
`metadata/<group>/<artifact>/<newVersion>/reachability-metadata.json`, the driver
sends that untraced seed straight to the agent for a metadata fix and then
reruns the tests; when the file does not exist it fails outright. So the agent is
asked to invent exactly the metadata the collection step declined to observe,
which is the ordering forbidden by §ROADMAP-forge-native-finalization.

The revamp replaces the seed-and-repair pair with the native trace gate
(§AR-forge-workflow-pipeline): `generateMetadata`, then native tracing, then an
agent fix only if the coordinate still fails. `fixTestNativeImageRun` stays a
seed generator and never a success gate, but its output must reach the agent
only after tracing has had its turn.

## 2. A failed run tells nobody and is retried from scratch

A logical failure must produce the same follow-up in every issue queue
(§FS-human-intervention-policy). The follow-up resolver only considers
`library-new-request` and `library-update-request`, so a failed
`fails-native-image-run` run — like a failed `fails-javac-compile` or
`fails-java-run` run — preserves a branch and then silently releases the claim.
The issue returns to `Todo` with no comment, no `human-intervention` label, and
no `resumable` label, so the next cycle re-claims it and repeats the same
failure from scratch while preserved branches accumulate and no maintainer is
told anything.

A logical failure in a repair queue must produce the analysis comment, the
`human-intervention` label, and — when the preserved work carries a continuation
marker — the `resumable` label that lets the next run continue from the phase
that failed instead of restarting (§FS-forge-run-continuation). External
failures keep the existing silent-release behavior in every queue.

Acceptance: a `fails-native-image-run` run collects native-trace metadata before
any agent repair, and one whose run fails in the seed, collection, exploration,
or finalization step ends labeled and commented; a second cycle either resumes
from the preserved marker or, if the same phase fails again, is not
re-attempted silently (§ORCH-forge-orchestration-spec).

# ROADMAP-forge-local-branch-review: Pre-push local branch review

Priority: fourth (part of §ROADMAP-forge-implementation).

A generated branch is reviewed once today, and only after it is already a pull
request. Everything the run held — the verified worktree, the local CI gate
records, the resolved descriptor statistics — is discarded at push time, so the
cheapest moment to fix a finding has passed before any reviewer looks.

This item adds `local_review()` as a phase of `publish_branch()`
(§AR-forge-workflow-pipeline), in the one slot the existing ordering allows:
after the pre-publication gate (§FS-local-ci-equivalent-verification.2) and
after descriptor input resolution, because the review rules dereference render
statistics that only exist once that input resolves, and before the descriptor
is written, because the verdict has to be inside the descriptor the publisher
reads.

The phase must:

- Review cold: a detached worktree at the verified commit, with no session
  shared with the run that generated the branch, over `base_ref..HEAD` — the
  eventual PR diff minus the descriptor commit.
- Judge under the same `skills/review-*/SKILL.md` rules the post-push reviewer
  applies, selected by `task_type` so both reviews use one rule set, with the
  same blocking discipline: request changes only for a concrete violation of an
  enumerated rule.
- Substitute each PR-only input the skills reach for with its local
  equivalent — `git diff` for `gh pr diff`, the local CI gate records for PR
  check runs, the resolved descriptor statistics for PR body entry counts.
- Send a non-approval back for one bounded repair, then re-run the gate over
  the repaired tree, and reset to the verified pre-repair commit if that re-run
  fails; a review finding must not destroy an otherwise-publishable run.
- Carry the verdict as its own descriptor field rather than folding it into
  `modifiers.human_intervention`, so triage can still tell the causes apart,
  and record every non-approval in a tracked findings file.
- Treat an unavailable reviewer as a non-approval rather than an error, so a
  review outage becomes one labeled PR instead of a throughput stall.

Acceptance: a run publishes a branch whose descriptor already carries the
review verdict, a PR opens with the review label applied at open time rather
than after a later review, and a blocked-then-repaired branch publishes
unlabeled with the finding still recorded.

# ROADMAP-forge-issue-form-enforcement: Issue-form rules enforced before the claim

Priority: fifth (part of §ROADMAP-forge-implementation).

Two rules of the issue-form gate (§WF-forge-workflow-drivers.2,
§AR-forge-workflow-pipeline) are contract in the spec and nowhere in the code:

- **Exactly one workflow label.** An issue carrying two queue labels is
  processed once per queue that matches it, so the same issue can be claimed,
  worked, and published more than once, each time by a different driver working
  from different assumptions about what the issue asks for.
- **A `fails-*` issue must request a version strictly above the coordinate's
  current `latest`.** This is caught today inside the driver, after the claim,
  the project transition, and the worktree already exist — the full cost of the
  rejection is paid before the rejection happens.

Both are decidable from the issue payload alone, so both belong in the
deterministic gate rather than in prose each driver re-checks
(§root/PRCPL-prefer-algorithmic), and both must be decided at the boundary,
before the first side effect, so a malformed issue never consumes a queue slot
(§root/PRCPL-verify-inputs).

Acceptance: an issue carrying more than one workflow label is rejected without
being claimed, and the rejection names the conflicting labels; a `fails-*` issue
whose requested version is not strictly above the current `latest` is rejected
at the same point, before assignment, project transition, or worktree creation;
and the driver-side late checks are deleted rather than left in place as a
second implementation of the same rule.

# ROADMAP-forge-descriptor-off-tree: Publication descriptor off the tree

Priority: sixth (part of §ROADMAP-forge-implementation).

The publication descriptor is a build artifact of one run, not repository
content, but today it is committed at
`stats/<group>/<artifact>/<version>/forge-publication.json`, appears in the pull
request diff, and lands on `master` when the PR merges
(§GIT-publication-descriptor). The repository has already had to absorb that:
the stats schema validator was widened to accept the file. Every merged Forge PR
leaves one behind, in a directory that otherwise holds derived per-version
metrics.

This item makes the descriptor exist for exactly as long as it is needed — from
the push that certifies a branch until the pull request is open — and never
enter a tree.

The constraint that shapes the design: an unprivileged local run hands data to
CI only through a pushed ref, and GitHub fires `push` workflows for
`refs/heads/*` and `refs/tags/*` only, so a side ref such as `refs/forge/*`
would be pushed and silently ignored. The descriptor therefore travels as an
annotated tag rather than as a commit:

- Local finalization pushes the verified branch, then an annotated tag
  `forge-publication/<publication-id>` pointing at the verified head commit,
  carrying the descriptor JSON as its message. The branch itself carries only
  generated work.
- Branch Ready triggers on that tag, validates the descriptor against its own
  copy of the schema from the default branch, and hands the commit on
  (§GIT-actions-publication). The tag *is* the SHA binding: it names the commit
  it certifies, which removes the current awkwardness that the head SHA cannot
  be a descriptor field because a commit cannot contain its own object ID.
- The publisher reads the descriptor from the tag, opens the pull request
  against `descriptor.branch`, and deletes the tag once the PR exists, so the
  tag namespace does not accumulate one entry per publication.

Everything the descriptor is for survives unchanged: it is still data and never
GitHub instructions, still schema-validated by trusted default-branch code,
still bound to one exact commit, and still the only channel from local
verification to the publisher.

Retries need the tag to be force-updatable, and a re-pushed tag re-triggers
publication; the existing `Forge-Publication-ID` idempotency check absorbs that
by resolving to the pull request already opened for the identity. If a
descriptor ever outgrows a tag message, the fallback is a lightweight tag
pointing at a blob.

Acceptance: a merged Forge pull request adds no `forge-publication.json`
anywhere, and `stats/` contains only derived metrics; the pull request diff
contains only generated work; the stats schema validator no longer needs a
descriptor exemption; and a publication whose tag is re-pushed opens no second
pull request.

# ROADMAP-forge-failure-locates-phase-and-step: Failures name the phase and the step

Priority: seventh (part of §ROADMAP-forge-implementation).

When a run fails, the failure must say **where** it failed: the phase, and the
step inside it. Today a failed run reports a status and an error, and the reader
reconstructs the location by reading logs backwards — even though the pipeline
already defines the vocabulary needed to state it outright
(§AR-forge-workflow-pipeline).

Every failure report must carry both coordinates:

- **Phase**: one of the banded segments of the pipeline — `claim`, `setup`,
  `fix`, `explore`, `finalization`, `publication`. The five durable ones are
  already the continuation vocabulary (§FS-forge-run-continuation); `claim` is
  the dispatcher-side segment before a run exists.
- **Step**: the exact method from the pipeline that failed — for example
  `check_host_requirements()`, `neural_setup()`, `check_setup()`,
  `native_trace_gate()`, `generate_tests()`, `local_ci_check()`,
  `push_verified_branch()` — plus the operand it failed on when the step has
  one, such as the class being generated or the gate command that returned
  non-zero.

The pair must appear identically everywhere a failure surfaces, so the terminal,
the marker, and the issue tell the same story: the worker's terminal output and
run log, the terminal run status returned to `forge_metadata`, the continuation
marker, and the human-intervention comment
(§FS-human-intervention-policy). A failure that cannot name its step is itself a
defect: an unlabeled failure means a code path is raising outside the pipeline's
own step boundaries.

Acceptance: every terminal failure prints one line of the form
`run failed in <phase>/<step>` before its error detail; the continuation marker
records the same phase and step, so a resumed run states what it is retrying;
the human-intervention comment leads with the same pair; and no failure path
reports a phase without a step.

# ROADMAP-forge-algorithmic-then-neural-setup: Algorithmic setup, then neural setup

Priority: eighth (part of §ROADMAP-forge-implementation).

The setup phase must be three steps in one fixed order, all owned by the driver
(§AR-forge-workflow-pipeline): `normal_setup()` does every setup step that needs
no model, `neural_setup()` does every setup step that needs one, and
`check_setup()` verifies the result before any generation starts. Today the
order is inverted and the ownership is split: `forge_metadata` invokes the
preflight agent before driver dispatch, the fix drivers try to apply its
decision before the target version is copied or created, and artifact URL
population and source-context download happen later still — so the model decides
about a tree that does not exist yet, and part of the neural work runs after the
step that is supposed to contain all of it.

The target shape:

- **`normal_setup(coordinates, strategy, run_context) -> PreparedRunResult`** —
  algorithmic. Branch creation or checkout, scaffolding a new library or copying
  the supported version for a repair, resolution of the test and metadata
  directories. Its output is the only valid input to the neural step.
- **`neural_setup(PreparedRun) -> NeuralSetupResult`** — neural, and the single
  place every model-driven setup step happens: artifact URL population,
  source-context download, the library-preparation decision, and application of
  its typed actions. No model-driven setup work may run before `normal_setup()`
  or between `neural_setup()` and `check_setup()`. The step runs on an agent with
  web tooling, because resolving artifact URLs, downloading sources, and judging
  what a library needs to build require reading upstream repositories, Maven
  Central, and library documentation; a strategy mapping this step onto an agent
  without web access is misconfigured
  (§STRAT-forge-predefined-strategy-contract).
- **`check_setup(NeuralSetupResult) -> ReadyRun | FAILED`** — algorithmic.
  Opens each artifact the setup steps were supposed to produce and confirms it
  was properly generated — the coordinate's `index.json` with its four URLs, the
  source context, the preflight JSON against its schema, each decided action in
  the tree — then captures the recovery checkpoint
  (§WF-forge-workflow-drivers.1, §root/PRCPL-verify-inputs).

Failure is uniform with the rest of the pipeline: a neural-setup timeout or an
unusable response is `RUN_STATUS_FAILURE` to the driver — exactly as
`agent_fix()` fails — and the driver reports the run as failed to
`forge_metadata`, never as a successful `no_action` decision. Each setup step
writes its artifact to a place fixed by the coordinate, and `NeuralSetupResult`
is the set of those paths rather than a report about them, so nothing the agent
returns can move where the check looks (§WF-forge-workflow-drivers.1). A
completed `NeuralSetupResult` is persisted in the continuation marker so a
resumed run does not repeat it (§FS-forge-run-continuation), and the failure
names the setup step it died in (§ROADMAP-forge-failure-locates-phase-and-step). The run context
these steps consume is dispatcher-owned
(§ROADMAP-forge-dispatcher-owned-run-preconditions).

Acceptance: no agent is invoked for setup before the driver has produced a
`PreparedRun`; artifact URL population and source download happen inside
`neural_setup()` rather than after it; generation cannot start from anything but
a `ReadyRun`; a neural-setup timeout ends the run as a setup failure instead of
proceeding as `no_action`; a setup artifact that is missing, unparseable, or
missing a required field — an `index.json` without its four URLs, an absent
source context, a preflight JSON that does not validate — fails the setup
segment rather than being repaired or ignored; and a resumed run whose marker
holds a completed neural setup skips straight to `check_setup()`.
