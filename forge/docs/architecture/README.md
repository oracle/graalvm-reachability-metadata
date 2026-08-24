# Architecture

Forge's implementation structure lives here. A document belongs in this
directory when it explains *how* Forge is built — which component owns a step,
where a boundary runs, what plugs in where. What Forge must *do* belongs in
[../functional-spec/](../functional-spec/README.md), and per-workflow behavior
belongs in [../functional-spec/workflows/](../functional-spec/workflows/README.md).

Not every ID here is an `AR` ID. Architecture that belongs to one component
keeps its own prefix and its own home file in this folder, so the prefix still
tells you which file to open:

| Prefix | Home | Scope |
| --- | --- | --- |
| `AR` | this folder | what cuts across components |
| `DW` | [do-work.md](do-work.md) | the unattended worker loop |
| `ORCH` | [orchestration-scripts.md](orchestration-scripts.md) | the dispatcher |
| `GIT` | [git-scripts.md](git-scripts.md) | branch and pull-request publication |

| ID | Subject |
| --- | --- |
| §AR-forge-architecture | Forge architecture, and the map of every architecture ID |
| §AR-forge-workflow-pipeline | What runs during one workflow, step by step, and who owns each step |
| §AR-forge-control-plane | Worker loop and dispatcher own queue control |
| §AR-forge-workflow-boundary | Workflow drivers compose setup, workflow engine, and metrics |
| §AR-forge-strategy-agent-boundary | Strategies configure workflows, agents edit code |
| §AR-forge-verification-publication-boundary | Local verification hands data to trusted publication |
| §AR-agent-api | Forge agent API and Pi implementation |
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
- [agent.md](agent.md) — the agent interface and its Pi implementation.
- [do-work.md](do-work.md) — the long-running worker loop: bootstrap,
  self-update, stop markers, and cycle scheduling.
- [orchestration-scripts.md](orchestration-scripts.md) — the dispatcher: queue
  claiming, worktree setup, workflow dispatch, and issue bookkeeping.
- [git-scripts.md](git-scripts.md) — publication: staging, the descriptor, the
  PR body, labels, and issue linking.
