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
algorithmically, the coverage gates, and the enumerated ways a contribution can
cheat. Review skills and review automation implement this contract and must not
contradict it.

## 1. Rule strength

Every rule is either a **must** or a **should**:

- A **must** holds unconditionally. A concrete must violation blocks the
  contribution: review requests changes, and the violation is repaired, not
  argued around. The musts are §FS-test-contract.1 (what a test must have),
  §FS-test-contract.2 (what it must not do), §FS-test-contract.4 (the Native
  Image execution contract), and sections 2–4 of this contract.
- A **should** guides generation and repair. The shoulds are
  §FS-test-contract.3 (what a test should do). In review they are advisory: a
  reviewer may mention a should violation but must not block on it, and
  automated review must not request changes for one.

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
unknown and asks for an explanation. A reviewer must not present speculation as
the cause of a regression.

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
violation; review states the concrete pattern found and blocks. Where a
pattern violates a test-contract point, the cited point is the rule; this
section fixes the review bar for detecting it.

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
