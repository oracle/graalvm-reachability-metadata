# Skills

This directory contains all agent skills and workflows used for the `graalvm-reachability-metadata` project.

Skills are loaded by symlinking the desired skill directory into the matching repo-local agent directory.

## Available Skills

### `fix-missing-reachability-metadata`

Fixes missing GraalVM reachability metadata for a library specified by coordinates. 
It runs the target tests, identifies missing metadata entries from the error output, and adds them to the correct section of `reachability-metadata.json`. This process repeats until all required metadata is available and the tests pass.

### `review-library-bulk-update`

Reviews automated pull requests with the `library-bulk-update` label.
It encodes the historical approve/close/rerun logic for bulk tested-version updates, checks that the diff stays limited to `metadata/**/index.json`, requires `REQUEST_CHANGES` whenever the review leaves a blocking comment, and approves clean PRs without merging them yet.

### `review-library-new-request`

Reviews pull requests with the `library-new-request` label.
It covers new-library metadata PRs, including titles like `[GenAI] Add support for com.fasterxml:classmate:1.5.1 using gpt-5.4`, and encodes the review rules already used in this repository: reject scaffold-only tests, keep test packages separate from library packages so tests do not bypass visibility boundaries, and question metadata coverage claims that are not supported by the diff.

### `review-fixes-javac-fail`

Reviews pull requests with the `fixes-javac-fail` label.
It covers compile-failure repair PRs for existing libraries and applies a lighter review than `library-new-request`: keep the diff scoped to the compile fix, preserve meaningful tests, and block regressions where dynamic-access coverage drops between the previously tested version and the new version.

### `review-fixes-java-run-fail`

Reviews pull requests with the `fixes-java-run-fail` label.
It covers JVM runtime-failure repair PRs for existing libraries and applies a lighter review than `library-new-request`: keep the diff scoped to the Java runtime fix, preserve meaningful tests and assertions, and block regressions where dynamic-access coverage drops between the previously tested version and the new version.

### `review-fixes-native-image-run-fail`

Reviews pull requests with the `fixes-native-image-run-fail` label.
It covers native-image runtime-failure repair PRs for existing libraries and applies a lighter review than `library-new-request`: verify the native path is fixed rather than skipped, keep metadata and test changes scoped, and block regressions where dynamic-access coverage drops between the previously tested version and the new version.

### `close-new-library-support-pr`

Closes new-library support pull requests.
It finds the issue linked by the PR, removes the issue assignee, rolls the linked project item from `In Progress` back to `Todo`, and preserves `Done` when another PR has already completed the issue.

### `close-human-intervention-and-failing-ci-prs`

Closes batches of open pull requests that have the `human-intervention` label or failing CI.
It resolves each linked issue, clears all assignees, and moves project status back to `Todo`.

### `ipr`

Creates a GitHub issue and pull request for `oracle/graalvm-reachability-metadata`, links the PR to the issue, and requests reviews from `kimeta`, `jormundur00`, and `vjovanov`.

## Loading Locally

Repo-root skill links are committed as direct directory symlinks:

- `.codex/skills -> ../skills`
- `.claude/skills -> ../skills`
- `.pi/skills -> ../skills`
- `.antigravity/skills -> ../skills`

The `forge/` sub-workspace also carries links for agents launched from that
directory:

- `forge/skills -> ../skills`
- `forge/.codex/skills -> ../../skills`
- `forge/.claude/skills -> ../../skills`
- `forge/.pi/skills -> ../../skills`
- `forge/.antigravity/skills -> ../../skills`

Any other agent-local state remains ignored by Git.
