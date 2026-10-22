# Architecture

Forge's implementation structure lives here. A document belongs in this
directory when it explains *how* Forge is built — which component owns a step,
where a boundary runs, what plugs in where. What Forge must *do* belongs in
[../functional-spec/](../functional-spec/README.md), and per-workflow behavior
belongs in [../functional-spec/](../functional-spec/README.md).

One kind lives here: every declaration in this folder is an `AR-` ID, whichever
file it sits in. Architecture that belongs to one component — the issue run in
[workflow-pipeline.md](workflow-pipeline.md) and its prompts in
[workflow-prompts.md](workflow-prompts.md), the dispatcher in
[dispatcher-package.md](dispatcher-package.md), publication in
[git-scripts.md](git-scripts.md), the exhaust report and chunk linking in
[workflows.md](workflows.md), and the code coverage workflow family in
[code-coverage/](code-coverage/) — is grouped by file rather than by prefix.
[drivers.md](drivers.md) and [orchestration-scripts.md](orchestration-scripts.md)
hold only retired stubs.

| ID | Subject |
| --- | --- |
| [§AR-forge-architecture](architecture.md#ar-forge-architecture-forge-architecture) | Forge architecture, and the map of every architecture ID |
| [§AR-forge-workflow-pipeline](workflow-pipeline.md#ar-forge-workflow-pipeline-forge-workflow-pipeline) | The Rhei state machine of one issue run, state by state |
| [§AR-forge-workflow-prompts](workflow-prompts.md#ar-forge-workflow-prompts-forge-workflow-prompts) | The fixed instructions and handoffs every agent state receives |
| [§AR-forge-run-location](architecture.md#ar-forge-run-location-one-run-location-for-progress-and-failure) | How progress and failure output share one phase/step vocabulary |
| [§AR-forge-control-plane](architecture.md#ar-forge-control-plane-the-launcher-owns-queue-control) | The launcher owns queue control |
| [§AR-forge-workflow-boundary](architecture.md#ar-forge-workflow-boundary-programs-compose-one-isolated-run) | Programs compose one isolated run |
| [§AR-forge-drivers](drivers.md#ar-forge-drivers-workflow-drivers-retired) | Workflow drivers (retired) |
| [§AR-forge-driver-queues](drivers.md#ar-forge-driver-queues-the-per-queue-drivers-retired) | The per-queue drivers (retired) |
| [§AR-forge-driver-finalization](drivers.md#ar-forge-driver-finalization-finalization-and-metrics-retired) | Finalization and metrics (retired) |
| [§AR-forge-strategy-agent-boundary](architecture.md#ar-forge-strategy-agent-boundary-programs-decide-agents-edit) | Programs decide, agents edit |
| [§AR-forge-verification-publication-boundary](architecture.md#ar-forge-verification-publication-boundary-local-verification-hands-data-to-trusted-publication) | Local verification hands data to trusted publication |
| [§AR-agent-api](agent.md#ar-agent-api-forge-agent-api-and-backend-adapters) | Forge agent API and backend adapters |
| [§AR-forge-workflow-system](workflows.md#ar-forge-workflow-system-forge-workflows-retired) | Forge workflows (retired) |
| [§AR-java-fail-fix-workflow](workflows.md#ar-java-fail-fix-workflow-java-fix-workflows-retired) | Java fix workflows (retired) |
| [§AR-dynamic-access-workflow](workflows.md#ar-dynamic-access-workflow-dynamic-access-exploration-retired) | Dynamic-access exploration (retired) |
| [§AR-dynamic-access-iterative](workflows.md#ar-dynamic-access-iterative-iterative-exploration-retired) | Iterative exploration (retired) |
| [§AR-dynamic-access-bulk](workflows.md#ar-dynamic-access-bulk-bulk-exploration-retired) | Bulk exploration (retired) |
| [§AR-dynamic-access-composite](workflows.md#ar-dynamic-access-composite-composite-fix-then-explore-retired) | Composite fix-then-explore (retired) |
| [§AR-dynamic-access-fallback-and-failure](workflows.md#ar-dynamic-access-fallback-and-failure-fallback-and-failure-retired) | Fallback and failure (retired) |
| [§AR-dynamic-access-exhaust-report](workflows.md#ar-dynamic-access-exhaust-report-exhaust-report) | Exhaust report |
| [§AR-chunked-dynamic-access-pr-linking](workflows.md#ar-chunked-dynamic-access-pr-linking-chunk-pr-linking) | Chunk PR linking |
| [§AR-code-coverage-improvement](code-coverage/workflow.md#ar-code-coverage-improvement-code-coverage-improvement-workflow) | Code coverage improvement workflow |
| [§AR-code-coverage-deep-navigation](code-coverage/deep-navigation.md#ar-code-coverage-deep-navigation-deep-phase-navigation-evidence) | Deep-phase navigation: samples, counters, control flow, routes, miss classification, and group sessions |
| [§AR-code-coverage-prompts](code-coverage/prompts.md#ar-code-coverage-prompts-agent-prompt-composition) | Agent prompt composition: which prompt layer carries what |
| [§AR-code-coverage-trajectory](code-coverage/trajectory.md#ar-code-coverage-trajectory-per-pass-coverage-trajectory) | Per-pass coverage trajectory: each loop's pass yield as a Rhei metric |
| [§AR-code-coverage-benchmarking](code-coverage/benchmarking.md#ar-code-coverage-benchmarking-code-coverage-benchmark-architecture) | Fixed-input benchmark execution, metrics publication, and preserved workspaces |
| [§AR-forge-orchestration](orchestration-scripts.md#ar-forge-orchestration-forge-orchestration-scripts-retired) | Forge orchestration scripts (retired) |
| [§AR-forge-dispatcher-decomposition](dispatcher-package.md#ar-forge-dispatcher-decomposition-dispatcher-package-boundaries) | Dispatcher package boundaries |
| [§AR-forge-publication](git-scripts.md#ar-forge-publication-forge-branch-and-pull-request-publication) | Forge branch and pull-request publication |
| [§AR-pr-eligibility](git-scripts.md#ar-pr-eligibility-pr-eligibility-boundary) | PR eligibility boundary |
| [§AR-shared-publication-pipeline](git-scripts.md#ar-shared-publication-pipeline-shared-branch-publication-pipeline) | Shared branch publication pipeline |
| [§AR-expected-paths](git-scripts.md#ar-expected-paths-expected-path-staging) | Expected path staging |
| [§AR-publication-descriptor](git-scripts.md#ar-publication-descriptor-durable-publication-descriptor) | Durable publication descriptor |
| [§AR-actions-publication](git-scripts.md#ar-actions-publication-trusted-github-actions-publisher) | Trusted GitHub Actions publisher |
| [§AR-pr-body](git-scripts.md#ar-pr-body-pull-request-body-contents) | Pull request body contents |
| [§AR-pr-preview-builders](git-scripts.md#ar-pr-preview-builders-reusable-title-and-body-builders) | Reusable title and body builders |
| [§AR-issue-linking](git-scripts.md#ar-issue-linking-issue-linking-and-labels) | Issue linking and labels |
| [§AR-chunked-linking](git-scripts.md#ar-chunked-linking-chunked-dynamic-access-pr-linking) | Chunked dynamic-access PR linking |
| [§AR-not-for-native-image-publication](git-scripts.md#ar-not-for-native-image-publication-not-for-native-image-publication) | Not-for-native-image publication |
| [§AR-real-use-tests-publication](git-scripts.md#ar-real-use-tests-publication-real-use-tests-publication) | Real-use tests publication |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [workflow-pipeline.md](workflow-pipeline.md) — the Rhei state machine of one
  issue run. Start here.
- [workflow-prompts.md](workflow-prompts.md) — what each agent state is told.
- [architecture.md](architecture.md) — the control plane and the boundaries that
  keep generated work reviewable.
- [workflows.md](workflows.md) — the exhaust report and chunk PR linking; the
  rest are retired stubs of the old workflow engines.
- [dispatcher-package.md](dispatcher-package.md) — the dispatcher's lifecycles
  and module boundaries.
- [agent.md](agent.md) — the agent interface and its Pi and Claude Code adapters.
- [drivers.md](drivers.md), [orchestration-scripts.md](orchestration-scripts.md)
  — retired stubs.
- [git-scripts.md](git-scripts.md) — publication: staging, the descriptor, the
  PR body, labels, and issue linking.
- [code-coverage/workflow.md](code-coverage/workflow.md) — the PGO-driven
  coverage workflow, behavior and architecture in one document until it is split
  into a spec of its own.
- [code-coverage/deep-navigation.md](code-coverage/deep-navigation.md) — the
  evidence that steers the deep phase, how each hint is derived from it, and
  how a pass runs its prompt as group sessions.
- [code-coverage/benchmarking.md](code-coverage/benchmarking.md) — the fixed
  suite, execution sequences, metrics derivation, publishing worktree,
  workspace preservation, and the naive baseline arm.
