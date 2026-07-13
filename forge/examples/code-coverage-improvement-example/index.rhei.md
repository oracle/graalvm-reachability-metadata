# Rhei: Code Coverage Improvement
**States:** code-coverage-improvement

## Overview

This workspace converts `oracle/graalvm-reachability-metadata` issue `#8380` into a bounded
code coverage improvement workflow for one already-supported library. The issue
must carry `code-coverage-improvement` and identify a Maven coordinate in
`group:artifact:version` form unless `org.example:example-library:1.2.3` is provided.

The workflow keeps generated code coverage tests under the dedicated suite path
`tests/src/<group>/<artifact>/<test-version>/code-coverage-improvement` (a tracked
extension suite inside the indexed test project), writes runtime evidence under
`runtime/code-coverage/`, and runs two separately measured phases
§WF-code-coverage-improvement. The first gives the agent only exact
JaCoCo-uncovered public API entries. The second gives it at most 100
JaCoCo-uncovered internal methods as compact paths derived from sampled PGO and
the Native Image static call graph. Each phase has five bounded editing passes,
and every pass is followed by a fresh JaCoCo result.

## Source

| Field | Value |
|---|---|
| Repository | `oracle/graalvm-reachability-metadata` |
| Issue | `#8380` |
| Required label | `code-coverage-improvement` |
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

## Verification

The pipeline tasks run unreviewed: deterministic helpers, schema-validated
artifacts, and zero-exit validation gates decide their completion. The coverage
loops are measurement-driven: a deterministic measurement program
(JaCoCo correlation; for the deep loop also sampled PGO and the static call
graph) always writes the current report to one fixed location, decides
whether the loop continues, and derives the cover prompt from that report in
the same step; the cover agent always returns to
measurement — at most `5` cover passes per phase, with
measurement step failures repaired through a bounded fix state. The
finalization task runs as a deterministic program of numbered steps (read the
conversion record, run the JVM tests with the coverage suite, finalize
metrics); a nonzero exit code is the number of the failed step and routes to
`finalize-fix`, after which the steps re-run from the start. `finalize-verify`
then accepts the artifacts only on presence, schema validation of
`final-metrics.json`, and metrics outcomes — never on an agent's own claim.
Fixable failures are bounded by `2` pass(es); failed targets or an
explicit `needsHumanIntervention` flag route directly to `human-intervention`.
Finalization runs no Native Image validation at this stage.
