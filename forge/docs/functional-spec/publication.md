# FS-forge-publication-readiness: Publication readiness

Generation ending is not the same as a run being publishable. Everything a run
must still satisfy between a finished working tree and a merged pull request is
grounded here: the verification it must pass (§FS-local-ci-equivalent-verification),
the tested-version split its result must record
(§FS-library-update-tested-version-split), the review it receives before the
push (§FS-local-branch-review), when the result needs maintainer judgment rather
than another automated attempt (§FS-human-intervention-policy), and the
automated review the published PR receives (§FS-automated-pr-review).

Publication readiness is consumed once per publication branch. After the
trusted publisher opens the matching Forge pull request, identified by the
exact head branch and publication ID, later pushes maintain that pull request;
they are not new publication attempts and must not re-enter descriptor
validation or privileged publication. This remains true when a force-pushed
rebase makes GitHub's path comparison observe publication descriptors that
arrived from the base branch. An open or merged matching Forge pull request is
a successful no-op; an ambiguous match or a matching pull request closed
without merge still fails for inspection. §GOAL-shorten-issue-to-shipped-metadata

## FS-local-ci-equivalent-verification: Local pre-publication verification

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

### 1. Generation and finalization

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

### 2. Pre-publication gate

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
§FS-human-intervention-policy. A finding from the pre-push review is not a
failed check and does not reach this repair; §FS-local-branch-review states what
answers it, and re-runs this gate when it has. After the gate passes, Forge must
algorithmically compare the final PR diff with the expected library-scoped
paths. The verification metrics and PR description must list any shared
repository paths, and the local reviewer must treat those paths as disposition
evidence. Publication may add `human-intervention` only when the resulting
review decision is `rejected` with action `human-intervention`, following
§FS-human-intervention-policy.

## FS-native-test-verification-gate: Native test verification gate

Metadata production for a coordinate begins with an *approximation*. The JVM
`native-image-agent`, and any metadata an agent wrote by hand, record only the
reflection, resource, proxy, and serialization accesses observed while the test
suite runs on HotSpot. Two classes of access slip past that: accesses the suite
never exercised on HotSpot, and accesses whose reachability differs only under
closed-world native compilation. Both surface as native test failures *after*
metadata generation reported success — the agent can fail to produce the metadata
the native image actually needs.

The gate closes those gaps with **runtime truth** rather than more inference. It
runs a real native image with metadata tracing enabled, records what the
execution actually touches, and re-supplies it until the binary stops missing
metadata. It is what proves a coordinate's test binary passes on Native Image,
and every workflow ends on it (§AR-forge-workflow-engine.2,
§AR-native-test-verification-callers). Native Image must always work, so `FAILED`
is a hard error: the caller returns a failure status and resets its branch to its
checkpoint.

### 1. Observe, record, rebuild

Two native-image behaviors cooperate in a single execution. **Exact reachability
metadata with exit-on-miss** makes the image treat any access not backed by
supplied metadata as a hard miss rather than silently registering it: the binary
exits `ExitStatus.MISSING_METADATA` (172) and prints the exact missing entry the
instant it hits one. **Metadata tracing support** makes that same execution write
what it observed into a per-cycle trace directory.

So one run both detects a miss and records it. Feeding that directory into the
next build makes the next cycle aware of the previously missed access. Iterating
walks the metadata from the agent's approximation to the exact set the image
requires; exit `0` means nothing missed.

Recovery is therefore ordered: **JVM agent, then native tracing, then Codex.**
Tracing must not run before the agent step. Only when the loop still cannot make
the binary pass does the residual failure go to Codex, because at that point it
is evidence of a code or test defect rather than a metadata gap. Pi is never
invoked — its role elsewhere is to remove failing tests, exactly the wrong move
when the failure is a real defect that must surface.

### 2. Inputs and budget

The caller supplies the coordinate, the reachability repo path, and an absolute
staging root namespaced per class for the per-class caller and per coordinate
otherwise. Agent metadata is staged under `agent` and merged trace metadata under
`trace`; durable repository metadata is written only after the final merge
succeeds and the durable re-run passes.

Condition packages, when not supplied, are derived from the coordinate's
`user-code-filter.json` after the agent step has had a chance to refresh it,
excluding obvious generated-test packages, and fall back to the coordinate's
group. They follow the library's code, not its Maven coordinate: Maven groups are
frequently not Java package roots — an `org.apache.tomcat.embed` artifact executes
`org.apache.catalina`, `org.apache.coyote`, and `org.apache.tomcat` code — and
tracing needs a condition class that is actually on the access stack before the
access occurs.

The outer budget is the strategy parameter
`max-native-test-verification-iterations`, default 40
(§FS-predefined-strategy-parameter-families). Convergence is expected within a
handful of cycles; the default is a soft cap, not a target, and each cycle
rebuilds the image, so wall-clock cost is dominated by build time. A per-cycle
timeout, default 30 minutes, caps the preflight test invocation and each trace
cycle; a timeout is treated as a non-zero exit.

### 3. The loop

The staging root and its per-cycle runs directory are reset at the start of every
invocation, so no stale entry leaks across runs.

The agent step runs first, always. If it fails, tracing starts with no accepted
trace directories. If it succeeds, the coordinate is tested against the staged
agent metadata; a failure *before* the native tests is a code or test problem and
goes straight to Codex, and a native-test failure enters the trace loop.

**A pass must survive finalization.** When the coordinate passes against staged
metadata, the gate merges it into durable repository metadata and re-runs the
tests *without* staged directories. Only that durable re-run returns `PASSED`;
otherwise the finalized metadata goes to Codex. This catches merge-time condition
invalidation, where raw staged metadata passes but the merged result leaves an
access behind an unsatisfied condition. A trace-backed pass takes the same route:
merge accepted trace directories, merge into durable metadata, re-run.

```mermaid
flowchart TD
    Agent[Generate agent metadata] -- fail --> Outer
    Agent -- pass --> Test[Test against staged agent metadata]
    Test -- pass --> FinalA[Merge into durable metadata]
    FinalA --> DurA[Re-run without staged dirs]
    DurA -- pass --> PassA([PASSED])
    DurA -- fail --> Codex
    Test -- fails before native tests --> Codex
    Test -- native tests fail --> Outer{budget left?}
    Outer -- no --> Codex
    Outer -- yes --> Run[One trace cycle]
    Run --> Route{exit code}
    Route -- 0 --> MergeT[Merge accepted trace dirs] --> FinalA
    Route -- "172, new entries" --> Accept[Accept dir] --> Outer
    Route -- "172, none" --> Codex
    Route -- other --> Codex
    Codex[Codex] --> Ok{converged?}
    Ok -- yes --> PassI([PASSED_WITH_INTERVENTION])
    Ok -- no --> Fail([FAILED])
```

Each trace cycle is one Gradle invocation, rebuilding with every accepted
directory so far, and routes on the binary's exit code:

| Exit | Meaning | Action |
| --- | --- | --- |
| `0` | accumulated metadata and the code under test are sufficient | merge, finalize, return `PASSED` |
| `172`, new entries | the cycle captured a missing access | accept the directory, continue |
| `172`, no new entries | tracing stalled: the run reported a miss it did not record | open a Native Image tracing ticket, route to Codex |
| other non-zero | the code is broken in a way more metadata cannot fix | route to Codex |

A `172` with no new entries is a Native Image tracing defect, not a library
metadata gap — the run already proved the access happens and named it. The gate
prints accumulated progress and the failure-log tail so the defect is reproducible
upstream, then falls back so the run still makes progress.

**Inactive conditions are too-late conditions.** When GraalVM reports that
metadata for an access exists but is inactive because its runtime conditions were
not satisfied, the repair must move or duplicate that metadata under a condition
reached *before* the access — usually inferred from the library frame performing
it. Reusing the unsatisfied condition is invalid: it preserves the same timing
failure.

The result carries the status (`PASSED`, `PASSED_WITH_INTERVENTION`, or
`FAILED`), the staging root, the cycles consumed, the ordered trace directories
that produced 172, at most one intervention record, and the last relevant Gradle
log path with the binary's parsed exit code — the log path is **required** when
the status is `FAILED`, and callers surface it in run metrics and the PR body
(§FS-forge-run-metrics).

### 4. Codex, and the one carve-out

Codex is terminal when invoked. It has full repository write access through the
`fix-missing-reachability-metadata` skill, validates its own work, and the gate
does not re-verify or re-run a trace cycle afterwards — that would produce a
Codex/verify ping-pong on a real code defect. Its exit decides between
`PASSED_WITH_INTERVENTION` and `FAILED`.

Everything reaching Codex — exhausted budget (reason `metadata-gap-exhausted`),
stalled progress, trace timeouts, non-172 failures — arrives with a reproduction
command including the accepted trace directories. The Codex process and its
instructions must pin the GraalVM and Java homes and the full native-image version
to the exact distribution the failed command used, and must fail rather than
reproduce or verify against a different installation. Accumulated directories are
not discarded before Codex runs; its fixes are additive.

**Final merge failures are the only carve-out.** If the trace-output merge or the
final durable merge fails, the gate returns `FAILED` directly without invoking
Codex. These are infrastructure problems downstream of a successful validation
path — a merge task, a filesystem write, malformed metadata input — and Codex
cannot repair the metadata pipeline itself. Every other failure mode routes
through Codex first.

### 5. Gradle task contract

Tracing shells out **only** to the Gradle wrapper; it must never invoke
`native-image` or `native-image-utils` directly. Rebuilding inside the loop is the
point: metadata collected by an earlier pass unlocks code paths only later builds
reach. The reachability repo must provide three tasks (§root/AR-test-harness);
which flags they pass and where they put the binary is a Gradle-side concern.

| Task | Properties | Contract |
| --- | --- | --- |
| `nativeTraceImage` | `-Pcoordinates` (required), `-PmetadataConfigDirs` (optional, → `-H:ConfigurationFileDirectories`) | Adds `-H:+MetadataTracingSupport`. Produces a binary at a path derivable from the coordinate alone. Non-zero exit is a build failure. |
| `runNativeTraceImage` | `-Pcoordinates`, `-PtraceMetadataPath` (→ `-XX:TraceMetadata=path=…`, fresh per cycle), `-PtraceMetadataConditionPackages` (→ `-XX:TraceMetadataConditionPackages`) all required; `-PmetadataConfigDirs` optional | Adds `-H:+MetadataTracingSupport` so the tracer writes at runtime, *and* `--exact-reachability-metadata` with `-H:MissingRegistrationReportingMode=Exit` so the reporter exits 172 on a miss. No caller-supplied program arguments. Runs with `ignoreExitValue=true`; the gate recovers the real code from Gradle's `finished with non-zero exit value N` line. |
| `mergeNativeTraceMetadata` | `-PinputDirs`, `-PoutputDir` (contents replaced) | Wraps `native-image-utils generate` and is the **only** point at which that tool is invoked. |

**The tracer must record whatever the reporter reports missing.** When the
reporter prints a missing entry, the same invocation must add the equivalent entry
to the directory named by `-PtraceMetadataPath`, with the metadata kind, condition
package, and target identity matching what the merge later consumes — otherwise
the loop has not produced a trustworthy runtime-observed signal and the gate falls
back rather than converging.

Toolchain configuration — GraalVM home, Java home, the native-image binary,
`native-image-utils` — is the reachability repo's responsibility; the gate does
not export, override, or check it beyond what Gradle requires. All three tasks
must accept `--no-daemon` and be idempotent across invocations on the same
coordinate, so a rerun is possible after the gate exits whatever its status.

The gate is composed from these tasks and owns no domain logic of its own. It
must not depend on any workflow implementation or on the post-generation
intervention lane, so that every caller gets identical behavior.

## FS-local-branch-review: Local pre-push branch review

Every generated branch except a `code-coverage-improvement` or
`code-coverage-benchmark-result` branch must receive its authoritative semantic
review before it is pushed (§FS-automated-pr-review). The verified working tree,
local gate records, and resolved render statistics are still available at this
point, so a contribution-local finding can be corrected before publication.
The shared publication pipeline implements this phase before it writes the
descriptor. Coverage and benchmark publication remain deterministic descriptor
routes rather than semantic code-review subjects.

**Placement.** The review runs inside publication, after the pre-publication
gate of §FS-local-ci-equivalent-verification.2 has passed and the descriptor
input has resolved, and before the descriptor is written. It judges
`base_ref..HEAD`, the eventual pull-request diff minus the descriptor commit,
with the local evidence that repository CI does not provide.

**Isolation and authority.** The review runs cold in a worktree detached at the
verified commit and in a session carrying no generation transcript. Forge invokes
the worker-configured analysis role (§FS-forge-agent-runtime-selection), requests
`xhigh` reasoning, supplies the prompt and evidence, and owns execution and
logging. The reviewer applies the task's label-specific rules and the first
matching disposition in §root/FS-contribution-contract.5. Its prompt enumerates
the mutation-producing finalization duties and requires the reviewer to run them
after its last edit, repair their failures inside the contribution, and write the
verdict only for that stable finalized tree. This is the ordinary generated
branch's one semantic review; neither local verification nor the published-PR
process launches another general repair or review agent after that verdict.

**Outcomes.** The review has exactly two decisions:

- `approved`, with no `action`;
- `rejected`, with exactly one `action`: `human-intervention` or `close`.

Publication proceeds for either decision. Even a close disposition first creates
the pull request that records the outcome. A reviewer that cannot be reached
because of authentication, non-zero exit, timeout, or an unreadable verdict
becomes the fixed `rejected` plus `human-intervention` outage outcome; it is
not a third status or a failed publication.

**The disposition ladder is mandatory.** A contribution-local violation is
repaired in the detached worktree and approved only when the resulting tree
satisfies the review rules. A shared infrastructure defect is not repaired on
the contribution: the reviewer returns structured evidence, Forge opens or
reuses one issue for that defect, and the descriptor records `rejected` plus
`human-intervention`. Unsupported library behavior meeting
§root/FS-contribution-contract.5.4 records `rejected` plus `close`. Every
other unresolved or uncertain finding records `rejected` plus
`human-intervention`.

**Findings record.** Every finding is appended to tracked
`forge/FINDINGS.md`, including one the reviewer fixed and one left open. Forge
renders each entry from the returned title and body, so its shape cannot drift;
an unavailable reviewer receives a fixed outage title and body.
`forge/FINDINGS.md` is expected publication output and must not itself trigger
repository-level-change classification.

**Verdict data.** The reviewer writes one structured verdict to the path Forge
supplies. It carries `decision`, conditional `action`, a review comment,
finding title and body, and a fix note when a repair was made. An infrastructure
disposition also carries the structured defect evidence needed to find or create
its issue. The descriptor retains those words, model and session provenance, and
changed-path evidence. Repair is work completed before the decision, not a
descriptor state; `changes_requested`, `repaired`, `repair_reverted`, and
`published_tree` are not control-flow outcomes.

**Forge verifies edits.** Forge derives the changed paths and owns staging and
commits. A changed tree replays finalization
(§FS-local-ci-equivalent-verification.1) and the pre-publication gate
(§FS-local-ci-equivalent-verification.2) with every secondary agent repair
disabled; an unchanged tree does not. This replay is verification, not another
mutation phase: the head and worktree must remain byte-for-byte equal to the
reviewed commit. If either gate fails or changes that tree, Forge restores the
last verified tree, records the failed attempt in the finding, and publishes
that exact tree as `rejected` with the action selected by the disposition
ladder. No edit made after the verdict may enter the published head, and an
approval of a discarded tree must never describe it.

**Durability and rendering.** The review prompt, response, and repair pass are
durable task logs (§FS-durable-generation-logs). Its in-flight verdict is staged
with pending publication metrics so continuation reuses it. The final verdict
is a first-class descriptor field rendered in the pull-request body's Local
Agent Review section. It is never folded into another modifier, and publication
may derive a review label only from its exact decision/action pair.

The descriptor is validated by the publisher against the schema on the default
branch (§AR-actions-publication), which admits no unknown fields, so the schema,
renderer, and executor must land before a run emits the new contract.

## FS-library-update-tested-version-split: Library-update tested-version split

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
through the `Refs:` line and `Forge-Unblocks-Issue:` trailer of §AR-pr-body.
Once the PR merges, Forge releases the issue — clearing its assignees and moving
its project status to `Todo` — so the successor update enters normal processing.

## FS-human-intervention-policy: Human intervention policy

The `human-intervention` label is a maintainer follow-up signal, not a generic
failure label. Forge must apply it only when the available evidence shows that
the work cannot be safely completed or trusted without human judgment about the
generated code, repository automation, metadata, or library behavior.

For a published generated pull request every such case must be represented by
`local_review.decision: rejected` and
`local_review.action: human-intervention`; no other descriptor flag or inferred
condition may add the label. Valid cases include:

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
- The pre-push review found something it could not correct, or its repair did
  not survive finalization and the gate, so the branch publishes with the
  finding still open (§FS-local-branch-review).
- A CI-repair review reached the escalation of the disposition ladder: a rule
  violation it could not repair inside the contribution's file set, or a defect
  in shared repository infrastructure that the contribution must not carry and
  that now has its own issue (§root/FS-contribution-contract.5).

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


## FS-automated-pr-review: Automated pull request review

Forge's published-PR process is the deterministic executor of the authoritative
pre-push decision in §FS-local-branch-review. It never launches a second general
semantic reviewer. Before any approval, rejection action, repair, or merge, it
must load the publication descriptor from the pull request's exact current head
and apply the same schema and publication trust checks as the trusted publisher.
Missing, malformed, stale, wrong-repository, wrong-branch, or unsupported
descriptor state makes the pull request ineligible for automation.

Eligibility is structural, not presentational. The head repository must be
`oracle/graalvm-reachability-metadata`, the head branch must be upstream
`ai/**`, the pull request must resolve to the trusted Forge publication identity,
and the validated descriptor must name a supported generated task or benchmark
publication. A title marker, branch name, or label alone is insufficient.

**Approved heads are approved deterministically.** For a normal generated task,
`local_review.decision` must be `approved`; a validated benchmark-result
descriptor is approved by definition because it records measurements rather
than a mergeable generated contribution. Forge submits the GitHub approval with
an explicit commit ID equal to the validated head and never carries that
approval across a later push. Pending required checks wait. A head merges only
after every required CI and repository merge gate is successful and
non-blocking.

**Rejected heads are acted on immediately.** A rejected descriptor never
receives an approval and does not wait for CI. Action `human-intervention`
ensures the label is present and leaves the pull request open. Action `close`
posts one idempotent explanation using the recorded reviewer reason, closes the
pull request, labels the linked issue `library-unsupported-version`, and closes
that issue. The trusted publisher performs this immediately after PR creation;
the PR-review process may reconcile the same end state idempotently.

**Failed CI enters repair only after deterministic retries.** For each failed
GitHub Actions workflow on an approved current head, Forge reruns failed jobs
while `run_attempt` is below `3`. If required CI still fails after that budget,
Forge invokes the worker-configured analysis role as a CI-repair reviewer in an
isolated worktree. The turn receives the failed-check evidence and exact-head
descriptor, diagnoses the failure, and applies §root/FS-contribution-contract.5:
repair only contribution-local defects, return structured evidence for shared
infrastructure defects, close only for the supported unfixable-library case,
and escalate everything else.

A contribution-local repair performs the same local-review responsibilities on
the resulting tree, appends every new finding to `forge/FINDINGS.md`, updates
the descriptor so its decision describes that exact tree, and pushes to the
existing head branch. Structured infrastructure evidence causes trusted Forge
code to open or reuse one infrastructure issue and link it before recording
`rejected` plus `human-intervention`; an unfixable library records `rejected`
plus `close`. Any case that cannot be fixed by changing the contribution is
explained on the pull request. A push restarts CI and invalidates prior approval;
every later action begins again from the new exact-head descriptor.

Transient CI noise, GitHub status/API failures, Maven download failures, and
other external infrastructure errors are retried or waited out and are not
converted into `human-intervention` (§FS-human-intervention-policy).

A PR labeled `human-intervention-fixed` is the explicit maintainer signal that
manual follow-up has been completed. Forge may dismiss stale requested-changes
reviews left by the previous process and resume deterministic approval and merge
handling after the normal gates pass, without reviving the general review agent.

**A conflict that needs no judgment is resolved, not escalated.** A pull request
whose only merge conflict is the shared findings ledger must not be left for a
maintainer. Because every branch records its finding at the same offset in the
append-only `forge/FINDINGS.md` (§FS-local-branch-review), any two open pull
requests conflict there, and each merge re-conflicts the rest; keeping both
entries is the only correct resolution, so the repository configures git to
take it without asking. Conflict refresh is deterministic queue maintenance,
not review: before CI state can make a pull request eligible for an agent,
Forge must merge the base branch into a conflicting same-repository head and
push the result when that merge left no conflict behind. A merge that still
conflicts — in the ledger or in any other file — is a real disagreement over
content and takes the human-intervention path instead, as does a head Forge
cannot push to. Pushing restarts the pull request's checks, so review and merge
belong to a later pass: Forge must re-read the review decision and checks after
pushing rather than carrying pre-push state forward, which also means an
approval dismissed by the push is re-earned by the normal review path rather
than assumed.
