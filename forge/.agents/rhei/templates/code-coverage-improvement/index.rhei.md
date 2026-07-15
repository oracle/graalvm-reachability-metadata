# Rhei: Code Coverage Improvement
**States:** code-coverage-improvement

## Overview

This workspace converts `{{repo}}` issue `#{{issue_number}}` into a bounded
code coverage improvement workflow for one already-supported library. The issue
must carry `{{issue_label}}` and identify a Maven coordinate in
`group:artifact:version` form unless `{{coordinate}}` is provided.

The workflow keeps generated code coverage tests under the dedicated suite path
`tests/src/<group>/<artifact>/<test-version>/code-coverage-improvement` (a tracked
extension suite inside the indexed test project), writes runtime evidence under
`runtime/code-coverage/`, and runs two separately measured phases
§WF-code-coverage-improvement. The first gives the agent at most 200 exact
JaCoCo-uncovered public API entries. The second gives it at most 200
JaCoCo-uncovered internal methods as compact paths derived from sampled PGO and
the Native Image static call graph. Each phase has a bounded editing-pass
budget scaled heuristically from its baseline uncovered count, and every pass
is followed by a fresh JaCoCo result.

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

## Verification

The pipeline tasks run unreviewed: deterministic helpers, schema-validated
artifacts, and zero-exit validation gates decide their completion. The coverage
loops are measurement-driven: a deterministic measurement program
(JaCoCo correlation; for the deep loop also sampled PGO and the static call
graph) always writes the current report to one fixed location, decides
whether the loop continues, and derives the cover prompt from that report in
the same step; the cover agent always returns to
measurement — a heuristic per-phase budget of baseline-uncovered / 2 / 250
cover passes, at least one and at most `{{coverage_iterations}}`, with
measurement step failures repaired through a bounded fix state. The
finalization task runs as a deterministic program of numbered steps (read the
conversion record, run checkstyle, run the JVM tests with the coverage
suite, finalize
metrics); a nonzero exit code is the number of the failed step and routes to
`finalize-fix`, after which the steps re-run from the start. `finalize-verify`
then accepts the artifacts only on presence, schema validation of
`final-metrics.json`, and metrics outcomes — never on an agent's own claim.
Fixable failures are bounded by `{{fix_passes}}` pass(es); failed targets or an
explicit `needsHumanIntervention` flag route directly to `human-intervention`.
Finalization runs no Native Image validation at this stage.
