# FS-forge-run-continuation: Run continuation and resume

Run continuation lets a later Forge run re-enter a failed issue run at the phase
that failed, instead of regenerating the whole result from scratch. It is a
cross-cutting capability across the Forge workflow system
(§WF-forge-workflow-system) and builds on the workflow engine's ownership of run
state (§WF-forge-workflow-engine): the engine
already advances per-iteration progress as commits and reverts to the last good
checkpoint on a failed iteration (§WF-dynamic-access-iterative-strategy).
Continuation makes that checkpoint state survive process exit and become
re-entrant on a new run.

Continuation serves §GOAL-shorten-issue-to-shipped-metadata — which already
requires Forge to "preserve enough evidence for maintainers or later Forge runs
to continue without rediscovering the same problem" — and
§GOAL-minimize-generation-cost, by not re-spending tokens and compute on phases
that already succeeded.

Continuation is **additive** to human intervention, not a replacement. A logical
failure still preserves the work branch and labels the issue `human-intervention`
exactly as before (§FS-human-intervention-policy); continuation only adds a
machine-readable way for a later run, or a maintainer, to resume from that
preserved branch.

## 1. Phase model

A run is an ordered sequence of phases. Continuation classifies each phase by its
*shape*, because shape decides how it resumes:

- **Continuous phases** iterate internally and commit many checkpoints. Resume
  *continues* from the last checkpoint.
- **Discrete phases** are one short step. Resume *redoes* the step from the
  preserved branch; a completed discrete phase is skipped.

| Phase | Shape | Resume action |
| --- | --- | --- |
| `setup` | discrete | Skip the sub-steps already marked done (`preflightDone`, `setupDone`); otherwise rerun. |
| `fix` | continuous | Continue from the preserved branch at the recorded `iteration`. |
| `explore` | continuous | Rerun the phase; the regenerated dynamic-access report self-prunes to uncovered classes, and the recorded `exhaustedClasses` keep already-abandoned classes from being retried. |
| `finalization` | discrete | If entered but not completed, redo from the preserved branch. |
| `publication` | discrete (remote) | If `isPushed`, the branch already landed on the recorded `branch` and resume finishes publication from there; otherwise run the full publication pipeline (§GIT-shared-publication-pipeline). |

The unifying invariant: **the preserved branch HEAD is the cursor.** The
committed tree is the source of truth for where a phase got to, so the marker
never stores commit hashes — only the logical state a rebuild cannot recover.

## 2. ContinuationMarker

The continuation marker is one JSON object that records only non-reconstructable
state. Anything a rebuild can regenerate is omitted and recomputed on resume: the
dynamic-access coverage report is regenerated from the rebuilt tree, and covered
classes re-appear in the regenerated report.

```json
{
  "schemaVersion": 1,
  "continueFrom": "publication",
  "preservedBranch": "ai/<login>/human-intervention/issue-9102-library-new-request-com.acme.widget-3f9a2c11",
  "strategyName": "dynamic_access_main_sources_pi_gpt-5.5",
  "issueNumber": 9102,
  "label": "library-new-request",
  "coordinate": "com.acme:widget:1.4.0",
  "newVersion": null,
  "libraryUpdateRoute": null,
  "libraryPreparationPreflight": null,
  "phases": {
    "setup":        { "status": "completed", "preflightDone": true, "setupDone": true },
    "fix":          { "status": "skipped",   "iteration": null },
    "explore":      { "status": "completed", "exhaustedClasses": ["com.acme.Foo"] },
    "finalization": { "status": "completed" },
    "publication":  { "status": "pending",   "isPushed": false,
                      "branch": "ai/<login>/add-lib-support-com.acme-widget-1.4.0" }
  }
}
```

### 2.1 Lifecycle

The marker is **gitignored in the worktree during the run** and written eagerly
on each phase transition, so it survives a hard kill or agent timeout. It is
**force-added onto the preservation branch only** when a logical failure
preserves the work. This is the same mechanic the chunked dynamic-access exhaust
report already uses to carry resume state (§WF-dynamic-access-exhaust-report);
because the marker never enters a successful run's publication staging
(§GIT-expected-paths), a completed PR stays clean with no cleanup step.

### 2.2 Field rules

- `continueFrom` is the authoritative resume entry point; it equals the first
  phase whose `status` is not `completed` or `skipped`.
- `preservedBranch` is the remote branch that holds the preserved tree and the
  marker.
- `strategyName` re-instantiates the workflow engine and its exploration variant
  (§STRAT-forge-predefined-strategy-contract).
- `issueNumber`, `label`, `coordinate`, and `newVersion` re-route the workflow;
  they are kept explicit because the coordinate is sanitized inside the branch
  name and cannot be parsed back reliably.
- `libraryUpdateRoute` records the dispatcher-selected route for
  `library-update-request` issues so publication-only resume does not depend on
  a per-run sidecar directory that is absent from the preserved branch.
- `libraryPreparationPreflight` records dispatcher preflight output so resume
  can skip the preflight agent while still passing the original advisory setup
  context back to the workflow driver.
- Improve-coverage runs write the original `.baseline-stats.json` into the
  resolved test directory during setup. Resume treats that as preserved worktree
  state and reuses it instead of recomputing the baseline after generation may
  already have changed the tree.
- `explore.exhaustedClasses` is the only EXPLORE state worth keeping: a fresh
  report cannot distinguish an uncovered-but-abandoned class from an
  uncovered-but-untried one.
- `publication.isPushed` is stored rather than derived so a stale remote branch
  from an earlier aborted attempt cannot be mistaken for this run's push;
  `publication.branch` is stored so resume targets the original branch namespace
  regardless of which identity runs the resume.

## 3. Resume flow

A resume run reads the marker from the preserved branch and:

1. Re-routes the workflow from `issueNumber`/`label`/`coordinate` and
   re-instantiates the engine from `strategyName`.
2. Derives preserved-work branch prefixes from recent issue comment authors and
   the deterministic `ai/<login>/human-intervention/issue-...-` branch naming
   convention, then selects a matching remote branch that carries a valid marker.
3. Checks out the preserved branch and rebases it onto current `master`; if it
   no longer applies cleanly, it falls back to a clean run rather than resuming a
   stale tree.
4. Enters the phase named by `continueFrom` and applies that phase's resume
   action from §1, skipping every earlier completed or skipped phase.

Publication resume hinges on the push: the branch is read from the marker
and `isPushed` records whether the branch is already pushed, so a resumed run only
does the PR making. Opening the pull request is the workflow's
completion — a marker only exists for a run that failed before that point, so
continuation never reaches a state with the pull request already open.

## 4. Relationship to human intervention

Continuation does not change the human-intervention contract
(§FS-human-intervention-policy). A logical failure still preserves the work
branch and labels the issue `human-intervention`, so a maintainer always retains
the existing safety signal and diagnostics. The marker rides that same preserved
branch, giving a later automated run — or the maintainer — a precise place to
continue. External or transient failures take no issue action and write no
marker, exactly as today.
