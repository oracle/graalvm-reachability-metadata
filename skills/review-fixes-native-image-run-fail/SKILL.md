---
name: review-fixes-native-image-run-fail
description: Review pull requests with the `fixes-native-image-run-fail` label in graalvm-reachability-metadata. Use when asked to review or triage a PR that fixes a native-image runtime failure for an existing library version update. Focus on validating the runtime/native fix, keeping the diff scoped, and applying the repair coverage gate to overall and breakdown dynamic-access reports.
---

# Review `fixes-native-image-run-fail` PRs

These PRs repair metadata or tests after an existing library version compiles and builds a native image, but the native image fails when the test runs. Review them more lightly than `library-new-request` PRs: the library is already supported, so the goal is to restore native-image behavior for a newer version while preserving existing dynamic-access coverage.

The rules below implement the contribution contract: block only on a concrete violation of a must — the shape limits (§FS-contribution-contract.2), the coverage gates (§FS-contribution-contract.3), the cheating patterns (§FS-contribution-contract.4), and the test contract's musts (§FS-test-contract) — while shoulds stay advisory in review (§FS-contribution-contract.1).

## Chunked dynamic-access PRs

When the PR has the `chunked-dynamic-access` label, skip every repair coverage
comparison in this skill, including for the final chunk. Request changes for
coverage only when the reported `dynamicAccess.coveredCalls` is `0`. Keep every
non-coverage rule. §FS-contribution-contract.3

The PR number or URL can be passed as an optional argument (for example, `1234`, `https://github.com/oracle/graalvm-reachability-metadata/pull/1234`). If the user says "review this PR" without an argument, infer the PR from the surrounding conversation or `gh pr status`; only ask the user when it cannot be inferred. Use `gh pr view <pr>`, `gh pr diff <pr>`, and `gh pr checks <pr>` against the resolved PR throughout the workflow below.

## Review Principles

- Confirm the PR has label `fixes-native-image-run-fail`.
- Expect targeted metadata, resource, proxy, serialization, JNI, initialization, or test adjustments that make the native executable run successfully for the new version.
- Be more relaxed than `library-new-request`: do not reject only because a test is inherited from older support, uses compatibility branches, or keeps an existing package layout.
- Do not accept fixes that hide the failing native path by skipping assertions, skipping native-image runtime execution, or weakening the test until the failure disappears.
- Treat dynamic-access coverage preservation as the main quality gate. Apply the
  ordered repair comparison in the workflow below independently to the overall
  report and every breakdown present in either version.
- Use the reported stats evidence as-is. Do not inspect generation filters,
  agent configuration, or metadata contents to second-guess the reported
  `totalCalls`, `coveredCalls`, or coverage ratios.
- Treat metadata entry counts as telemetry only. Never request changes or apply
  `human-intervention` because the new version reports fewer metadata entries.
- A new version that reports zero total dynamic-access calls has no comparable
  call surface and passes the numeric coverage gate. The native-image runtime fix itself must still pass and must not be made
  green by skipping the native path or disabling native-image behavior.
- Accept only `reachability-metadata.json` files as metadata files. Reject legacy native-image metadata config files such as `reflect-config.json`, `resource-config.json`, `proxy-config.json`, `serialization-config.json`, `jni-config.json`, or `predefined-classes-config.json`.
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
   - Reject legacy native-image metadata config files. Metadata for generated support and test-only metadata must use `reachability-metadata.json`.
   - Reject or request changes if the PR fixes the native run by disabling the failing behavior.

3. Review the native-image fix.
   - Confirm the metadata or test change matches the observed native runtime failure, such as missing reflection, resources, proxies, serialization constructors, JNI access, or class initialization behavior.
   - Metadata additions should be specific enough to the target library behavior; do not require hand-minimized entries when generated metadata is coherent and validation passes.
   - Test changes are acceptable when the upstream API or runtime behavior changed, but they must still exercise the native path that previously failed.
   - Do not require the stricter `library-new-request` rules about scaffold-only tests or test package placement unless the PR is also adding a new library.

4. Check dynamic-access coverage across versions.
   - Compare `stats/<group>/<artifact>/<old-metadata-version>/stats.json` and
     `stats/<group>/<artifact>/<new-metadata-version>/stats.json` when stats are
     present in the PR or available on the branch.
   - Apply the following ordered comparison independently to the overall
     `dynamicAccess` report and every `dynamicAccess.breakdown` scope present in
     either version:
     1. Pass when the new scope reports `totalCalls == 0`.
     2. Fail when the new scope reports `totalCalls > 0` and `coveredCalls == 0`.
     3. Pass when new `coveredCalls` is at least old `coveredCalls`, or when the
        new coverage percentage is at least the old percentage.
     4. When both scopes report fewer than 10 `totalCalls`, fail only when old
        `coveredCalls - new coveredCalls > 2`.
     5. Otherwise, fail only when coverage drops by more than 20 percentage
        points.
   - Reach step 4 only after both covered calls and percentage decreased, so the
     subtraction is always positive. A failing scope is blocking unless the PR
     gives a concrete, credible explanation of the changed upstream surface.
   - For reference: `8/8 -> 7/9` and `8/8 -> 6/9` pass the small-report rule;
     `8/8 -> 5/9` fails it; `10/10 -> 15/20` passes because covered calls grew;
     and `20/20 -> 15/20` fails the large-report rule.
   - Do not compare metadata entry counts; they are telemetry, not a review gate.
   - If required old or new stats are missing or stale, ask for
     `generateLibraryStats` or the relevant CI stats job before approving.

5. Check CI before deciding.
   - Expected minimum: native-image compile and native test execution are green for the target coordinate.
   - Metadata validation and Java tests should also pass for the changed coordinate.
   - If current-defaults and future-defaults lanes both run, both should pass unless the PR clearly targets only one failing lane and the other failure is unrelated infrastructure noise.
   - If CI is flaky but the diff and coverage comparison are sound, ask for a rerun instead of blocking on speculation.

## Decision Rules

Approve when all of these are true:

- The PR is scoped to the target existing library and the native-image runtime failure it fixes.
- The native-image runtime path still executes meaningful library behavior.
- The overall report and every breakdown pass the ordered repair coverage gate, or any failing scope is convincingly explained by a changed upstream surface.
- Required metadata, Java, native-image compile, and native-image run checks are green.

Request changes when any of these are true:

- The fix makes native execution pass by skipping the failing native path, disabling assertions, or removing coverage.
- The overall report or a breakdown fails the ordered repair coverage gate without a credible explanation and replacement coverage.
- Metadata additions are unrelated to the failure or affect other libraries without justification.
- CI failures indicate the native-image runtime problem is not actually fixed.

Ask for follow-up instead of rejecting when:

- Stats needed for the old/new version comparison are missing or stale.
- CI failed in a way that looks like infrastructure noise.
- A failing repair-coverage scope may reflect a plausible upstream API or runtime change, but the PR does not explain it.

## Output Style

Keep comments short and factual:

- For coverage failures: name the failing overall or breakdown scope, report old
  and new `coveredCalls`, `totalCalls`, and percentages, and identify whether it
  failed the zero-covered, small-report, or large-report rule. Ask for restored
  coverage or a concrete explanation. Do not report a change that an earlier
  step of the ordered gate accepts.
- For native skips: say that the PR avoids the failing native path instead of fixing it, so it does not demonstrate native-image runtime coverage.
- For unrelated changes: say the PR should stay scoped to the `fixes-native-image-run-fail` repair and remove unrelated files.
- For legacy metadata files: say that metadata must use `reachability-metadata.json` and ask for old config files such as `reflect-config.json` or `resource-config.json` to be replaced.
- For missing stats: ask for regenerated library stats or CI evidence before approval.
