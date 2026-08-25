# Architecture

Forge's implementation structure lives here. A document belongs in this
directory when it explains *how* Forge is built — which component owns a step,
where a boundary runs, what plugs in where. What Forge must *do* belongs in
[../functional-spec/](../functional-spec/README.md), and per-workflow behavior
belongs in [../functional-spec/](../functional-spec/README.md).

One kind lives here: every declaration in this folder is an `AR-` ID, whichever
file it sits in. Architecture that belongs to one component — the worker loop in
[do-work.md](do-work.md), the dispatcher in
[orchestration-scripts.md](orchestration-scripts.md), publication in
[git-scripts.md](git-scripts.md), the workflow engines in
[workflows.md](workflows.md), the code coverage workflow in
[code-coverage-improvement.md](code-coverage-improvement.md) — is grouped by
file rather than by prefix.

| ID | Subject |
| --- | --- |
| [§AR-forge-architecture](architecture.md#ar-forge-architecture-forge-architecture) | Forge architecture, and the map of every architecture ID |
| [§AR-forge-workflow-pipeline](architecture.md#ar-forge-workflow-pipeline-forge-workflow-architecture) | What runs during one workflow, step by step, and who owns each step |
| [§AR-forge-run-location](architecture.md#ar-forge-run-location-one-run-location-for-progress-and-failure) | How progress and failure output share one phase/step vocabulary |
| [§AR-forge-control-plane](architecture.md#ar-forge-control-plane-worker-loop-and-dispatcher-own-queue-control) | Worker loop and dispatcher own queue control |
| [§AR-forge-workflow-boundary](architecture.md#ar-forge-workflow-boundary-workflow-drivers-compose-setup-workflow-engine-and-metrics) | Workflow drivers compose setup, workflow engine, and metrics |
| [§AR-forge-drivers](drivers.md#ar-forge-drivers-workflow-drivers) | Workflow drivers: one per issue queue |
| [§AR-forge-driver-contract](drivers.md#ar-forge-driver-contract-what-every-driver-does) | What every driver does |
| [§AR-forge-driver-queues](drivers.md#ar-forge-driver-queues-the-per-queue-drivers) | The per-queue drivers |
| [§AR-forge-driver-finalization](drivers.md#ar-forge-driver-finalization-finalization-and-metrics) | Finalization and metrics |
| [§AR-forge-strategy-agent-boundary](architecture.md#ar-forge-strategy-agent-boundary-strategies-configure-workflows-agents-edit-code) | Strategies configure workflows, agents edit code |
| [§AR-forge-verification-publication-boundary](architecture.md#ar-forge-verification-publication-boundary-local-verification-hands-data-to-trusted-publication) | Local verification hands data to trusted publication |
| [§AR-agent-api](agent.md#ar-agent-api-forge-agent-api-and-backend-adapters) | Forge agent API and backend adapters |
| [§AR-forge-workflow-system](workflows.md#ar-forge-workflow-system-forge-workflows) | Forge workflows: workflow, driver, strategy, and the six engines |
| [§AR-forge-workflow-engine](workflows.md#ar-forge-workflow-engine-what-every-workflow-owns) | What every workflow owns |
| [§AR-forge-workflow-strategy-config](workflows.md#ar-forge-workflow-strategy-config-strategies-bind-to-workflows) | Strategies bind to workflows |
| [§AR-basic-iterative](workflows.md#ar-basic-iterative-basic-iterative) | Basic iterative |
| [§AR-java-fail-fix-workflow](workflows.md#ar-java-fail-fix-workflow-java-fix-workflows) | Java fix workflows |
| [§AR-dynamic-access-workflow](workflows.md#ar-dynamic-access-workflow-dynamic-access-exploration) | Dynamic-access exploration |
| [§AR-dynamic-access-iterative](workflows.md#ar-dynamic-access-iterative-iterative-exploration) | Iterative exploration |
| [§AR-dynamic-access-bulk](workflows.md#ar-dynamic-access-bulk-optimistic-exploration) | Optimistic exploration |
| [§AR-dynamic-access-composite](workflows.md#ar-dynamic-access-composite-composite-fix-then-explore) | Composite fix-then-explore |
| [§AR-dynamic-access-fallback-and-failure](workflows.md#ar-dynamic-access-fallback-and-failure-fallback-and-failure) | Fallback and failure |
| [§AR-dynamic-access-exhaust-report](workflows.md#ar-dynamic-access-exhaust-report-exhaust-report) | Exhaust report |
| [§AR-native-test-verification-callers](workflows.md#ar-native-test-verification-callers-callers) | Which engine invokes the native test verification gate, and when |
| [§AR-chunked-dynamic-access-pr-linking](workflows.md#ar-chunked-dynamic-access-pr-linking-chunk-pr-linking) | Chunk PR linking |
| [§AR-code-coverage-improvement](code-coverage-improvement.md#ar-code-coverage-improvement-code-coverage-improvement-workflow) | Code coverage improvement workflow |
| [§AR-code-coverage-improvement-architecture](code-coverage-improvement.md#ar-code-coverage-improvement-architecture-code-coverage-improvement-workflow-architecture) | Code coverage improvement workflow architecture |
| [§AR-do-work-loop](do-work.md#ar-do-work-loop-do-work-loop-architecture) | do-work loop architecture |
| [§AR-forge-orchestration](orchestration-scripts.md#ar-forge-orchestration-forge-orchestration-scripts) | Forge orchestration scripts |
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

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [architecture.md](architecture.md) — the pipeline, the control plane, and the
  boundaries that keep generated work reviewable. Start here.
- [workflows.md](workflows.md) — the six registered workflow engines: what each
  is for, which strategy families bind to it, and what every engine owes a run.
- [drivers.md](drivers.md) — one driver per issue queue: what each prepares,
  which workflow it runs, and how a run is finalized.
- [agent.md](agent.md) — the agent interface and its backend adapters.
- [do-work.md](do-work.md) — the long-running worker loop: bootstrap,
  self-update, stop markers, and cycle scheduling.
- [orchestration-scripts.md](orchestration-scripts.md) — the dispatcher: queue
  claiming, worktree setup, workflow dispatch, and issue bookkeeping.
- [git-scripts.md](git-scripts.md) — publication: staging, the descriptor, the
  PR body, labels, and issue linking.
- [code-coverage-improvement.md](code-coverage-improvement.md) — the PGO-driven
  coverage workflow, behavior and architecture in one document until it is split
  into a spec of its own.
