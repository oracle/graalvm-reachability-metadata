# AR-forge-dispatcher-decomposition: Dispatcher package boundaries

The dispatcher is the launcher's GitHub side (§AR-forge-control-plane). It hosts
two lifecycles with opposite directions: a producer lifecycle that turns a
labeled issue into one Rhei run — queue fetching, the live claim, the issue-form
gate, starting the run, and closing it out (§FS-forge-issue-resolution-goal) —
and a consumer lifecycle that reviews the pull requests those runs produce
(§FS-automated-pr-review). Its implementation is therefore a package,
`dispatcher/`, whose module boundaries follow those lifecycles rather than
call-graph proximity; `forge_metadata.py` stays the single CLI entry point that
parses arguments and wires them together (§AR-forge-workflow-pipeline.1).

The producer side decomposes along the fetch-to-run path: the per-label,
per-tier issue fetch, claim preflight against live GitHub state, the optimistic
claim, the issue-form gate, and the run launch with its close-out each own one
stage, so a stage's inputs come only from the stage before it. Everything about
the issue after the claim is the run's, not the dispatcher's. The consumer side
is merged-PR reconciliation followed by one worker-agent turn per eligible pull
request; a review never starts a generation run (§FS-automated-pr-review).

Shared foundations sit below both lifecycles and may not depend on either:
configuration constants, cross-stage records, interrupt and shutdown state,
GitHub CLI plumbing, the issue claim cache with its local lock, worktree
helpers, and fixture-mode support used by end-to-end tests. Dependencies point
strictly downward — lifecycle modules import foundations and earlier stages,
never the reverse — so the package imports without cycles and each module is
patchable in isolation by the tests.
