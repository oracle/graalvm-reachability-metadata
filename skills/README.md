# Skills

This directory contains all agent skills and workflows used for the `graalvm-reachability-metadata` project.

Skills are loaded by symlinking the desired skill directory into the matching repo-local agent directory.

## Available Skills

### `fix-missing-reachability-metadata`

Fixes missing GraalVM reachability metadata for a library specified by coordinates. 
It runs the target tests, identifies missing metadata entries from the error output, and adds them to the correct section of `reachability-metadata.json`. This process repeats until all required metadata is available and the tests pass.

### `close-new-library-support-pr`

Closes new-library support pull requests.
It finds the issue linked by the PR, removes the issue assignee, rolls the linked project item from `In Progress` back to `Todo`, and preserves `Done` when another PR has already completed the issue.

### `ipr`

Creates a GitHub issue and pull request for `oracle/graalvm-reachability-metadata`, links the PR to the issue, and requests reviews from the standard reviewers other than the PR author.

## Loading Locally

Repo-root skill links are committed as direct directory symlinks:

- `.codex/skills -> ../skills`
- `.claude/skills -> ../skills`
- `.pi/skills -> ../skills`

The `forge/` sub-workspace also carries links for agents launched from that
directory:

- `forge/skills -> ../skills`
- `forge/.codex/skills -> ../../skills`
- `forge/.claude/skills -> ../../skills`
- `forge/.pi/skills -> ../../skills`

Any other agent-local state remains ignored by Git.
