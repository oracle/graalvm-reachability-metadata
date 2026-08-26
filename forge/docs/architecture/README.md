# Architecture

Forge's implementation structure lives here. A document belongs in this
directory when it explains *how* Forge is built — which component owns a step,
where a boundary runs, what plugs in where. What Forge must *do* belongs in
[../functional-spec/](../functional-spec/README.md), and per-workflow behavior
belongs in [../functional-spec/](../functional-spec/README.md).

Not every ID here is an `AR` ID. Architecture that belongs to one component
keeps its own prefix and its own home file in this folder, so the prefix still
tells you which file to open:

| Prefix | Home | Scope |
| --- | --- | --- |
| `AR` | this folder | what cuts across components |
| `DW` | [do-work.md](do-work.md) | the unattended worker loop |
| `ORCH` | [orchestration-scripts.md](orchestration-scripts.md) | the dispatcher |
| `GIT` | [git-scripts.md](git-scripts.md) | branch and pull-request publication |
| `WF` | [workflows.md](workflows.md) | the registered workflow engines |
| `CC` | [code-coverage-improvement.md](code-coverage-improvement.md) | the code coverage workflow, pending its own spec |

| ID | Subject |
| --- | --- |
| [§AR-forge-architecture](architecture.md#ar-forge-architecture-forge-architecture) | Forge architecture, and the map of every architecture ID |
| [§AR-forge-workflow-pipeline](architecture.md#ar-forge-workflow-pipeline-forge-workflow-architecture) | What runs during one workflow, step by step, and who owns each step |
| [§AR-forge-control-plane](architecture.md#ar-forge-control-plane-worker-loop-and-dispatcher-own-queue-control) | Worker loop and dispatcher own queue control |
| [§AR-forge-workflow-boundary](architecture.md#ar-forge-workflow-boundary-workflow-drivers-compose-setup-workflow-engine-and-metrics) | Workflow drivers compose setup, workflow engine, and metrics |
| [§AR-forge-drivers](drivers.md#ar-forge-drivers-workflow-drivers) | Workflow drivers: one per issue queue |
| [§AR-forge-driver-contract](drivers.md#ar-forge-driver-contract-what-every-driver-does) | What every driver does |
| [§AR-forge-driver-queues](drivers.md#ar-forge-driver-queues-the-per-queue-drivers) | The per-queue drivers |
| [§AR-forge-driver-finalization](drivers.md#ar-forge-driver-finalization-finalization-and-metrics) | Finalization and metrics |
| [§AR-forge-strategy-agent-boundary](architecture.md#ar-forge-strategy-agent-boundary-strategies-configure-workflows-agents-edit-code) | Strategies configure workflows, agents edit code |
| [§AR-forge-verification-publication-boundary](architecture.md#ar-forge-verification-publication-boundary-local-verification-hands-data-to-trusted-publication) | Local verification hands data to trusted publication |
| [§AR-agent-api](agent.md#ar-agent-api-forge-agent-api-and-pi-implementation) | Forge agent API and Pi implementation |
| §WF-forge-workflow-system | Forge workflows: workflow, driver, strategy, and the six engines |
| §WF-forge-workflow-engine | What every workflow owns |
| §WF-forge-workflow-strategy-config | Strategies bind to workflows |
| §WF-basic-iterative | Basic iterative |
| §WF-java-fail-fix-workflow | Java fix workflows |
| §WF-dynamic-access-workflow | Dynamic-access exploration |
| §WF-dynamic-access-iterative | Iterative exploration |
| §WF-dynamic-access-bulk | Optimistic exploration |
| §WF-dynamic-access-composite | Composite fix-then-explore |
| §WF-dynamic-access-fallback-and-failure | Fallback and failure |
| §WF-dynamic-access-exhaust-report | Exhaust report |
| §WF-native-test-verification-callers | Which engine invokes the native test verification gate, and when |
| §WF-chunked-dynamic-access-pr-linking | Chunk PR linking |
| §CC-code-coverage-improvement | Code coverage improvement workflow |
| §CC-code-coverage-improvement-architecture | Code coverage improvement workflow architecture |
| §DW-do-work-loop | do-work loop architecture |
| §ORCH-forge-orchestration-spec | Forge orchestration scripts |
| §GIT-forge-publication | Forge branch and pull-request publication |
| §GIT-pr-eligibility | PR eligibility boundary |
| §GIT-shared-publication-pipeline | Shared branch publication pipeline |
| §GIT-expected-paths | Expected path staging |
| §GIT-publication-descriptor | Durable publication descriptor |
| §GIT-actions-publication | Trusted GitHub Actions publisher |
| §GIT-pr-body | Pull request body contents |
| §GIT-pr-preview-builders | Reusable title and body builders |
| §GIT-issue-linking | Issue linking and labels |
| §GIT-chunked-linking | Chunked dynamic-access PR linking |
| §GIT-not-for-native-image-publication | Not-for-native-image publication |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [architecture.md](architecture.md) — the pipeline, the control plane, and the
  boundaries that keep generated work reviewable. Start here.
- [workflows.md](workflows.md) — the six registered workflow engines: what each
  is for, which strategy families bind to it, and what every engine owes a run.
- [drivers.md](drivers.md) — one driver per issue queue: what each prepares,
  which workflow it runs, and how a run is finalized.
- [agent.md](agent.md) — the agent interface and its Pi implementation.
- [do-work.md](do-work.md) — the long-running worker loop: bootstrap,
  self-update, stop markers, and cycle scheduling.
- [orchestration-scripts.md](orchestration-scripts.md) — the dispatcher: queue
  claiming, worktree setup, workflow dispatch, and issue bookkeeping.
- [git-scripts.md](git-scripts.md) — publication: staging, the descriptor, the
  PR body, labels, and issue linking.
- [code-coverage-improvement.md](code-coverage-improvement.md) — the PGO-driven
  coverage workflow, behavior and architecture in one document until it is split
  into a spec of its own.
