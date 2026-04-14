---
name: review-library-bulk-update
description: Review automated pull requests with the `library-bulk-update` label in graalvm-reachability-metadata. Use when asked to review or triage a PR that bumps tested versions for existing libraries, including approve vs close decisions, CI checks, diff-scope validation, and verification of `source-code-url`, `test-code-url`, `documentation-url`, and `repository-url` changes.
---

# Review `library-bulk-update` PRs

These PRs are created by the scheduled compatibility workflow and are expected to update supported library versions by changing `metadata/**/index.json`.

## Historical Review Logic

Previous reviews follow a simple pattern:

- Approve silently when the PR only updates the expected metadata index files and CI is green, then enable auto-merge.
- Close the PR when it contains unrelated changes such as workflow files, Gradle files, generated sources, or test code that clearly did not belong in a bulk version update.
- Close stale PRs with merge conflicts instead of repairing them manually if a newer bulk-update PR will supersede them.
- If CI looks flaky or inconsistent with the diff, ask for or trigger a rerun rather than approving blindly.
- Treat generated-source changes as suspicious in this PR type. They should normally not appear here.

## Workflow

1. Inspect the PR summary.
   - Confirm the PR has label `library-bulk-update`.
   - Read the body and verify it is a bot-generated "Added tested versions" summary.
   - Gather files, reviews, issue comments, inline comments, and status checks.

2. Validate the diff scope first.
   - Expected files: `metadata/<group>/<artifact>/index.json`.
   - Normal change shape: new values appended to `tested-versions`, with formatting-only line movement around the surrounding JSON.
   - Be suspicious of changes to `latest`, `metadata-version`, `test-version`, `allowed-packages`, `requires`, or URL fields unless the PR clearly needs them.
   - Reject or close the PR if it changes:
     - `.github/workflows/**`
     - `ci.json`
     - `gradle/**`, `build.gradle`, `settings.gradle`, `gradle.properties`
     - `tests/src/**`
     - generated Java sources
     - any other non-`metadata/**/index.json` file

3. Check that the PR body matches the diff.
   - Every library listed in the body should correspond to an `index.json` diff.
   - Added tested versions should match the actual JSON changes.
   - There should not be hidden updates outside the listed libraries.

4. Check CI before deciding.
   - Required baseline: JSON validation, metadata tests, and style checks must be green.
   - If the PR unexpectedly triggered build-logic or workflow-heavy jobs, that is a sign the diff scope is wrong.
   - If checks failed because the PR is contaminated by unrelated changes, close it.
   - If checks failed due to likely infra flakiness or a suspicious runner issue, request a rerun or rerun all relevant tests before approval.

5. Review each changed `index.json`.
   - Confirm the new versions were added under the correct `metadata-version` entry.
   - Preserve existing ordering conventions; new tested versions are typically appended in version order.
   - Do not accept dropped tested versions, entry reshuffling, or unrelated field edits without a clear reason.

6. Apply URL verification when URL fields changed.
   - This is higher scrutiny than a normal bulk-update PR.
   - Review `source-code-url`, `test-code-url`, `documentation-url`, and `repository-url` with the same rules used by `PopulateArtifactURLs`.

7. If the PR is clean and approved, enable auto-merge.
   - In this repository, approved automation PRs are expected to auto-merge after approval.
   - Do this after the approval is submitted, and only when the PR is otherwise mergeable.

## URL Verification Rules

If any URL field changed, verify all of the following:

- `repository-url` is the canonical repository root URL.
- `repository-url` must not include a versioned tree path such as `/tree/<tag>`.
- `source-code-url`, `test-code-url`, and `documentation-url` must point to the exact library version in the changed entry.
- Do not accept unversioned docs, `latest`, `current`, or branch-based docs unless there is no versioned source and the PR clearly justifies it.
- Prefer Maven `-sources.jar`, `-test-sources.jar`, and `-javadoc.jar` when they exist and are valid.
- If a Maven `-sources.jar` or `-test-sources.jar` is used, verify it contains real source files such as `.java`, `.kt`, `.scala`, or `.groovy`, not only metadata or license files.
- If the URL points to a repository tree or archive instead of Maven, verify that it resolves to real source or test files for the exact version tag.
- If a candidate source or test URL cannot be verified with confidence, it should not be approved as-is.

Use the logic from `tests/tck-build-logic/src/main/groovy/org/graalvm/internal/tck/harness/tasks/PopulateArtifactURLs.java`, especially the `urlUpdateInstructions` and `sourceArtifactVerificationInstructions` rules.

## Decision Rules

Approve when all of these are true:

- The PR is limited to the expected `metadata/**/index.json` files.
- The diff is only tested-version maintenance or clearly justified URL maintenance.
- CI is green, or any rerun confirms green status.
- Any changed URLs were verified against the exact version.
- Auto-merge was enabled after approval when the PR is mergeable.

Close or reject when any of these are true:

- The PR contains unrelated files or generated code.
- The PR is stale, conflicted, or clearly superseded by a newer automation run.
- CI failures point to a real regression or invalid update.
- URL changes cannot be verified confidently.

Ask for rerun or deeper investigation when:

- The diff looks correct but CI failed in a way that smells like infrastructure noise.
- A generated file changed and you need to confirm whether the workflow accidentally regenerated something.

## Output Style

Match the historical style:

- Clean PR: approve with no comment or a very short confirmation, then enable auto-merge.
- Contaminated PR: leave a short factual comment explaining why it is being closed or should not be merged.
- Flaky CI: leave a short comment requesting or noting a rerun.
