# AR-forge-dispatcher-decomposition: Dispatcher package boundaries

The dispatcher is the launcher's GitHub side (§AR-forge-control-plane). It hosts
two lifecycles with opposite directions: a producer lifecycle that turns a
labeled issue into one Rhei run — queue fetching, the live claim, the issue-form
gate, starting the run, and closing it out (§FS-forge-issue-resolution-goal) —
and a consumer lifecycle that watches the CI of the pull requests those runs
produce (§FS-published-pr-review). Its implementation is therefore a package,
`dispatcher/`, whose module boundaries follow those lifecycles rather than
call-graph proximity; `forge_metadata.py` stays the single CLI entry point that
parses arguments and wires them together (§AR-forge-workflow-pipeline.1).

The producer side decomposes along the fetch-to-run path: the per-label,
per-tier issue fetch, claim preflight against live GitHub state, the optimistic
claim, the issue-form gate, and the run launch with its close-out each own one
stage, so a stage's inputs come only from the stage before it. Everything about
the issue after the claim is the run's, not the dispatcher's. The consumer side
is merged-PR reconciliation followed by the CI pass of
§AR-forge-dispatcher-decomposition.1; the pass never starts a generation run.

Shared foundations sit below both lifecycles and may not depend on either:
configuration constants, cross-stage records, interrupt and shutdown state,
GitHub CLI plumbing, the issue claim cache with its local lock, worktree
helpers, and fixture-mode support used by end-to-end tests. Dependencies point
strictly downward — lifecycle modules import foundations and earlier stages,
never the reverse — so the package imports without cycles and each module is
patchable in isolation by the tests.

## 1. The CI pass

`forge_metadata.py --ci` runs one pass (§FS-published-pr-review). The
failing state and its evidence come from the scripts the maintainer queues
already use (§root/FS-maintainer-agent-queues.1): `.ephor/ci-failures.sh` reads
the head's check rollup, reports the failing jobs with the tail of each failed
log, and says how many checks are still running, and `.ephor/ci-restart.sh`
reruns the failed jobs of a head. The pass adds no second reader of GitHub
Actions.

The ledger is the pull request's own comments: each handled head gets one
comment by the Forge account that ends with
`<!-- forge-ci:<head SHA>:<action> -->`, where the action is `rerun`, `push`,
or `issue`. The markers answer the three bounds without local state — a head
with a marker is not diagnosed again, a `rerun` marker on a head that fails
again sends it to an issue, and two `push` markers spend the fix pushes — so a
pass on another host reaches the same decisions.

The agent runs in a detached worktree of the head, with its evidence and
instruction in the worktree's parent directory, and writes `diagnosis.json`
there: `cause` (`transient`, `defect`, `flaky`, or `outside`), `summary`, and,
for `outside`, the issue's `title`, `body`, and `signature`. A repair is the
checkout's `HEAD`. The program maps the cause to its action, refuses an action
the cause or the bounds do not allow, and performs it: the rerun through
`ci-restart.sh`, the push with a lease on the diagnosed head, and the issue
after a search for an open one whose body carries the same
`forge-ci-signature`. A refusal or an unconfirmed action becomes an issue the
program writes from the evidence and the agent's summary. A dry run stops each
action at the line that prints it.
