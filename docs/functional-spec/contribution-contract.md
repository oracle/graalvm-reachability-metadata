# FS-contribution-contract: Test and metadata contribution contract

A contribution is one test project plus the metadata it justifies, delivered as
a single pull request. This contract defines what a reviewer — human or
automated (§forge/FS-automated-pr-review) — may block on. It binds every
contribution equally, whether a human wrote it or Forge generated it, so that
each shipped metadata entry stays justified by a test that would fail without
it (§GOAL-tested-metadata) and coverage grows without weakening what already
ships (§GOAL-protect-shipped-metadata).

What a test must have, must not do, should do, and how it behaves under Native
Image is normative in the test contract (§FS-test-contract), which makes
§FS-repository-functional-spec.5.2 concrete for test generation; metadata
content is normative in §FS-repository-functional-spec.5.1. This contract does
not restate those rules. It adds only what review needs on top of them: the
strength of each rule, the contribution-shape limits that can be checked
algorithmically, the coverage gates, the enumerated ways a contribution can
cheat, and what a reviewer must do about each of them. Review skills and review
automation implement this contract and must not contradict it.

## 1. Rule strength

Every rule is either a **must** or a **should**:

- A **must** holds unconditionally. A concrete must violation blocks the
  contribution: the violation is repaired, not argued around, and
  §FS-contribution-contract.5 decides who repairs it. The musts are
  §FS-test-contract.1 (what a test must have),
  §FS-test-contract.2 (what it must not do), §FS-test-contract.4 (the Native
  Image execution contract), and sections 2–4 of this contract.
- A **should** guides generation and repair. The shoulds are
  §FS-test-contract.3 (what a test should do). In review they are advisory: a
  reviewer may mention a should violation but must not block on it, must not
  repair on it, and automated review must not request changes for one.

Reviewers block only on a concrete violation of an enumerated must — never on
self-formed judgments of test quality, test depth, scope taste, or "end-user
behavior" that no enumerated rule backs. In particular, a test that exercises
the library's own types — including relocated or shaded types that ship in the
library JAR — satisfies the public-API requirement; a reviewer must not demand
a different entry point.

## 2. Contribution shape

These limits are mechanical facts about the diff. They are musts, and they are
the preferred place to grow algorithmic enforcement: anything checkable from
file paths and machine-produced numbers is validated by tooling, with review as
the backstop (§PRCPL-prefer-algorithmic).

- **One library, one version.** A `library-new-request`,
  `library-update-request`, or `fixes-*` contribution targets exactly one
  library coordinate and one new tested version, plus its supporting files.
- **Closed file set.** Changes stay inside the target coordinate's
  directories: `metadata/<group>/<artifact>/<version>/reachability-metadata.json`,
  `metadata/<group>/<artifact>/index.json`,
  `stats/<group>/<artifact>/<version>/` (stats and execution metrics), and
  `tests/src/<group>/<artifact>/<version>/`, plus an allowed-Docker-image
  entry only when the test requires it. A Forge-generated contribution may also
  update `forge/FINDINGS.md` when it records that contribution's local review
  finding (§forge/FS-local-branch-review). No other Forge path belongs in the
  contribution. Build logic, workflows, other coordinates, and generated
  sources outside the target test directory do not belong in the contribution.
- **Single metadata format.** The only accepted metadata file is
  `reachability-metadata.json`. Legacy split-config files (`reflect-config.json`,
  `resource-config.json`, `proxy-config.json`, `serialization-config.json`,
  `jni-config.json`, `predefined-classes-config.json`) are rejected wherever
  they appear, including as test-only metadata.
- **Index integrity.** `index.json` changes keep tested versions in the
  correct metadata-version bucket, without duplicates, with exactly one
  `latest` entry (§FS-repository-functional-spec.5.1), and must pass index
  validation against current master before merge.

## 3. Coverage and metadata gates

Coverage evidence is compared per label. New-library and library-update gates
use percentages. The `fixes-*` repair gate also uses covered-call counts so a
larger or equally covered dynamic-access surface is not rejected only because
its percentage is lower. The gates are musts; a breach blocks unless the
contribution gives a concrete, credible explanation such as a changed upstream
API surface.

| Label | Gate |
| --- | --- |
| `library-new-request` | Dynamic-access coverage above 20% when the report has calls to cover. |
| `library-update-request` | Coverage percentage does not drop at all against the previously supported version. |
| `fixes-javac-fail`, `fixes-java-run-fail`, `fixes-native-image-run-fail` | Each overall or breakdown scope passes the repair comparison below. |

For the `fixes-*` labels, apply this ordered comparison independently to the
overall dynamic-access report and every breakdown present in either version.
Treat `N/A` on either side of a comparison as `0/0`: zero total calls and zero
covered calls.

1. A new scope with no calls passes because no comparable call surface remains.
2. A new scope with calls but zero covered calls fails.
3. A scope passes when its new covered-call count is at least its previous
   covered-call count, or when its new coverage percentage is at least its
   previous percentage.
4. When both the previous and new scope report fewer than 10 total calls, the
   scope fails only when the previous covered-call count exceeds the new count
   by more than two.
5. Every other scope fails only when its coverage drops by more than 20
   percentage points.

The order is part of the rule: step 4 is reached only when both the covered-call
count and percentage decreased, so its subtraction is always positive.

When a scope fails, the reviewer must investigate why before deciding. The
analysis uses available evidence such as the old and new stats, test diff,
upstream API or runtime changes, and CI output. The review states the supported
cause; when the evidence does not establish one, it states that the cause is
unknown and escalates under §FS-contribution-contract.5.5 rather than deciding
on a guess. A reviewer must not present speculation as the cause of a
regression.

PRs with the `chunked-dynamic-access` label are exempt from every percentage
and repair-comparison gate in this section, including the final chunk. A chunk
covers only part of the library-wide dynamic-access report
(§forge/FS-forge-chunked-dynamic-access), so its coverage gate fails only when
the reported `dynamicAccess.coveredCalls` is zero. This exception does not relax
any non-coverage rule or guardrail below.

One guardrail accompanies the gates:

- **Entry-count mismatch.** For new libraries, a covered dynamic-access call
  count at least 75% higher than the PR-reported metadata entry total (library
  plus test-only) requires investigation before approval.

Metadata entry-count changes are telemetry only for `fixes-*` reviews and never
block a repair contribution. Gates are computed from the reported evidence
as-is: the stats files and the PR-description counts. Reviewers do not
hand-count metadata entries, and do not second-guess the numbers through
`user-code-filter.json`, agent configuration, or metadata file contents.
Coverage numbers are a minimum gate, not proof of completeness — high coverage
alone does not show the metadata is complete or necessary. When the tested
version reports zero dynamic-access calls, the entry-count mismatch probe and
depth-of-coverage objections carry no signal and are not applied. That leniency
covers only the numeric evidence: the Native Image execution contract and any
issue-requested metadata gate still apply.

## 4. Cheating caught in review

A contribution cheats when its evidence — a green test, a coverage number, a
metadata entry — does not mean what it claims. Each pattern below is a must
violation; review states the concrete pattern found, blocks, and takes the
disposition §FS-contribution-contract.5 assigns it. Where a pattern violates a
test-contract point, the cited point is the rule; this section fixes the review
bar for detecting it.

1. **Scaffold-only test** (§FS-test-contract.1.3). The review bar is exact:
   a test is "scaffold" only when its body is still the unmodified placeholder
   from the TCK scaffold templates
   (`tests/tck-build-logic/src/main/resources/scaffold/Test.*.template`). Once
   the placeholder body is replaced with code that actually invokes the target
   library, the test is not a scaffold, and a reviewer must not stretch the
   term to a test that merely looks thin — thinness is judged only by the
   gates of section 3.
2. **Native Image dodging** (§FS-test-contract.4.1, §FS-test-contract.4.2).
   The test skips Native Image execution or tolerates its failures. A bare
   `catch (Error)` without the `isUnsupportedFeatureError` verification, and
   the sanctioned pattern applied outside genuine open-ended dynamic class
   loading (§FS-test-contract.4.3), are the same violation.
3. **Fake requested-metadata coverage** (§FS-test-contract.2.4). Metadata a
   reporter asked for is "exercised" by the test's own dynamic access instead
   of the library's: direct reflection against the metadata target, no-op
   class literals, a bare `ClassLoader.getResource` existence check, or
   test-only code that bypasses the library path. Requested metadata must be
   reached through the library's public API — library code that reflects,
   loads the resource, proxies, serializes, or crosses JNI
   (§FS-test-contract.1.5).
4. **Coverage bought by asserting breakage** (§FS-test-contract.2.6). A
   dynamic-access call site is made "covered" by asserting a known bug,
   regression, or version-specific failure of the target artifact.
5. **Version-pinned test** (§FS-test-contract.2.5) — unless the version check
   is itself the behavior under test.
6. **Visibility bypass** (§FS-test-contract.2.2). The test lives in the
   library's own package to reach package-private or internal code, proving
   access no consumer has.
7. **Compiling against fiction** (§FS-test-contract.2.3). Source stubs, fake
   replacements, or shadow classes for library or dependency types in their
   real packages let the code compile without the real API.
8. **Fix by weakening** (§FS-test-contract.2.9). A compile or runtime repair
   passes by deleting tests, disabling test classes, removing assertions,
   swallowing the failing exception, or simplifying the test to triviality,
   instead of adapting it to the changed API while keeping the same behavior
   covered.
9. **Condition cheating.** A metadata entry's `typeReached` condition names a
   type that is not reached before the dynamic access occurs — a later or
   merely related class — so the entry never activates when needed or
   activates too broadly. Preferring the narrowest condition is a should
   (§FS-test-contract.3.5); an *invalid* condition is this must violation.

Review scrutiny is label-scoped: the `fixes-*` repair labels do not re-apply
the new-library bars for scaffold history or package placement to inherited
baseline tests — compatibility branches and existing layouts stay acceptable —
but every pattern above remains blocking wherever it appears in the changed
code.

## 5. Review disposition

A review is not finished when it has a verdict. Every review ends in exactly
one disposition, chosen by the first case below that applies. The ladder exists
because a generated contribution has no author waiting to answer a review: the
automation that opened the pull request has moved on, so a finding left on it
stalls the queue instead of resolving it (§forge/FS-automated-pr-review).
Whoever reviews is the party that acts.

The dispositions are approve, repair, close, and escalate. Approval means every
must in this contract holds on the tree that will merge — including after a
repair. Closing is available only where a rule in this contract says a
contribution must be closed, which today is §FS-contribution-contract.5.4 and
only in the case that rule leaves nothing to record; absent such a rule, a
contribution the reviewer cannot bring inside the rules escalates
(§FS-contribution-contract.5.5).

### 5.1 A violation inside the contribution is repaired, not reported

When the violation is in the contribution's own files and the reviewer can
bring it inside the rules, the reviewer makes the change on the pull request's
head and re-runs the gates that change affects. Recording the finding and
stopping is not a disposition for generated work: the pre-push review already
repairs what it finds (§forge/FS-local-branch-review), and the published-PR
review must not be weaker than the review that preceded it.

Requesting changes is a disposition only when a human author will act on it. On
a generated contribution it is §FS-contribution-contract.5.5 wearing a review's
clothes, and it must be recorded as such, so the pull request carries the
`human-intervention` label a maintainer triages rather than a request nobody
reads.

A repair is bound by every rule the contribution was bound by. In particular a
repair must not weaken the evidence to reach a green result: deleting tests,
disabling test classes, dropping assertions, swallowing the failing exception,
or reducing a test to triviality is the same violation whether the generator or
the reviewer commits it (§FS-test-contract.2.9). The review states what it
changed and why, so the repair is reviewable as a change and not only as an
outcome.

One removal is not a weakening: dropping a scenario that targets behavior
Native Image cannot support (§FS-test-contract.4.5) when the refusal cannot be
verified. Where none of the three proofs of §FS-test-contract.4.3.1 can be
obtained — the library catches GraalVM's `UnsupportedFeatureError` and leaves
nothing behind — the scenario can be neither tolerated nor made to pass, so
keeping it only turns a repairable contribution into an unexplained failure.
The sanctioned repair is the re-scope of §FS-test-contract.4.3.2: drop that
scenario and cover the rest of the library's public surface. Three conditions
bound it, and the review states all three. The unsupported mechanism is named
concretely, not inferred from a red test. The scenario's failure is genuinely
unverifiable; a refusal the helper can prove is tolerated with the helper
instead of deleted. And what remains still exercises the library's public API
and carries the metadata the contribution ships, with the gates of
§FS-contribution-contract.3 applied to the re-scoped result and not to the
tree the reviewer started from. When nothing survives the re-scope, the version
is unsupportable and the disposition is §FS-contribution-contract.5.4.

### 5.2 A repair stays inside the contribution's file set

The closed file set of §FS-contribution-contract.2 bounds the repair exactly as
it bounds the contribution. A fix that can only be made by editing build logic,
the test harness, workflows, the allowed-image scanning machinery — as distinct
from the single allowed-Docker-image entry §FS-contribution-contract.2 admits
when the test requires it — or another coordinate is not a repair; it is
§FS-contribution-contract.5.3.

The recurring form is a test that fails because of a defect in a shared Gradle
task or harness class. It is repaired by changing the test, or it is not
repaired here at all. Editing the shared code inside the contribution makes one
library's pull request carry a repository-wide behavior change through a review
scoped to one coordinate, and that change reaches master with no reviewer
having weighed its effect on every other library. The contribution's own
directories are the only place a reviewer's edit belongs.

### 5.3 A defect in shared infrastructure becomes its own issue

When the cause of a failure is in shared repository infrastructure — build
logic, the test harness, CI workflows, image scanning, or publication — the
contribution is not what is wrong, and nothing the reviewer does inside it can
be the fix. The reviewer, in order:

1. finds the open issue that already tracks the defect, or opens one describing
   the failure, its cause, and the evidence — one issue per defect, never one
   per pull request that met it;
2. comments on the pull request naming that issue and stating that the
   contribution is blocked on it rather than on its own content;
3. takes the escalation of §FS-contribution-contract.5.5 for the pull request
   itself.

An infrastructure defect is met by many contributions. Repairing it inside each
one hides how often it recurs, splits one change across several unrelated
diffs, and leaves the repository's shared code changed by whichever pull
request happened to arrive first. One issue per defect keeps the recurrence
visible and the fix reviewable on its own terms.

This case is a defect in the repository's own code, which is distinct from a
transient external failure such as a rate limit, a registry outage, or a
network error. A transient failure is retried or waited out, and becomes
neither an issue nor an escalation (§forge/FS-human-intervention-policy).

### 5.4 A library version Native Image cannot support is recorded as skipped

A library version is unsupportable when the dynamic access it performs is
reachable only through behavior Native Image does not support
(§FS-test-contract.4.5): the library guards that access itself, the guard fails
under Native Image whatever the metadata registers, and the library then falls
back or degrades. No metadata entry changes the outcome and no test can cover
the calls, so the coverage gates of §FS-contribution-contract.3 are unreachable
by construction rather than unmet by this attempt. Reflective access to JDK
internals that Native Image substitutes and withholds from reflection is the
recurring shape.

The evidence bar is the mechanism, not the number. The review names the
concrete unsupported behavior and shows the library's dynamic-access call sites
standing behind it. Coverage that is merely low, a test that is merely hard to
write, or a failure not yet diagnosed is §FS-contribution-contract.5.1 or
§FS-contribution-contract.5.5 — never this.

Where it holds, the version is recorded as unsupported rather than repaired or
escalated, because metadata no test can justify is not shipped
(§GOAL-tested-metadata). Recording has two halves and both are required.

The first half is the skip record. The reviewer adds a `skipped-versions` entry
to `metadata/<group>/<artifact>/index.json` for the unsupportable version and
for every newer version that shares the same unsupported behavior, each with a
`reason` naming the mechanism the review has already established. Skipping only
the reported version hands the same wall to the next one, because the
compatibility walker stops a library at its first failure
(§FS-library-version-update-automation.2). The skip is what makes the outcome
stick: a version that is closed but not skipped re-enters the candidate list and
the automation re-files the same issue on its next run
(§FS-library-version-update-automation.1).

The second half is the contribution. Where the artifact already has an index
entry — the `fixes-*` labels and `library-update-request` — the reviewer repairs
the contribution into that skip record: `metadata/<version>/`,
`tests/src/<version>/` and the mirrored `stats/<version>/stats.json` and
`stats/<version>/execution-metrics.json` are dropped, the skip entries are
written into `index.json`, and the result is approved.

What is dropped is fixed by what the stats gate checks. It validates the whole
repository rather than the diff, so metadata left without its mirrored stats
fails it and the two must go together; it also checks execution metrics against
tested versions, so metrics cannot stay for a version that will never be tested.
The publication descriptor `stats/<version>/forge-publication.json` is the
exception and stays: it is exempt from both checks, and it identifies the
contribution, so deleting it makes the publication unresolvable
(§forge/FS-forge-publication-readiness).

Where the artifact has no index entry — a new library that turns out to be
unsupportable — there is nothing to skip and nothing to merge, and the
contribution is closed.

Either way the linked issue ends labeled `library-unsupported-version` and
closed rather than returned to the work queue: an open request in `Todo` is
claimed again and spends the same budget on the same wall. On the closed path
the reviewer's disposition does both. On the skip path the merge closes the
issue through the contribution's link, and the label is already present whenever
the compatibility automation filed the issue
(§FS-library-version-update-automation.4); where it is absent it is applied
before the contribution merges.

Where a library change could remove the guard, the incompatibility is reported
upstream. This is the library-level counterpart of the test-level case in
§FS-test-contract.4.3.2.

### 5.5 Everything else is handed to a maintainer

A contribution the reviewer cannot bring inside the rules, and that no rule
condemns to §FS-contribution-contract.5.4, is labeled `human-intervention` and
commented on. It is not closed, and it is not approved. The comment states the
rule at issue, what the reviewer tried, and what it could not decide, so a
maintainer continues from the review instead of re-deriving it
(§forge/FS-human-intervention-policy).

Uncertainty is this case. A reviewer that cannot tell whether a must is
violated escalates instead of resolving the doubt in either direction:
approving an unclear contribution ships metadata that may be unjustified, and
closing one throws away work no rule condemns. Escalation is the only
disposition whose cost is bounded by a maintainer's attention.
