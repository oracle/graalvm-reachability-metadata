# FS-forge-run-continuation: Run continuation and resume

Run continuation lets a later Forge run re-enter a failed issue run at the phase
that failed, instead of regenerating the whole result from scratch. It is a
cross-cutting capability across the Forge workflow system
(§AR-forge-workflow-system) and builds on the workflow engine's ownership of run
state (§AR-forge-workflow-engine): the engine
already advances per-iteration progress as commits and reverts to the last good
checkpoint on a failed iteration (§AR-dynamic-access-iterative).
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
| `publication` | discrete (remote) | If `isPushed`, the exact descriptor branch already landed and local publication is complete; report it without another mutation. Otherwise reuse the recorded publication identity and run local finalization (§AR-shared-publication-pipeline). |

The unifying invariant: **the preserved branch HEAD is the cursor.** The
committed tree is the source of truth for where a phase got to, so the marker
never stores commit hashes — only the logical state a rebuild cannot recover.
Every phase before the active resume point must be terminal: workflows that do
not have a primary fix phase mark `fix` as `skipped` when setup completes, and
workflows that do not run dynamic-access exploration mark `explore` as
`skipped` before finalization.

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
  "strategyName": "dynamic_access_main_sources_pi_gpt-5.6-sol",
  "issueNumber": 9102,
  "label": "library-new-request",
  "coordinate": "com.acme:widget:1.4.0",
  "newVersion": null,
  "libraryUpdateRoute": null,
  "libraryPreparationPreflight": null,
  "publicationMetrics": {
    "library": "com.acme:widget:1.4.0",
    "timestamp": "2026-06-18T14:44:56.782450Z",
    "extras": {
      "post_generation_intervention": { "stage": "future-defaults-all" },
      "local_ci_verification": { "status": "passed" }
    }
  },
  "phases": {
    "setup":        { "status": "completed", "preflightDone": true, "setupDone": true },
    "fix":          { "status": "skipped",   "iteration": null },
    "explore":      { "status": "completed", "exhaustedClasses": ["com.acme.Foo"],
                      "chunkClassCount": 15, "chunkProcessedClassCount": 4 },
    "finalization": { "status": "completed" },
    "publication":  { "status": "pending",   "isPushed": false,
                      "publicationId": "9102-library-new-request-20260618T144456Z",
                      "branch": "ai/<login>/add-lib-support-com.acme-widget-1.4.0-9102-20260618t144456z" }
  }
}
```

### 2.1 Lifecycle

The marker is **gitignored in the worktree during the run** and written eagerly
on each phase transition, so it survives a hard kill or agent timeout. It is
**force-added onto the preservation branch only** when a logical failure
preserves the work. This is the same mechanic the chunked dynamic-access exhaust
report already uses to carry resume state (§AR-dynamic-access-exhaust-report);
because the marker never enters a successful run's publication staging
(§AR-expected-paths), a completed PR stays clean with no cleanup step.

### 2.2 Field rules

- `continueFrom` is the authoritative resume entry point; it equals the first
  phase whose `status` is not `completed` or `skipped`.
- `preservedBranch` is the remote branch that holds the preserved tree and the
  marker.
- `strategyName` re-instantiates the workflow engine and its exploration variant
  (§FS-forge-predefined-strategy-contract).
- `issueNumber`, `label`, `coordinate`, and `newVersion` re-route the workflow;
  they are kept explicit because the coordinate is sanitized inside the branch
  name and cannot be parsed back reliably.
- `libraryUpdateRoute` records the dispatcher-selected route for
  `library-update-request` issues so publication-only resume does not depend on
  a per-run sidecar directory that is absent from the preserved branch.
- `libraryPreparationPreflight` records the driver's completed neural setup
  output so resume can skip the setup agent while still restoring applied
  actions, source-context evidence, and advisory guidance.
- `publicationMetrics` records the committed per-library execution-metrics
  entry (`library` plus `timestamp`) and only the local-only descriptor fields
  needed to reconstruct `.pending_metrics.json` during publication resume.
  Durable metrics
  remain the source for normal cost, token, coverage, and status evidence
  (§FS-forge-run-metrics); the marker carries local extras such as
  `post_generation_intervention`, `local_ci_verification`, and
  `library_update_alias_split` when they exist.
- Improve-coverage runs write the original `.baseline-stats.json` into the
  resolved test directory during setup and include it in the setup checkpoint.
  Resume treats that committed checkpoint file as preserved state and reuses it
  instead of recomputing the baseline after generation may already have changed
  the tree.
- `explore.exhaustedClasses` is the only EXPLORE state worth keeping: a fresh
  report cannot distinguish an uncovered-but-abandoned class from an
  uncovered-but-untried one.
- `explore.chunkClassCount` and `explore.chunkProcessedClassCount` record active
  chunk budget already spent by a failed run, so resume continues the same
  chunk instead of starting a full new threshold-sized chunk.
- `publication.isPushed` is stored rather than derived so a stale remote branch
  cannot be mistaken for this run's exact push; `publication.branch` and
  `publication.publicationId` preserve the unique descriptor identity and branch
  regardless of which identity runs the resume.

## 3. Resume flow

A resume run reads the marker from the preserved branch and:

1. Requires the issue to carry `resumable`; plain `human-intervention` issues
   stay manual and do not trigger preserved-branch discovery.
2. Re-routes the workflow from `issueNumber`/`label`/`coordinate` and
   re-instantiates the engine from `strategyName`.
3. Derives preserved-work branch prefixes from recent issue comment authors and
   the deterministic `ai/<login>/human-intervention/issue-...-` branch naming
   convention, then selects a matching remote branch that carries a valid marker.
4. Checks out the preserved branch and rebases it onto current `master`; if it
   no longer applies cleanly, it falls back to a clean run rather than resuming a
   stale tree.
5. Enters the phase named by `continueFrom` and applies that phase's resume
   action from §1, skipping every earlier completed or skipped phase.

If a resumed run fails again while `continueFrom` still points to the same
phase, Forge treats automatic continuation as exhausted for that issue. It
removes the `resumable` label, releases the issue claim, and does not post a
second human-intervention analysis because the first failed-run report remains
the maintainer-facing diagnostic (§FS-human-intervention-policy).

Publication resume hinges on the single push. The marker supplies the stable
publication ID and branch. When `isPushed` is false, Forge clears resume-only
artifacts, reconstructs any missing pending metrics, writes the descriptor,
commits, and pushes. When `isPushed` is true, the exact local handoff is already
complete; Forge does not create a PR or push another bookkeeping commit.

Pending metrics remain readable until descriptor creation. If the transient
file is missing, Forge reconstructs it from the durable execution-metrics entry
referenced by `publicationMetrics` and overlays marker extras. A publication
marker without those inputs is incomplete: Forge does not guess from the latest
metrics entry. Actions publication proceeds independently from the pushed
descriptor and its failures remain preserved for manual inspection.

## 4. Relationship to human intervention

Continuation does not change the human-intervention contract
(§FS-human-intervention-policy). A logical failure still preserves the work
branch and labels the issue `human-intervention`, so a maintainer always retains
the existing safety signal and diagnostics. This holds even when the failure
halts the worktree mid-rebase, in either publication rebase mode: the rebase
*starts* and then stops on an `index.json` conflict from a sibling same-artifact
PR that landed while the run was in flight, or the rebase *refuses to start*
because the worktree carries changes outside the narrowly staged expected paths
(§AR-expected-paths). Preservation first aborts any in-progress rebase — a no-op
in the refuse-to-start mode, where no sequencer state exists — then commits the
generated tree (including those unstaged changes) to the preserved branch, so the
branch is never silently dropped. The marker rides that same preserved branch,
giving a later automated run — or the maintainer — a precise place to continue.

Because local finalization runs only after a PR-eligible workflow result, a
failure before the verified push carries a successful workflow status but no
generation-analysis candidate. Forge still preserves and marks that local
failure as resumable rather than orphaning the marker. A repeated failure in the
same resumed phase removes `resumable` without adding another analysis. External
or transient local failures retain their existing no-action policy.

A Branch Ready or Actions publisher failure occurs after local completion and is
not converted into Forge failure handling: it leaves the branch, assignment,
labels, and `In Progress` project state untouched for manual inspection.
