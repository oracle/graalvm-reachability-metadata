# ORCH-forge-orchestration-spec: Forge orchestration scripts

`forge_metadata.py` is Forge's orchestration hub between the do-work loop
(§DW-do-work-loop), GitHub, isolated worktrees, workflow drivers
(§WF-forge-workflow-drivers), review queues, and the git-scripts publication
component (§GIT-forge-publication). It resolves supported GitHub issues into
isolated workflow runs (§FS-forge-issue-resolution-goal): it owns queue
scanning, optimistic single-issue claiming, Maven coordinate and workflow
derivation, project status transitions, worktree creation, workflow dispatch,
retry/cache bookkeeping, and final issue cleanup. Workflow drivers receive
resolved inputs, own one run end to end (§AR-forge-workflow-boundary), and do
not scan queues, decide which issue to claim, or outsource deterministic setup
policy to Codex or other LLM agents during a generated run.

Supported issue queues are label-driven: `library-new-request` and
`library-update-request` route to dynamic-access generation
(§WF-dynamic-access-workflow) and coverage improvement
(§WF-improve-library-coverage); `fails-javac-compile` and `fails-java-run` route
to Java fail-fix (§WF-java-fail-fix-workflow); `fails-native-image-run` routes
to native-image run-fix (§WF-native-image-run-fix-workflow).
Successful runs produce PRs with matching review labels such as
`fixes-javac-fail`, `fixes-java-run-fail`, and `fixes-native-image-run-fail`;
issue labels and PR labels are not interchangeable.

Forge does not open these `fails-*` issues; it claims them. They are produced by
the repository's scheduled library version compatibility automation, which tests
newer upstream versions, records the passing ones in a `library-bulk-update` PR,
and files one labeled tracking issue per failing `(library, version)` pair —
the contract for that producer is the repository functional spec's
[Library version update automation](../../docs/functional-spec.md#fs-library-version-update-automation-library-version-update-automation)
(root-namespace ID `FS-library-version-update-automation`).

Orchestration must claim exactly one issue per workflow run, dispatch the
matching workflow driver, and either hand PR-eligible results to publication
(§GIT-pr-eligibility) or preserve failed results according to the workflow
status.

To keep queue scanning and claiming cheap under the GitHub API, orchestration
uses two shared, lock-protected local caches: an issue-search cache for queue
listing/count queries and an issue-claim cache that records recent claim
decisions. Both are enabled by default and short-lived — `FORGE_ISSUE_SEARCH_CACHE`
(TTL `FORGE_ISSUE_SEARCH_CACHE_TTL_SECONDS`, default 10 minutes) and
`FORGE_ISSUE_CLAIM_CACHE` (TTL `FORGE_ISSUE_CLAIM_CACHE_TTL_SECONDS`, default 15
minutes); setting either env var to `0` disables that cache. The caches only
reduce redundant API calls within a TTL window; claiming itself remains
optimistic and authoritative against live GitHub state.

## 1. Library-Specific Preparation Decision

After claiming a supported issue and preparing its isolated worktree, but before
dispatching the workflow driver, orchestration runs an LLM preflight decision
that identifies whether the library needs additional setup beyond the normal
generated test scaffold. Orchestration gives the agent a small starting context
— issue text and any existing tests — and instructs it to research the library
itself (its resolved artifact, dependencies, usage, and documentation) rather
than deciding solely from that context. The agent investigates but must not
modify the repository or apply setup; it returns only the decision.

The preflight decision exists for library-specific requirements that are hard
to infer from labels alone, such as optional Maven dependencies, Docker-backed
services and their required allowed Docker images, or library setup the agent
must perform before meaningful tests can be generated.

### 1.1 Deterministic setup versus advisory guidance

The preflight decision separates its output into two distinct kinds, and the
workflow driver routes each kind to a different consumer:

- **Deterministic setup** is a typed, structurally-validated list of one-time,
  idempotent source/config edits the driver applies itself before generation —
  not free text injected into a prompt. The supported kinds are a `dependency`
  declaration added to the library's test `build.gradle`, and a `docker_image`
  pin added to the `allowed-docker-images` directory. Because the model only
  supplies typed fields (a `group:artifact:version` coordinate, an image
  reference and slug), they are validated by shape rather than scanned as prose,
  and the driver applies each one once and idempotently. These are source-tree
  edits, not environment mutations: the driver does not pull images, download
  dependencies, or mutate the environment from the decision — the actual image
  pull and dependency resolution stay gated by the allow-list and the build, and
  local CI-equivalent verification (§FS-local-ci-equivalent-verification) remains
  the sole authority for rejecting work CI would not accept.

- **Advisory guidance** is the residual reasoning the agent must apply inside the
  generated test code (for example, exercise a driver against a stood-up
  service, or set a system property before a factory lookup). Only this guidance
  is passed to the workflow as prompt context, and it is evidence, not trusted
  instructions. Deterministic setup the driver already applied is omitted from
  the prompt so an iterating workflow does not re-attempt a completed edit; any
  deterministic item the driver could not apply yet (for example, a `dependency`
  edit for a new library whose scaffold does not exist until generation) falls
  back into the advisory guidance so the agent still performs it.

This split is what keeps deterministic, idempotent edits out of the iteration
loop and confines the prompt to reasoning. The driver — not orchestration and
not a generated-run LLM agent — owns the decision of what to do with each field
of the persisted record.

The LLM decision is advisory input to workflow preparation, not a verification
result. It must be recorded in metrics with the prompt, model, decision,
evidence, selected deterministic setup, advisory guidance, and the result of
applying each deterministic action. A preflight decision must not allow tests to
rely on untracked downloads, undeclared optional dependencies, or Docker images
that CI would reject (§FS-local-ci-equivalent-verification).

If the preflight decision cannot run or returns invalid, unavailable, or unsafe
output, orchestration must degrade to a recorded no-action advisory result. It
must keep the collected evidence and failure reason visible in metrics rather
than silently omitting the preflight record.

Orchestration scripts must not let a failed workflow silently disappear.
Successful or chunk-ready runs (§WF-chunked-dynamic-access-pr-linking) proceed to
the git-scripts publication component; failed runs preserve diagnostics
(§FS-local-ci-equivalent-verification), restore claim state as appropriate, and
leave enough context for human follow-up.

## 2. Pull Request Review Queues

`forge_metadata.py` also owns the pull-request review side of Forge, which is a
separate responsibility from issue resolution: it reviews already-published PRs
rather than producing them. This is the orchestration mechanics behind the
review behavior contract in §FS-automated-pr-review. It is entered through
`forge_metadata.py --review-pr <label> [--limit N] [--review-model <model>]
[--period <seconds|Nm|Nh|Nd>]`, and the do-work loop (§DW-do-work-loop) drives
the same code path on its own schedule.

**Queue configuration.** A single explicit `FORGE_REVIEW_LABEL` selects one
review queue; otherwise orchestration runs the default set of PR review queues,
one per successful-result label — `library-new-request`, `library-update-request`,
`fixes-javac-fail`, `fixes-java-run-fail`, `fixes-native-image-run-fail`, and the
bulk-update label. Each queue has a per-label limit env var (defaulting to
`FORGE_REVIEW_LIMIT`, default 1) and shares `FORGE_REVIEW_MODEL`. Setting a
queue's limit to 0 disables it.

**Candidate selection.** For each queue, orchestration fetches PRs carrying the
queue label, plus PRs carrying `human-intervention-fixed`, and selects only
those that are CI-complete, not authored by the authenticated review user, and
not still blocked by `human-intervention`. PRs labeled `human-intervention` are
skipped until a maintainer marks them `human-intervention-fixed`, at which point
orchestration may dismiss stale requested-changes reviews and let normal merge
gates proceed (§FS-automated-pr-review).

**Isolated review run.** Each selected PR is reviewed in a throwaway detached
worktree created from a freshly fetched base ref, with the PR checked out in
detached HEAD. Review is performed by Codex (`codex exec`), which is expected to
apply the label-specific review skill, read the authoritative diff with
`gh pr diff`, and submit either an approval or a requested-changes review
directly on GitHub. The review must not write files or re-checkout. The run is
logged durably (§FS-durable-generation-logs) and the worktree is cleaned up
afterward; a review timeout or non-zero Codex exit is a review failure, not an
approval.

**Scheduling and shutdown.** With `--period`, the review loop repeats after each
interval; without it, it runs once. The loop checks the do-work stop markers
(§DW-do-work-loop) between iterations and during sleep and exits without
starting another review when a stop marker is present.
