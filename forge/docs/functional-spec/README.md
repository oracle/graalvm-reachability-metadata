# Functional spec

Forge behavior and contributor-facing requirements live here. A document belongs
in this directory when it states *what* Forge must do — a requirement a run can
be held to, a gate it must pass, a contract that decides an outcome. How Forge
is built belongs in [../architecture/](../architecture/README.md) — including the
workflow engines that execute a run
([workflows.md](../architecture/workflows.md)) and the drivers that prepare one
([drivers.md](../architecture/drivers.md)).

Not every ID here is an `FS` ID. A contract that belongs to one component keeps
its own prefix and its own home file in this folder, so the prefix still tells
you which file to open:

| Prefix | Home | Scope |
| --- | --- | --- |
| `FS` | this folder | what Forge must do |
| `STRAT` | [strategies.md](strategies.md) | the predefined strategy bundle contract |
| `BENCH` | [benchmarking.md](benchmarking.md) | the generation benchmark contract |

| ID | Subject |
| --- | --- |
| §FS-forge-functional-spec | Forge functional specification |
| §FS-forge-issue-resolution-goal | Forge issue resolution goal |
| §FS-forge-scope | Supported issue queues |
| §FS-forge-glossary | Glossary |
| §FS-forge-requirements | Requirement gates |
| §FS-forge-host-requirements | Host requirements |
| §FS-forge-run-requirements | Run requirements |
| §FS-forge-outputs | Run outputs |
| §FS-forge-run-metrics | Per-run metrics record |
| §FS-durable-generation-logs | Durable generation and session logs |
| §FS-forge-publication-readiness | Publication readiness |
| §FS-local-ci-equivalent-verification | Local pre-publication verification |
| §FS-native-test-verification-gate | Native test verification gate |
| §FS-local-branch-review | Local pre-push branch review |
| §FS-library-update-tested-version-split | Library-update tested-version split |
| §FS-human-intervention-policy | Human intervention policy |
| §FS-automated-pr-review | Automated pull request review |
| §FS-forge-run-status | Run status semantics |
| §FS-forge-chunked-dynamic-access | Chunked dynamic-access semantics |
| §FS-forge-workflow-spec-catalog | Workflow specifications catalog |
| §FS-forge-run-continuation | Run continuation and resume |
| §STRAT-workflow-strategy-registry | Predefined strategy configuration architecture |
| §STRAT-forge-predefined-strategy-contract | Predefined strategy configuration contract |
| §STRAT-predefined-strategy-loader | Strategy loading boundary |
| §STRAT-predefined-strategy-fields | Strategy bundle fields |
| §STRAT-predefined-strategy-example | Representative predefined strategy bundle |
| §STRAT-predefined-strategy-parameter-families | Parameter families |
| §STRAT-java-fail-fix-composite-strategy-config | Java fail-fix composite strategy configuration |
| §STRAT-predefined-strategy-extension | Adding or changing a strategy bundle |
| §BENCH-forge-generation-benchmarking | Forge generation benchmarking |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [functional-spec.md](functional-spec.md) — the specification itself:
  requirements, outputs, verification, and the policies that decide a run's
  outcome. Start here.
- [continuation.md](continuation.md) — the continuation marker, and how a failed
  run resumes at the phase that failed.
- [strategies.md](strategies.md) — what a strategy bundle must declare, and how
  one is loaded, extended, and bound to a workflow engine.
- [benchmarking.md](benchmarking.md) — what a generation benchmark must measure
  and record when comparing strategies.
