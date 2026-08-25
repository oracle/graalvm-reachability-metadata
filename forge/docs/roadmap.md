# ROADMAP-forge-implementation: Forge implementation roadmap

This roadmap lists the active implementation gaps to close in Forge against the
functional spec (§FS-forge-functional-spec), ordered by delivery priority. It
serves the overall Forge direction in §GOAL-forge-direction. Only open work is
listed: an item is deleted from this file once it ships, and the behavior it
introduced lives in the spec, workflow, or architecture declaration it cites.

1. Dispatcher-owned run preconditions (§ROADMAP-forge-dispatcher-owned-run-preconditions).
2. One metadata collection step everywhere (§ROADMAP-forge-native-finalization).
3. One fixer, prompted by the failure (§ROADMAP-forge-one-fixer-variable-prompt).
4. Native-image-run-fail revamp (§ROADMAP-forge-native-image-run-fail-revamp).
5. Pre-push local branch review (§ROADMAP-forge-local-branch-review).
6. Issue-form rules enforced before the claim (§ROADMAP-forge-issue-form-enforcement).
7. A rejected issue is told why (§ROADMAP-forge-issue-form-rejection-feedback).
8. Publication descriptor off the tree (§ROADMAP-forge-descriptor-off-tree).
9. Failures name the phase and the step (§ROADMAP-forge-failure-locates-phase-and-step).
10. Algorithmic setup, then neural setup (§ROADMAP-forge-algorithmic-then-neural-setup).

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
  (§FS-forge-predefined-strategy-contract).
- Issue eligibility and issue form, including label routing to exactly one
  driver (§AR-forge-driver-queues).
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
dispatcher pinned. Fixture runs keep working through `forge_metadata.py`, the
supported control-plane entry point (§AR-forge-control-plane).

# ROADMAP-forge-native-finalization: One metadata collection step everywhere

This item of §ROADMAP-forge-implementation makes metadata collection a single
shared step — the native trace gate (§AR-forge-workflow-pipeline,
§FS-native-test-verification-gate) — used at every point a run produces
metadata: after a repair, after an exploration batch, and in finalization
(§AR-dynamic-access-workflow). The gate already is this contract in exploration;
the work is making repair and finalization use it instead of a subset of their
own.

The terminal case is the one that matters most: every workflow must end with the
gate, so no run publishes metadata that no native run has checked
(§AR-forge-workflow-engine.2, §AR-native-test-verification-callers). Today the
dynamic-access engines, `basic_iterative`, and `java_run_iterative` invoke it.
That leaves `javac_iterative` — which shares one implementation with
`java_run_iterative` but skips the gate on the compile-fix branch — the
native-image-run repair, and the finalization step every workflow shares, all
ending without it.

Collecting metadata is always the same ordered three steps, and never a subset:

1. `generateMetadata` — JVM-agent metadata for the coordinate, staged outside
   the durable `metadata/` tree.
2. Native tracing — `runNativeTraceImage` and the trace metadata it collects,
   which is the only source of metadata a JVM-mode agent run cannot observe
   (§FS-native-test-verification-gate).
3. Agent fix — invoked only if the coordinate still fails after 1 and 2, and
   only with the staged agent and trace metadata directories and the failing
   native-image log in hand (§FS-native-test-verification-gate).

The order is the requirement. An agent must never be asked to invent metadata a
deterministic step could have observed, so no call site may run
`generateMetadata` and then jump to the agent, and none may skip tracing because
the JVM-mode run looked healthy. Durable metadata is finalized only after a
passing validation path, and a residual failure returns `FAILED` only once the
agent has not converged (§FS-native-test-verification-gate).

Current gaps this item closes:

- Finalization runs `generateMetadata` once and then hands each of its three
  lanes — current-defaults, `future-defaults-all`, and current-defaults on
  GraalVM 25 (§FS-local-ci-equivalent-verification.1) — a bare `./gradlew test`
  with a fixed recovery ladder and no tracing anywhere. Finalization must run
  the gate once, as its first step, and the three lanes after it
  (§AR-forge-workflow-pipeline): the gate is what collects and proves the
  metadata, and a lane then judges that metadata under its own image mode and
  toolchain. A lane failure is not a second collection pass — it is repaired
  against the trace evidence the gate already produced, re-obtained in that
  lane's environment when the mode or toolchain is what the failure turns on.
- Nothing in the gate's environment is selectable by its caller.
  `verify_native_test_passes` derives its own Gradle environment and takes no
  override, so a repair cannot re-run the trace build under the toolchain or
  image mode of the lane that failed. The environment must become an argument.
- Repair runs reach the full gate only in the `java-run` fix mode, so a `javac`
  repair ends without collecting native metadata at all.
- The native-image run fix collects its seed with no tracing at all and then
  repairs it with an agent, detailed in
  §ROADMAP-forge-native-image-run-fail-revamp.

Acceptance is that every call site routes through the same collection step, and
that a metadata fix prompt can be traced back to the trace run that produced its
evidence.

# ROADMAP-forge-one-fixer-variable-prompt: One fixer, prompted by the failure

Priority: third (part of §ROADMAP-forge-implementation).

Forge repairs a failed check with one fixer whose *prompt* is chosen by the step
that failed (§AR-forge-workflow-pipeline). Today it has the opposite shape: each
step owns a hard-coded ladder of fixed prompts, and which repair runs is decided
by how far down its ladder the run has fallen rather than by what the evidence
says. A ladder's later rung is reached because the earlier one did not converge
— which is why the last rung ends up being the one move no evidence supports.

Finalization alone runs four unrelated ladders:

| Step | Ladder today |
| --- | --- |
| The three native test lanes | analysis-agent metadata fix, rerun, then the test agent deleting the failing tests, rerun — once per lane |
| `checkMetadataFiles` | an `allowed-packages` auto-append loop up to three times, then up to three analysis-agent attempts |
| Style fix and checks | up to three test-agent checkstyle attempts, each re-running `./gradlew test` and invoking a second recovery inside the attempt |
| `splitTestOnlyMetadata` and its legacy test-config rejection, `generateLibraryStats`, the commit | no repair at all — the run fails outright |

What replaces them is one fixer, invoked on the terms `agent_fix()` already sets
out — after a deterministic step has failed, on that step's own records, one
bounded attempt, with the step's re-run deciding. Only the prompt varies, and it
varies with the failing step: a gate failure prompts for metadata, a lane
failure prompts against that lane's trace evidence, `checkMetadataFiles` prompts
for metadata validity, a style failure prompts for the violation. The steps that
have no repair today gain one; the generated-test quality screening deliberately
does not, because it asks whether a test should exist rather than reporting a
defect.

Test deletion stops being a ladder rung and becomes a conclusion the fixer must
justify. Two findings have to hold: that the failure is not a metadata gap,
established by re-running the coordinate under the gate's trace build and
reading the binary's exit status rather than by the agent's own account
(§FS-native-test-verification-gate.5); and that the behavior under test is genuinely
unsupported by Native Image, named in the record. Ruling metadata out is not
enough on its own — most non-metadata failures are ordinary defects and are
repaired. Every deletion is recorded with the finding that justified it and
travels in the publication descriptor, including the second and third within one
run: the base class keeps only the first intervention it sees, so a run that
deletes in two lanes reports one.

Acceptance is that no step carries a repair sequence of its own, that the prompt
a repair used can be traced to the step and records that selected it, and that
every deletion in a run appears in the descriptor with its justification.

# ROADMAP-forge-native-image-run-fail-revamp: Native-image-run-fail revamp

Priority: fourth (part of §ROADMAP-forge-implementation).

The `fails-native-image-run` queue is the workflow that exists to make a native
image run, and it is the one workflow that never observes a native image run
while producing its metadata (§AR-forge-driver-queues.4). Two problems
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
re-attempted silently (§AR-forge-orchestration).

# ROADMAP-forge-local-branch-review: Pre-push local branch review

Status: implemented for every publication route except
`code-coverage-improvement`, which is deferred until its phase evidence is
snapshot-backed.

Priority: fifth (part of §ROADMAP-forge-implementation).

Before this item, a generated branch was reviewed once and only after it was
already a pull request. Everything the run held — the verified worktree, the
local CI gate records, the resolved descriptor statistics — was discarded at
push time, so the cheapest moment to fix a finding passed before any reviewer
looked.

The implementation adds `local_review()` as a phase of `publish_branch()` for
every route except `code-coverage-improvement`
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
- Answer a finding itself rather than delegating it to the bounded repair step
  (§AR-forge-workflow-pipeline): the reviewing agent reports the finding and
  makes the edit in the same pass, because no deterministic check can re-run to
  confirm that a rule violation is gone.
- Take the reviewer's report through one structured verdict — decision, review
  comment, finding title and body, and a fix note when it repaired something —
  and record all of it as returned. Forge renders `forge/FINDINGS.md` from the
  title and body so the record cannot drift, and never rewrites the decision or
  the fix note, which describe judgments and edits only the reviewer made.
- Re-run finalization and the local CI gate together over the repaired tree,
  and only over a repaired tree: whether a repair happened is read from the
  paths changed against the verified commit, so an approval costs no gate time.
  That selects which steps run; it does not edit the verdict. Both are deterministic steps, so a failure of either — not
  the finding — is what gets one bounded repair, and a repaired tree that still
  fails either resets to the verified pre-repair commit, keeping the findings
  entry and the verdict and publishing that branch labeled; a review finding
  must not destroy an otherwise-publishable run.
- Never fail the run. A finding the reviewer cannot repair publishes flagged
  for a maintainer (§FS-human-intervention-policy) rather than stopping
  publication, and is filed like any other.
- Carry the verdict as its own descriptor field rather than folding it into
  `modifiers.human_intervention`, so triage can still tell the causes apart,
  and render it in the PR body as a Local Agent Review section. Between the
  review and the descriptor write the verdict is staged with the run's other
  in-flight publication data, so a resumed publication does not lose it.
- Land the descriptor field and its renderer on the default branch before any
  run emits them. The publisher validates against its own copy of the schema and
  rejects unknown fields (§AR-actions-publication), so the two must ship in that
  order or every publication from a run carrying the verdict fails.
- Treat an unavailable reviewer as a non-approval rather than an error, so a
  review outage becomes one labeled PR instead of a throughput stall.

Acceptance: a run publishes a branch whose descriptor already carries the
review verdict, a PR opens with the review label applied at open time rather
than after a later review, and a branch whose finding the reviewer repaired
publishes unlabeled — finalization and the gate having passed again — with the
finding still recorded in `forge/FINDINGS.md`.

# ROADMAP-forge-issue-form-enforcement: Issue-form rules enforced before the claim

Priority: sixth (part of §ROADMAP-forge-implementation).

Three rules of the issue-form gate (§AR-forge-driver-queues,
§AR-forge-workflow-pipeline) were contract in the spec and nowhere in the code:

- **Exactly one workflow label.** An issue carrying two queue labels is
  processed once per queue that matches it, so the same issue can be claimed,
  worked, and published more than once, each time by a different driver working
  from different assumptions about what the issue asks for.
- **A `fails-*` issue must request a version strictly above the coordinate's
  current `latest`.** Nothing rejected such an issue; the driver only consulted
  the same predicate late, to decide whether to index the requested version as
  the new `latest`, after the claim, the project transition, and the worktree
  already existed — the full cost of the rejection paid before the rejection.
- **The coordinate must be fetchable, not merely parseable.** A title was
  accepted once a regular expression found `group:artifact:version` in it, so a
  typo in a group, an artifact nobody published, or a version that does not
  exist upstream was discovered as a Gradle resolution error deep inside a run
  that already held a claim and a worktree.

All three are decidable before the first side effect — the first two from the
issue payload and the metadata index, the third from one request against a URL
the coordinate fully determines — so all three belong in the deterministic gate
rather than in prose each driver re-checks (§root/PRCPL-prefer-algorithmic,
§root/PRCPL-verify-inputs). The fetch already exists in
`utility_scripts/native_image_artifact.py`, which reads POMs from Maven Central
for Native Image eligibility; the gate needs only the existence answer, against
Maven Central and then the Confluent fallback
(§root/AR-build-infrastructure.1). The answer is three-valued, because an
unreachable repository is not evidence that an artifact is missing: only
*absent from every repository* rejects, while *undecided* leaves the issue for
a later cycle.

Two rules the gate already applied — the title parses as Maven coordinates, and
a `fails-*` coordinate resolves a current `latest` — were silent `return None`
paths. They are now named rules of the same gate so they carry the same
feedback.

What the gate says when it rejects is §ROADMAP-forge-issue-form-rejection-feedback.

Acceptance: an issue carrying more than one workflow label is rejected without
being claimed, and the rejection names the conflicting labels; a `fails-*` issue
whose requested version is not strictly above the current `latest` is rejected
at the same point, before assignment, project transition, or worktree creation;
a coordinate no configured repository publishes is rejected before the claim,
naming the coordinate and the repositories tried; and the driver-side late use
of the same version predicate is deleted rather than left in place as a second
implementation of the rule.

# ROADMAP-forge-issue-form-rejection-feedback: A rejected issue is told why

Priority: seventh (part of §ROADMAP-forge-implementation).

A form rejection reached only the worker log
(§ROADMAP-forge-issue-form-enforcement). The issue kept its place in the queue,
was rescanned and re-rejected every cycle, and the reporter — the one person who
can fix it — was never told anything, so nothing about the issue ever changed.

The information needed to tell them is already in hand. Each rule of the gate is
decided separately from the issue payload, so a rejection knows exactly which
rule failed and on what value (§FS-forge-run-requirements.3). That name selects
one predefined comment, which quotes the offending value and states what the
issue must carry instead. The rejection then, in order: runs `release_claim()`
if a claim was somehow taken, posts the comment, and closes the issue
(§AR-forge-architecture).

**Closing, not returning to `Todo`.** A form defect is not repaired by waiting:
no later cycle can change the label set or the title, so an open rejected issue
is only a guarantee of being re-rejected forever. Closing takes it out of every
queue and hands the next move to the reporter, who edits and reopens or files a
corrected issue.

Closing also settles the dedup requirement. A closed issue is not scanned, so an
unchanged issue can never collect a second comment — the state change is the
deduplication. The one case left is a reopened issue whose defect was not fixed,
so the comment carries a marker keyed on the failed rule and the offending
value: a rejection whose marker is already on the issue closes it again without
commenting, while an edited title changes the value, changes the marker, and is
judged afresh.

A form rejection is an input defect, not a generation failure: it applies no
`human-intervention` label and preserves no branch
(§FS-human-intervention-policy).

Acceptance: each issue-form rule has one predefined comment naming it and
quoting the offending value; a rejected issue carries that comment and is closed
within the cycle that rejected it; a reopened issue with the same defect is
closed again without a second comment while an edited title is judged afresh;
and no form rejection applies `human-intervention` or preserves a branch.

# ROADMAP-forge-descriptor-off-tree: Publication descriptor off the tree

Priority: eighth (part of §ROADMAP-forge-implementation).

The publication descriptor is a build artifact of one run, not repository
content, but today it is committed at
`stats/<group>/<artifact>/<version>/forge-publication.json`, appears in the pull
request diff, and lands on `master` when the PR merges
(§AR-publication-descriptor). The repository has already had to absorb that:
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
  (§AR-actions-publication). The tag *is* the SHA binding: it names the commit
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

Priority: ninth (part of §ROADMAP-forge-implementation).

When a run fails, the failure must say **where** it failed: the phase, and the
step inside it. Today a failed run reports a status and an error, and the reader
reconstructs the location by reading logs backwards — even though the pipeline
already defines the vocabulary needed to state it outright
(§AR-forge-workflow-pipeline). This is the failure half of the legible run
output required by §FS-forge-run-output-legibility.

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

Priority: tenth (part of §ROADMAP-forge-implementation).

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

```text
normal_setup()
  → create branch
  → scaffold new library or copy target version
  → resolve test and metadata directories

neural_setup()
  → populate artifact URLs
  → materialize source context
  → run preflight agent
  → parse and validate its JSON
  → algorithmically apply dependency and Docker actions
  → render only remaining guidance

check_setup()
  → verify all expected files and edits
  → capture checkpoint
```

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
  (§FS-forge-predefined-strategy-contract).
- **`check_setup(NeuralSetupResult) -> ReadyRun | FAILED`** — algorithmic.
  Opens each artifact the setup steps were supposed to produce and confirms it
  was properly generated — the coordinate's `index.json` with its four URLs, the
  source context, the preflight JSON against its schema, each decided action in
  the tree — then captures the recovery checkpoint
  (§AR-forge-driver-contract, §root/PRCPL-verify-inputs).

Failure is uniform with the rest of the pipeline: a neural-setup timeout or an
unusable response is `RUN_STATUS_FAILURE` to the driver — exactly as
`agent_fix()` fails — and the driver reports the run as failed to
`forge_metadata`, never as a successful `no_action` decision. Each setup step
writes its artifact to a place fixed by the coordinate, and `NeuralSetupResult`
is the set of those paths rather than a report about them, so nothing the agent
returns can move where the check looks (§AR-forge-driver-contract). A
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
