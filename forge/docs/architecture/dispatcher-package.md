# AR-forge-dispatcher-decomposition: Dispatcher package boundaries

The dispatcher is the launcher's GitHub side (§AR-forge-control-plane). It hosts
two lifecycles with opposite directions: a producer lifecycle that turns a
labeled issue into one Rhei run — queue fetching, the live claim, the issue-form
gate, starting the run, and closing it out (§FS-forge-issue-resolution-goal) —
and a consumer lifecycle that reviews the pull requests those runs produce
(§FS-published-pr-review). Its implementation is therefore a package,
`dispatcher/`, whose module boundaries follow those lifecycles rather than
call-graph proximity; `forge_metadata.py` stays the single CLI entry point that
parses arguments and wires them together (§AR-forge-workflow-pipeline.1).

The producer side decomposes along the fetch-to-run path: the per-label,
per-tier issue fetch, claim preflight against live GitHub state, the optimistic
claim, the issue-form gate, and the run launch with its close-out each own one
stage, so a stage's inputs come only from the stage before it. Everything about
the issue after the claim is the run's, not the dispatcher's. The consumer side
is merged-PR reconciliation followed by the review of
§AR-forge-dispatcher-decomposition.1; the review never starts a generation run.

Shared foundations sit below both lifecycles and may not depend on either:
configuration constants, cross-stage records, interrupt and shutdown state,
GitHub CLI plumbing, the issue claim cache with its local lock, worktree
helpers, and fixture-mode support used by end-to-end tests. Dependencies point
strictly downward — lifecycle modules import foundations and earlier stages,
never the reverse — so the package imports without cycles and each module is
patchable in isolation by the tests.

## 1. The review

`forge_metadata.py --review` runs one pass (§FS-published-pr-review). Its reads
are one module: the open `ai/**` pull requests, a pull request as it is now, the
check runs and commit statuses of a head commit read through the commits API, so
an earlier head can be replayed, the tail of each failed job's log, the
`in-review` timeline event, and the head markers. The pass module owns every
decision and write: the skips, the lock, the agent, the confirmation, the
labels, the markers, and the issue.

The ledger is the pull request's own comments: a rerun or a pushed fix leaves
one comment by the Forge account that ends with
`<!-- forge-review:<head SHA>:<action> -->`, where the action is `rerun`,
`push`, or `issue`. The markers answer both bounds without local state — a
`rerun` marker on the head leaves no rerun, and two `push` markers spend the
fix pushes — so a pass on another host reaches the same decisions. A terminal
outcome is the `reviewed` label instead.

The agent runs in a detached worktree of the head. Its instruction is the
template `prompt_templates/pr_review/review.md`, with the `Metadata` and
`Native Image` rules of the `fix` state cut from the template's `states.yaml`
so both agents follow one copy, and its evidence (`evidence.md`,
`pull-request.md`) sits in the worktree's parent directory. It writes
`review.json` there: `outcome` (`approve`, `rerun`, `fix`, or `issue`), `case`,
`cause`, `summary`, and, for an issue, the issue's `title`, `body`, and
`signature`. The program confirms the outcome against GitHub — the approval of
exactly the head with auto-merge armed, a restarted run on the head, a new head
that descends from the reviewed one and, since it, changes only the contribution's paths
and `.github/spring-aot-excluded-tests.json` — and files the issue after a
search for an open one whose body carries the same `forge-ci-signature`. An
outcome it refuses or cannot confirm becomes an issue the program writes from
the evidence and the agent's summary. A dry run stops each write at the line
that prints it, and runs the agent with a dummy `GH_TOKEN`, an empty
`GH_CONFIG_DIR`, and a `pushInsteadOf` rewrite of every GitHub URL to an
unreachable one.
