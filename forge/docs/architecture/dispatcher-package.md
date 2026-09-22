# AR-forge-dispatcher-decomposition: Dispatcher package boundaries

The dispatcher (§AR-forge-orchestration) is one process that hosts two
lifecycles with opposite directions: a producer lifecycle that turns a labeled
issue into a workflow run — queue scanning, optimistic claiming, workspace
setup, driver invocation, and publication handoff
(§FS-forge-issue-resolution-goal) — and a consumer lifecycle that reviews the
pull requests those runs produce (§FS-automated-pr-review). The dispatcher's
implementation is therefore a package, `dispatcher/`, whose module boundaries
follow those lifecycles rather than call-graph proximity; `forge_metadata.py`
stays the single CLI entry point that parses arguments and wires the queues
(§AR-forge-control-plane).

The producer side decomposes along the claim-to-publication path: issue queue
search and priority tiers, claim preflight with its cached skip decisions,
issue claiming and workspace preparation, workflow driver invocation, and
publication handoff each own one stage, so a stage's inputs come only from the
stage before it. The consumer side decomposes around the review loop: pull
request state and merge gating feed the review queue, and failed-CI repair is a
module inside the review boundary — a repair is a review outcome, never a new
generation run (§FS-automated-pr-review).

Failure paths are first-class boundaries, not appendices of the stage that
failed. Preservation of failed work — the durable branch, its logs, and the
continuation marker — is kept separate from human-intervention analysis, which
reads preserved evidence to label and explain the failure
(§FS-human-intervention-policy): preservation must succeed even when analysis
cannot run, so the two never share a module.

Shared foundations sit below both lifecycles and may not depend on either:
configuration constants, cross-stage records, interrupt and shutdown state,
GitHub CLI plumbing, the issue claim and search caches with their local locks
(§AR-forge-orchestration), worktree management, and fixture-mode support used
by end-to-end tests. Dependencies point strictly downward — lifecycle modules
import foundations and earlier stages, never the reverse — so the package
imports without cycles and each module is patchable in isolation by the tests.
