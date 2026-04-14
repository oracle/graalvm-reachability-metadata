---
name: review-genai-pr
description: Review pull requests with the `GenAI` label in graalvm-reachability-metadata. Use when asked to review or triage an AI-generated PR that adds metadata and tests for a library, especially to catch scaffold-only tests, suspicious package names in test sources, metadata that is not justified by the exercised code path, and PRs that push more than one library.
---

# Review `GenAI` PRs

These PRs usually add reachability metadata and tests for one library version. Review them with extra scrutiny. The historical review comments in this repository show that the main failure mode is not formatting, but weak or unjustified content.

## Historical Review Signals

Treat the following as hard review rules unless the PR provides a strong reason otherwise:

- Do not accept PRs that push more than one library. A `GenAI` PR must stay scoped to one target library version plus its supporting test files.
- Do not accept scaffold-only tests. Generated tests must be changed into library-specific tests that exercise the behavior that requires the metadata.
- Do not accept test packages that match the library package directly. This makes `allowed-packages` harder to reason about and can hide bad test structure. Prefer a `test` prefix in the package path.
- Investigate any inconsistency between metadata coverage numbers and the actual metadata present in the diff. If the PR claims covered entries but the metadata file is empty or nearly empty, the test likely does not justify the result.

## Workflow

1. Inspect the PR summary.
   - Confirm the PR has label `GenAI`.
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

3. Review the test source before reading the metadata.
   - The test must be library-specific, not a lightly edited scaffold.
   - Reject tests that only instantiate the obvious type, mirror the generated skeleton, or fail to exercise the code path that would need metadata.
   - Check package names carefully. Tests should not live in the same package as the library under test unless there is a strong technical reason.
   - Prefer test packages prefixed with `test` so `allowed-packages` stays explicit and easy to audit.

4. Review the metadata with the test in mind.
   - Every metadata entry should be explainable from the exercised code path.
   - Be suspicious when the metadata is empty, trivial, or obviously smaller than what the PR claims to cover.
   - Ask where a metadata requirement comes from if the test does not appear to trigger it.
   - Do not approve speculative metadata added “just in case”.

5. Compare the metadata file, test, and reported coverage as one unit.
   - If covered-entry counts and the actual metadata do not line up, ask for investigation.
   - If the test would pass without the metadata, the PR has not proven the metadata is needed.
   - If the metadata would be needed but the test does not assert the relevant behavior, the PR is incomplete.

6. Check validation status.
   - Expected minimum: metadata validation and library test jobs for the target coordinates are green.
   - If the PR is small and the review concerns correctness rather than infra noise, prefer asking for code changes over rerunning CI.
   - If CI is flaky but the content is otherwise solid, ask for a rerun after the review issues are resolved.

## Review Heuristics

- Strong PR:
  - Test names and assertions are specific to the library behavior.
  - The test would fail meaningfully without the submitted metadata.
  - Test packages are clearly separated from the library namespace.
  - `reachability-metadata.json` contains only entries justified by the test.

- Weak PR:
  - PR pushes metadata or tests for more than one library.
  - Test class still looks like the scaffold.
  - Package name copies the library package.
  - Metadata appears without a demonstrated trigger path.
  - Claimed coverage is not credible from the diff.

## Output Style

Match the concise review style already used in this repository:

- For scaffold-only tests: say that tests must differ from the scaffold and should not be accepted as-is.
- For multiple-library PRs: say that `GenAI` PRs must push only one library and ask for the unrelated library additions to be removed.
- For bad test packaging: say that tests should not use the library package and ask for a `test` prefix.
- For metadata/coverage mismatch: ask where the metadata is and why the test would not fail without it.

Keep comments short, factual, and blocking. Focus on the concrete defect, not a long explanation.
