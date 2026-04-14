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

### `review-genai-pr`

Reviews pull requests with the `GenAI` label.
It encodes the review rules that have already emerged in this repository for AI-generated metadata PRs, especially rejecting scaffold-only tests, requiring test packages to stay separate from library packages, and questioning metadata coverage claims that are not supported by the diff.

## Loading Locally

Repo-local skill links are committed as direct directory symlinks:

- `.codex/skills -> ../skills`
- `.claude/skills -> ../skills`
- `.pi/skills -> ../skills`
- `.antigravity/skills -> ../skills`

Any other agent-local state remains ignored by Git.
