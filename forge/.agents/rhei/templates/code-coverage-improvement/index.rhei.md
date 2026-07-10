# Rhei: Code Coverage Improvement
**States:** code-coverage-improvement

## Overview

This workspace converts `{{repo}}` issue `#{{issue_number}}` into a bounded
code coverage improvement workflow for one already-supported library. The issue
must carry `{{issue_label}}` and identify a Maven coordinate in
`group:artifact:version` form unless `{{coordinate}}` is provided.

The workflow keeps generated code coverage tests under the dedicated suite path
`tests/<group>/<artifact>/<version>/code-coverage`, writes runtime evidence under
`runtime/code-coverage/`, and separates JVM JaCoCo API coverage from Native
Image sampled-PGO near-call evidence §WF-code-coverage-improvement.

## Source

| Field | Value |
|---|---|
| Repository | `{{repo}}` |
| Issue | `#{{issue_number}}` |
| Required label | `{{issue_label}}` |
| Coordinate override | `{{coordinate}}` |
| Source checkout | `{{repo_checkout}}` |
| Worktree root | `{{worktree_root}}` |
| Work subdirectory | `{{work_subdir}}` |
| Project owner | `{{project_owner}}` |
| Project number | `{{project_number}}` |
| TODO status | `{{todo_status}}` |
| In-progress status | `{{in_progress_status}}` |
| PR push remote | `{{pr_push_remote}}` |
| PR head owner | `{{pr_head_owner}}` |
| PR base branch | `{{pr_base_branch}}` |

## Review

Reviewed tasks use `agent-review` followed by deterministic routing from the
latest `**Needs fixes:** yes/no` marker. Fixes are bounded to the latest review
summary and return to review until `{{review_passes}}` pass(es) have run.
