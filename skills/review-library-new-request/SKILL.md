---
name: review-library-new-request
description: Review pull requests with the `library-new-request` label in graalvm-reachability-metadata. Use when asked to review or triage a PR that adds metadata and tests for a new library, including PRs titled like `[GenAI] Add support for com.fasterxml:classmate:1.5.1 using gpt-5.4`. Focus on catching scaffold-only tests, version-pinned or package-bypassing tests, and PRs that push more than one library.
argument-hint: "[pr-number-or-url]"
---

# Review `library-new-request` PRs

These PRs usually add reachability metadata and tests for one target library coordinate. Review them with extra scrutiny.

The PR number or URL can be passed as an optional argument (for example, `1234`, `https://github.com/oracle/graalvm-reachability-metadata/pull/1234`). If the user says "review this PR" without an argument, infer the PR from the surrounding conversation (for example, an open review tab, a PR URL mentioned earlier, or the current branch's PR from `gh pr status`); only ask the user when it cannot be inferred. Use `gh pr view <pr>`, `gh pr diff <pr>`, and `gh pr checks <pr>` against the resolved PR throughout the workflow below.

## Historical Review Signals

Treat the following as hard review rules unless the PR provides a strong reason otherwise:

- Do not accept PRs that push more than one library. A `library-new-request` PR must stay scoped to one target library version plus its supporting test files.
- Do not accept scaffold-only tests. Generated tests must be changed into library-specific tests that exercise the behavior that requires the metadata.
- Do not accept tests that disable themselves under native image. If the added library behavior is guarded by checks such as `assumeFalse("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")))`, `if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")))`, or a similar native-only skip, then the PR does not provide native runtime coverage.
- Do not accept tests that reference the exact library version in test code or assertions unless the version check is itself the behavior under test. One test should remain valid across multiple supported library versions.
- Treat dynamic-access coverage counts as incomplete evidence. They can miss metadata required through downstream libraries, so do not reject a PR only because the exploded stats files under `stats/<group>/<artifact>/<metadata-version>/stats.json` report `0/0` dynamic-access calls while the PR adds metadata.

## Workflow

1. Inspect the PR summary.
   - Resolve the target PR from the optional argument, or infer it from context when the user just says "review this PR". Ask the user only if it cannot be inferred.
   - Confirm the PR has label `library-new-request`.
   - Read the title and body to identify the target coordinates.
   - Gather files, reviews, inline comments, and CI checks.

2. Validate the diff shape first.
   - Confirm there is exactly one target coordinate in the PR. Reject the PR if it adds metadata or tests for multiple libraries, even if the extra libraries look valid on their own.
   - Expected files are usually limited to:
     - `metadata/<group>/<artifact>/<version>/reachability-metadata.json`
     - `metadata/<group>/<artifact>/index.json`
     - `tests/src/<group>/<artifact>/<version>/**`
   - Be suspicious of changes to build logic, workflows, unrelated libraries, generated sources outside the target test directory, or wide refactors.
   - Treat extra `metadata/**` or `tests/src/**` trees for other coordinates as a blocking scope violation, not as a minor cleanup issue.

3. Review the test source.
   - The test must be library-specific, not a lightly edited scaffold.
   - Confirm the library behavior actually runs under native image. Reject tests that skip or short-circuit the relevant assertions in native mode through checks on `System.getProperty("org.graalvm.nativeimage.imagecode")`, early returns, or equivalent guards.
   - Reject tests that hardcode the exact library version in strings, assertions, or expected output unless the test is explicitly validating version-dependent behavior.
   - Keep test packages separate from library packages. Reject tests that live under the target library's package unless the PR clearly needs that package placement to exercise the library.
   - Separate packages matter because tests placed inside the library package can bypass visibility boundaries and produce false confidence about what user code can access.
   - Reject tests that only instantiate the obvious type, mirror the generated skeleton, or fail to exercise the code path that would need metadata.

4. Review the metadata files only for presence and scope.
   - Confirm the expected metadata files exist for the single target coordinate.
   - Do not block the PR based on the internal contents of `reachability-metadata.json`.
   - Treat `reachability-metadata.json` containing `{}` as acceptable when the rest of the PR is coherent and validation passes.
   - Do not use the exploded stats files under `stats/<group>/<artifact>/<metadata-version>/stats.json` alone to argue that the submitted metadata is unnecessary.

5. Compare the PR claims, test, and reported coverage as one unit.
   - If the PR claims specific coverage numbers that do not line up with the diff, ask for investigation.
   - If the PR reports zero dynamic-access calls and `reachability-metadata.json` is `{}`, that is acceptable as long as the test is library-specific and the scope is otherwise correct.
   - Prefer concrete test quality issues over speculation about whether specific metadata entries are needed.

6. Check validation status.
   - Expected minimum: metadata validation and library test jobs for the target coordinates are green.
   - If the PR is small and the review concerns correctness rather than infra noise, prefer asking for code changes over rerunning CI.
   - If CI is flaky but the content is otherwise solid, ask for a rerun after the review issues are resolved.

## Review Heuristics

- Strong PR:
  - Test names and assertions are specific to the library behavior.
  - The test logic is version-agnostic and can cover multiple supported versions of the same library.
  - The PR stays scoped to one coordinate and its expected files.
  - Validation is green for the target coordinate.

- Weak PR:
  - PR pushes metadata or tests for more than one library.
  - Test class still looks like the scaffold.
  - Test logic disables or bypasses the library behavior under native image.
  - Test code hardcodes the target library version without a clear need.
  - Test sources are placed in the library package without a demonstrated need.
  - Claimed coverage is not credible from the diff.

## Output Style

Match the concise review style already used in this repository:

- For scaffold-only tests: say that tests must differ from the scaffold and should not be accepted as-is.
- For missing native runtime coverage: say that the added tests disable themselves under native image, so the library behavior never runs there and the PR does not provide native-image coverage.
- For version-pinned tests: say that tests should not reference the exact library version because the same test should support multiple library versions.
- For multiple-library PRs: say that `library-new-request` PRs must push only one library and ask for the unrelated library additions to be removed.
- For metadata/coverage mismatch: ask for investigation only when the PR makes concrete coverage claims that are not supported by the diff. Do not argue from metadata contents alone.

Keep comments short, factual, and blocking. Focus on the concrete defect, not a long explanation.
