# Rhei: Code Coverage Improvement
**States:** code-coverage-improvement

## Overview

This workspace converts `oracle/graalvm-reachability-metadata` issue `#8380` into a bounded
code coverage improvement workflow for one already-supported library. The issue
must carry `improve-code-coverage` and identify a Maven coordinate in
`group:artifact:version` form unless `org.example:example-library:1.2.3` is provided.

The workflow keeps generated code coverage tests under the dedicated suite path
`tests/<group>/<artifact>/<version>/code-coverage`, writes runtime evidence under
`runtime/code-coverage/`, and separates JVM JaCoCo API coverage from Native
Image instrumented-PGO discovery evidence §WF-code-coverage-improvement.

## Source

| Field | Value |
|---|---|
| Repository | `oracle/graalvm-reachability-metadata` |
| Issue | `#8380` |
| Required label | `improve-code-coverage` |
| Coordinate override | `org.example:example-library:1.2.3` |
| Source checkout | `../../..` |
| Worktree root | `../../../.agents/worktrees` |
| Work subdirectory | `forge` |
| Project owner | `oracle` |
| Project number | `30` |
| TODO status | `Todo` |
| In-progress status | `In Progress` |
| PR push remote | `` |
| PR head owner | `` |
| PR base branch | `master` |

## Review

Reviewed tasks use `agent-review` followed by deterministic routing from the
latest `**Needs fixes:** yes/no` marker. Fixes are bounded to the latest review
summary and return to review until `2` pass(es) have run.
