---
name: review-fixes-native-image-run-fail
description: Review pull requests with the `fixes-native-image-run-fail` label in graalvm-reachability-metadata. Use when asked to review or triage a PR that fixes a native-image runtime failure for an existing library version update. Focus on validating the runtime/native fix, keeping the diff scoped, and ensuring dynamic-access coverage does not drop between the previously tested version and the new version.
argument-hint: "[pr-number-or-url]"
---

# Review `fixes-native-image-run-fail` PRs

These PRs repair metadata or tests after an existing library version compiles and builds a native image, but the native image fails when the test runs. Review them more lightly than `library-new-request` PRs: the library is already supported, so the goal is to restore native-image behavior for a newer version while preserving existing dynamic-access coverage.

The PR number or URL can be passed as an optional argument (for example, `1234`, `https://github.com/oracle/graalvm-reachability-metadata/pull/1234`). If the user says "review this PR" without an argument, infer the PR from the surrounding conversation or `gh pr status`; only ask the user when it cannot be inferred. Use `gh pr view <pr>`, `gh pr diff <pr>`, and `gh pr checks <pr>` against the resolved PR throughout the workflow below.

## Review Principles

- Confirm the PR has label `fixes-native-image-run-fail`.
- Expect targeted metadata, resource, proxy, serialization, JNI, initialization, or test adjustments that make the native executable run successfully for the new version.
- Be more relaxed than `library-new-request`: do not reject only because a test is inherited from older support, uses compatibility branches, or keeps an existing package layout.
- Do not accept fixes that hide the failing native path by skipping assertions, skipping native-image runtime execution, or weakening the test until the failure disappears.
- Treat dynamic-access coverage preservation as the main quality gate. The new version should not report lower dynamic-access coverage than the previously tested version unless the PR gives a concrete, credible reason.
- For numeric gates, compare the reported evidence as-is. Do not inspect generation filters, agent configuration, or metadata contents to second-guess why dynamic-access or metadata-count numbers are what they are.
- Compare total metadata entry counts between the previous metadata version and the new metadata version only as a severe-drop guardrail, using the counts reported in the PR description. Report metadata entry count issues only when the PR-reported new total metadata entry count has fewer than 25% as many entries as the PR-reported original count.
- Prefer concrete evidence from native run output, generated metadata, stats, and CI over style objections.

## Workflow

1. Inspect the PR summary.
   - Resolve the target PR from the optional argument, or infer it from context when possible.
   - Confirm the PR has label `fixes-native-image-run-fail`.
   - Identify the target coordinate, the previous tested version, and the new tested version from the PR body, title, changed `index.json`, metadata path, and test path.
   - Gather files, reviews, inline comments, and CI checks.

2. Validate the diff scope.
   - Expected files are usually limited to:
     - `metadata/<group>/<artifact>/<version>/reachability-metadata.json`
     - `metadata/<group>/<artifact>/index.json`
     - `stats/<group>/<artifact>/<version>/stats.json`
     - `tests/src/<group>/<artifact>/<version>/**`
     - allowed Docker image entries or test resources only when the native test requires them
   - Treat generated test project files such as `.gitignore`, `build.gradle`, `gradle.properties`, `settings.gradle`, and `user-code-filter.json` as normal when they live under the target version's test directory.
   - Accept metadata additions that are necessary for the new upstream version.
   - Accept narrow test edits that keep the same behavior covered across old and new versions.
   - Be suspicious of unrelated build logic, workflows, generated sources, other libraries, or broad refactors.
   - Reject or request changes if the PR fixes the native run by disabling the failing behavior.

3. Review the native-image fix.
   - Confirm the metadata or test change matches the observed native runtime failure, such as missing reflection, resources, proxies, serialization constructors, JNI access, or class initialization behavior.
   - Metadata additions should be specific enough to the target library behavior; do not require hand-minimized entries when generated metadata is coherent and validation passes.
   - Test changes are acceptable when the upstream API or runtime behavior changed, but they must still exercise the native path that previously failed.
   - Do not require the stricter `library-new-request` rules about scaffold-only tests or test package placement unless the PR is also adding a new library.

4. Check dynamic-access coverage across versions.
   - Compare `stats/<group>/<artifact>/<old-metadata-version>/stats.json` and `stats/<group>/<artifact>/<new-metadata-version>/stats.json` when stats files are present in the PR or available on the branch.
   - Compare `dynamicAccess.coverageRatio`, `coveredCalls`, `totalCalls`, and the `dynamicAccess.breakdown` entries for reflection, resources, proxies, serialization, JNI, or any other present report type.
   - Do not use `user-code-filter.json`, agent configuration, or metadata file contents to argue that the reported dynamic-access values are not comparable. Use the stats values as reported unless the stats are missing or stale.
   - A lower `coverageRatio` or lower `coveredCalls` for the new version is blocking unless the total dynamic-access surface changed and the PR explains why the reduction is expected.
   - If metadata fixes make the native run pass but the dynamic-access ratio drops, ask for restored coverage first. Passing native execution is not enough by itself.
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
   - Expected minimum: native-image compile and native test execution are green for the target coordinate.
   - Metadata validation and Java tests should also pass for the changed coordinate.
   - If current-defaults and future-defaults lanes both run, both should pass unless the PR clearly targets only one failing lane and the other failure is unrelated infrastructure noise.
   - If CI is flaky but the diff and coverage comparison are sound, ask for a rerun instead of blocking on speculation.

## Decision Rules

Approve when all of these are true:

- The PR is scoped to the target existing library and the native-image runtime failure it fixes.
- The native-image runtime path still executes meaningful library behavior.
- Dynamic-access coverage does not drop between the previous and new tested versions, or any apparent drop is convincingly explained by a changed upstream API surface.
- Total metadata entry counts are not below the 25% severe-drop threshold, or the reduction is convincingly explained by a changed upstream API surface.
- Required metadata, Java, native-image compile, and native-image run checks are green.

Request changes when any of these are true:

- The fix makes native execution pass by skipping the failing native path, disabling assertions, or removing coverage.
- Dynamic-access coverage drops without a credible explanation and replacement coverage.
- Total metadata entry count drops below 25% of the original without a credible explanation.
- Metadata additions are unrelated to the failure or affect other libraries without justification.
- CI failures indicate the native-image runtime problem is not actually fixed.

Ask for follow-up instead of rejecting when:

- Stats needed for the old/new version comparison are missing or stale.
- CI failed in a way that looks like infrastructure noise.
- The API or native runtime change is plausible but the PR does not explain why a coverage drop is expected.
- Total metadata entry count drops below 25% of the original, but the changed upstream API surface makes the reduction plausible.

## Output Style

Keep comments short and factual:

- For coverage drops: say that dynamic-access coverage must not regress between tested versions, cite the old and new values, and ask for either restored coverage or a concrete explanation.
- For metadata entry drops: report only drops where the new total metadata has fewer than 25% as many entries as the previous version's total metadata; cite the old and new counts, and ask for either restored metadata or a concrete explanation of the API/runtime-surface change.
- For native skips: say that the PR avoids the failing native path instead of fixing it, so it does not demonstrate native-image runtime coverage.
- For unrelated changes: say the PR should stay scoped to the `fixes-native-image-run-fail` repair and remove unrelated files.
- For missing stats: ask for regenerated library stats or CI evidence before approval.
