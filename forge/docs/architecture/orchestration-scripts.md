# AR-forge-orchestration: Forge orchestration scripts

`forge_metadata.py` is Forge's orchestration hub between the do-work loop
(§AR-do-work-loop), GitHub, isolated worktrees, workflow drivers
(§AR-forge-drivers), review queues, and the git-scripts publication
component (§AR-forge-publication). It resolves supported GitHub issues into
isolated workflow runs (§FS-forge-issue-resolution-goal): it owns queue
scanning, optimistic single-issue claiming, Maven coordinate and workflow
derivation, project status transitions, worktree creation, workflow dispatch,
retry/cache bookkeeping, and final issue cleanup. Workflow drivers receive
resolved inputs, own one run end to end (§AR-forge-workflow-boundary), and do
not scan queues, decide which issue to claim, or outsource deterministic setup
policy to Codex or other LLM agents during a generated run.

Supported issue queues are label-driven: `library-new-request` and
`library-update-request` route to dynamic-access generation
(§AR-dynamic-access-workflow) and coverage improvement
(§AR-forge-driver-queues.2); `fails-javac-compile` and `fails-java-run` route
to Java fail-fix (§AR-java-fail-fix-workflow); `fails-native-image-run` routes
to native-image run-fix (§AR-forge-driver-queues.4).
Successful runs produce PRs with matching review labels such as
`fixes-javac-fail`, `fixes-java-run-fail`, and `fixes-native-image-run-fail`;
issue labels and PR labels are not interchangeable.

Forge does not open these `fails-*` issues; it claims them. They are produced by
the repository's scheduled library version compatibility automation, which tests
newer upstream versions, records the passing ones in a `library-bulk-update` PR,
and files one labeled tracking issue per failing `(library, version)` pair —
the contract for that producer is the repository functional spec's
[Library version update automation](../../../docs/functional-spec/functional-spec.md#fs-library-version-update-automation-library-version-update-automation)
(root-namespace ID `FS-library-version-update-automation`).

Queue scans drain a pipeline by label-derived urgency tier rather than ranking
within a fetched batch: the scan pages through every `high-priority` issue
first, then every `priority` issue, then issues carrying neither label. Each
tier is a separate GitHub search that excludes the labels of the tiers above it,
so an issue carrying both priority labels is served once, in the highest tier,
matching the repository status classification (§root/FS-repository-status-report.1).
A tier is left only once its search returns no further results, and its own
result window is therefore capped independently at GitHub's first
`GITHUB_SEARCH_MAX_RESULTS` matches.
Tier membership comes only from priority labels already present on GitHub;
queue scanning and claim checks do not add priority labels automatically.

Operators may restrict a queue run to exactly one tier with
`--priority high`, `--priority priority`, or `--priority normal`. The `high`
tier requires `high-priority`; the `priority` tier requires `priority` and
excludes `high-priority`; the `normal` tier excludes both labels. Without this
option, Forge drains all three tiers in order.

Tiered draining applies to scans that start at offset `0`. A scan started from a
random offset without `--priority` keeps paging the flat, unfiltered label query
so that concurrent runners spread across the queue. With `--priority`, the
offset—random or explicit—is relative only to the selected tier.

Orchestration must claim exactly one issue per workflow run, dispatch the
matching workflow driver, and either hand PR-eligible results to publication
(§AR-pr-eligibility) or preserve failed results according to the workflow
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

Operators can restrict issue queue scans to user-requested issues. In that
mode, orchestration fetches issue queue batches with the ordinary label query
and excludes issues authored by repository automation or the configured
maintainer accounts locally before claim processing. Queue counts and offsets
remain based on the ordinary GitHub search result order so the filter does not
make GitHub Search queries more complex. This filter applies only to issue
queue scans, not to explicit `--issue-number` runs, large-library continuation
artifacts, or pull request review queues.

## 1. Library-Specific Preparation Decision

After claiming a supported issue and preparing its isolated worktree,
orchestration passes the resolved coordinates, validated strategy, issue
context, worktree, setup-evidence path, and continuation state to the selected
workflow driver. The driver first completes normal setup, then runs an LLM
preflight decision as part of neural setup to identify whether the library needs
anything beyond the prepared scaffold or copied repair target. During
`neural_setup()`, the agent receives the populated artifact information,
downloaded source context, issue text, and prepared tests. It investigates but
must not modify the repository directly; typed setup actions are validated and
applied through the neural-setup boundary.

The preflight uses its own predefined Pi strategy with model `gpt-5.6-sol` and
medium reasoning, independently of the strategy selected for the dispatched
workflow. Operators may replace that bundle with
`FORGE_LIBRARY_PREFLIGHT_STRATEGY_NAME` without changing the generation
strategy.

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
  generated test code or repo-local test configuration, especially environment
  variables, system properties, or test initialization. The workflow prompt
  receives the decision summary, the deterministic setup Forge already applied
  or found present, and this advisory guidance as evidence, not trusted
  instructions. Any deterministic item the driver could not apply yet (for
  example, a `dependency` edit for a new library whose scaffold does not exist
  until generation) falls back into the advisory guidance so the agent still
  performs it.

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
The driver stores preflight handoff and prompt/response evidence in the ignored
per-run setup-evidence directory supplied by orchestration, not in the isolated
reachability worktree. Durable evidence is the normalized record embedded in
run metrics (§FS-forge-run-metrics).

If the agent times out during `neural_setup()` or returns invalid, unavailable,
or unsafe output, the driver must return a setup failure to orchestration. It
must keep the collected evidence and failure reason visible in metrics and
continuation state rather than silently converting the failure to `no_action`.

Orchestration scripts must not let a failed workflow silently disappear.
Successful or chunk-ready runs (§AR-chunked-dynamic-access-pr-linking) build one
typed publication handoff and invoke the shared local branch finalizer. That
finalizer writes the descriptor and pushes the verified branch; orchestration
then reports the branch and publication ID as the successful local outcome
without invoking a workflow-specific PR creator.

Follow-up issues for deferred coverage or tested-version splits are created
locally before the verified push and handed to the trusted Actions publisher as
typed descriptor facts carrying their issue numbers
(§AR-publication-descriptor). A later Branch Ready failure
does not cause local failure handling: the pushed branch remains preserved and
the claimed issue remains `In Progress` and assigned for manual inspection.
Failed generation or local finalization still preserves diagnostics
(§FS-local-ci-equivalent-verification), restores claim state as appropriate, and
leaves enough context for human follow-up.

## 2. Pull Request Review Queues

`forge_metadata.py` also owns the pull-request review side of Forge, which is a
separate responsibility from issue resolution: it reviews already-published PRs
rather than producing them. This is the orchestration mechanics behind the
review behavior contract in §FS-automated-pr-review. It is entered through
`forge_metadata.py --review-pr <label> [--limit N]
[--period <seconds|Nm|Nh|Nd>]`, and the do-work loop (§AR-do-work-loop) drives
the same code path on its own schedule.

**Queue configuration.** A single explicit `FORGE_REVIEW_LABEL` selects one
review queue; otherwise orchestration runs the default set of PR review queues,
one per successful-result label — `library-new-request`, `library-update-request`,
`fixes-javac-fail`, `fixes-java-run-fail`, `fixes-native-image-run-fail`, and the
bulk-update label. Each queue has a per-label limit env var (defaulting to
`FORGE_REVIEW_LIMIT`, default 1). Setting a queue's limit to 0 disables it. The
reviewer is the worker-configured analysis role, with no review-specific agent,
model, provider, or thinking override (§FS-forge-agent-runtime-selection).

**Candidate selection.** For each queue, orchestration fetches PRs carrying the
queue label, plus PRs carrying `human-intervention-fixed`, and selects only
those that are CI-complete, not authored by the authenticated review user, and
not still blocked by `human-intervention`. PRs labeled `human-intervention` are
skipped until a maintainer marks them `human-intervention-fixed`, at which point
orchestration may dismiss stale requested-changes reviews and let normal merge
gates proceed (§FS-automated-pr-review).

**Isolated review run.** Before selecting review work, orchestration validates
the parent process's GitHub CLI authentication and the selected analysis
backend's authentication without invoking a model (§FS-automated-pr-review).
Each selected PR is reviewed in a throwaway detached worktree created from a
freshly fetched base ref, with the PR checked out in detached HEAD.
The selected analysis agent is trusted with the authenticated GitHub CLI, reads
live PR metadata and checks, applies the label-specific checked-in rules, and
uses targeted diffs against the fresh base before submitting the review itself.
The shared analysis runtime owns invocation, durable logging, failures, and
token accounting (§FS-durable-generation-logs); orchestration supplies only the
review prompt, worktree, and GitHub-access capability. The worktree is cleaned
up afterward, and a timeout or unsuccessful agent turn is a review failure, not
an approval.

**Scheduling and shutdown.** With `--period`, the review loop repeats after each
interval; without it, it runs once. The loop checks the do-work stop markers
(§AR-do-work-loop) between iterations and during sleep and exits without
starting another review when a stop marker is present.
