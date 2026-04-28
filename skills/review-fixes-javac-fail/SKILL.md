---
name: review-fixes-javac-fail
description: Review pull requests with the `fixes-javac-fail` label in graalvm-reachability-metadata. Use when asked to review or triage a PR that fixes Java compilation failures for an existing library version update. Focus on validating the compile fix, keeping the diff scoped, and ensuring dynamic-access coverage does not drop between the previously tested version and the new version.
argument-hint: "[pr-number-or-url]"
---

# Review `fixes-javac-fail` PRs

These PRs repair test sources or test dependencies after an existing library is bumped and the new version no longer compiles with the old test code. Review them more lightly than `library-new-request` PRs: the library already exists, so the goal is to preserve working coverage across the version update rather than prove a brand-new metadata contribution from scratch.

The PR number or URL can be passed as an optional argument (for example, `1234`, `https://github.com/oracle/graalvm-reachability-metadata/pull/1234`). If the user says "review this PR" without an argument, infer the PR from the surrounding conversation or `gh pr status`; only ask the user when it cannot be inferred. Use `gh pr view <pr>`, `gh pr diff <pr>`, and `gh pr checks <pr>` against the resolved PR throughout the workflow below.

## Review Principles

- Confirm the PR has label `fixes-javac-fail`.
- Expect compile-focused changes plus the normal generated support files for the newly tested version: test source updates, imports, renamed APIs, dependency adjustments, a new metadata-version directory, stats, and metadata index changes.
- Be more relaxed than `library-new-request`: do not reject only because a test resembles older coverage, stays in an existing package layout, or contains compatibility branches for multiple supported versions.
- Do not accept changes that remove meaningful test coverage just to make `javac` pass.
- Treat dynamic-access coverage preservation as the main quality gate. The new version should not report lower dynamic-access coverage than the previously tested version unless the PR gives a concrete, credible reason.
- For numeric gates, compare the reported evidence as-is. Do not inspect generation filters, agent configuration, or metadata contents to second-guess why dynamic-access or metadata-count numbers are what they are.
- Compare total metadata entry counts between the previous metadata version and the new metadata version only as a severe-drop guardrail, using the counts reported in the PR description. Report metadata entry count issues only when the PR-reported new total metadata entry count has fewer than 25% as many entries as the PR-reported original count.
- Prefer small, targeted review comments. This label is for repair work, not a full redesign of historical tests.

## Workflow

1. Inspect the PR summary.
   - Resolve the target PR from the optional argument, or infer it from context when possible.
   - Confirm the PR has label `fixes-javac-fail`.
   - Identify the target coordinate, the previous tested version, and the new tested version from the PR body, title, changed `index.json`, and changed test path.
   - Gather files, reviews, inline comments, and CI checks.

2. Validate the diff scope.
   - Expected files are usually limited to the target coordinate's generated new-version support:
     - `metadata/<group>/<artifact>/<new-version>/reachability-metadata.json`
     - `metadata/<group>/<artifact>/index.json`
     - `stats/<group>/<artifact>/<new-version>/stats.json`
     - `tests/src/<group>/<artifact>/<new-version>/**`
   - Accept compatibility edits that keep one test source working across multiple tested versions.
   - Treat a new `reachability-metadata.json` for the tested version as normal for this label, including `{}` when validation and stats are coherent.
   - Treat generated test project files such as `.gitignore`, `build.gradle`, `gradle.properties`, `settings.gradle`, and `user-code-filter.json` as normal when they live under the target version's test directory.
   - Be suspicious of unrelated build logic, workflows, generated sources, other libraries, or broad refactors.
   - Reject or request changes if the PR removes tests, disables test classes, catches and ignores the failing exception.

3. Review the compile fix.
   - Confirm the edit addresses the actual Java compilation failure, such as renamed classes, changed method signatures, module boundaries, annotation processors, or dependency coordinates.
   - Compatibility branches are acceptable when they keep older and newer tested versions covered by the same test.
   - Version-specific logic is acceptable when the upstream API genuinely changed, but it should be narrow and documented by the code structure or assertions.
   - Do not require the stricter `library-new-request` rules about scaffold-only tests or test package placement unless the PR is also adding a new library.

4. Check dynamic-access coverage across versions.
   - Compare `stats/<group>/<artifact>/<old-metadata-version>/stats.json` and `stats/<group>/<artifact>/<new-metadata-version>/stats.json` when stats files are present in the PR or available on the branch.
   - Compare `dynamicAccess.coverageRatio`, `coveredCalls`, `totalCalls`, and the `dynamicAccess.breakdown` entries for reflection, resources, proxies, serialization, JNI, or any other present report type.
   - Do not use `user-code-filter.json`, agent configuration, or metadata file contents to argue that the reported dynamic-access values are not comparable. Use the stats values as reported unless the stats are missing or stale.
   - A lower `coverageRatio` or lower `coveredCalls` for the new version is blocking unless the total dynamic-access surface changed and the PR explains why the reduction is expected.
   - If `totalCalls` increases while `coveredCalls` stays flat or the ratio drops, ask for additional test coverage or metadata unless the uncovered calls are clearly outside the library behavior under test.
   - If stats are missing or stale, ask for `generateLibraryStats` or the relevant CI stats job before approving.

5. Compare metadata entry counts.
   - Compare total metadata entry counts for the previous metadata version and the new metadata version from the PR description, matching summary fields such as `Entries found` and `Previous library version metadata entries`.
   - Do not manually count entries from metadata files.
   - Do not use metadata file contents to argue that passing reported entry counts are incomplete.
   - Do not require an exact match. Differences are normal when upstream APIs move, generated metadata is cleaned up, or dynamic-access totals change.
   - Do not report metadata entry count issues unless the PR-reported new total metadata entry count is lower than 25% of the PR-reported original total metadata entry count.
   - When the new total is below 25% of the original and the tests and dynamic-access stats still claim comparable coverage, ask for restored metadata or a concrete explanation of the API/package change.
   - If the PR description does not report usable old and new metadata entry counts, do not infer them from metadata files; ask for refreshed PR summary evidence when the comparison is needed.

6. Check CI before deciding.
   - Expected minimum: `compileTestJava` or equivalent changed-metadata compile checks are green for the target coordinate.
   - Prefer seeing the full target `test` lane green because a compile fix can still reduce runtime or native coverage.
   - If current-defaults and future-defaults lanes both run, both should pass unless the PR clearly targets only one failing lane and the other failure is unrelated infrastructure noise.
   - If CI is flaky but the diff and coverage comparison are sound, ask for a rerun instead of blocking on speculation.

## Decision Rules

Approve when all of these are true:

- The PR is scoped to the target existing library and the compile failure it fixes.
- Tests still exercise the same meaningful library behavior after the compile repair.
- Dynamic-access coverage does not drop between the previous and new tested versions, or any apparent drop is convincingly explained by a changed upstream API surface.
- Total metadata entry counts are not below the 25% severe-drop threshold, or the reduction is convincingly explained by a changed upstream API surface.
- Required compile and metadata test checks are green.

Request changes when any of these are true:

- The fix makes compilation pass by weakening or bypassing the test instead of adapting it to the changed API while preserving meaningful coverage.
- Dynamic-access coverage drops without a credible explanation and replacement coverage.
- Total metadata entry count drops below 25% of the original without a credible explanation.
- CI failures indicate the compile problem is not actually fixed.

Ask for follow-up instead of rejecting when:

- Stats needed for the old/new version comparison are missing or stale.
- CI failed in a way that looks like infrastructure noise.
- The API change is plausible but the PR does not explain why a coverage drop is expected.
- Total metadata entry count drops below 25% of the original, but the changed upstream API surface makes the reduction plausible.

## Output Style

Keep comments short and factual:

- For coverage drops: say that dynamic-access coverage must not regress between tested versions, cite the old and new values, and ask for either restored coverage or a concrete explanation.
- For metadata entry drops: report only drops where the new total metadata has fewer than 25% as many entries as the previous version's total metadata; cite the old and new counts, and ask for either restored metadata or a concrete explanation of the API/runtime-surface change.
- For deleted coverage: say that the PR fixes compilation by removing coverage and should instead adapt the test to the new API.
- For unrelated changes: say the PR should stay scoped to the `fixes-javac-fail` repair and remove unrelated files.
- For missing stats: ask for regenerated library stats or CI evidence before approval.
