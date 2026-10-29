# Functional spec

Forge behavior and contributor-facing requirements live here. A document belongs
in this directory when it states *what* Forge must do — a requirement a run can
be held to, a gate it must pass, a contract that decides an outcome. How Forge
is built belongs in [../architecture/](../architecture/README.md) — including the
workflow engines that execute a run
([workflows.md](../architecture/workflows.md)) and the drivers that prepare one
([drivers.md](../architecture/drivers.md)).

One kind lives here: every declaration in this folder is an `FS-` ID, whichever
file it sits in. A contract that belongs to one component — the strategy bundle
contract in [strategies.md](strategies.md), the benchmark contract in
[benchmarking.md](benchmarking.md) — is grouped by file rather than by prefix.

| ID | Subject |
| --- | --- |
| [§FS-forge-functional-spec](functional-spec.md#fs-forge-functional-spec-forge-functional-specification) | Forge functional specification |
| [§FS-forge-issue-resolution-goal](functional-spec.md#fs-forge-issue-resolution-goal-forge-issue-resolution-goal) | Forge issue resolution goal |
| [§FS-forge-scope](functional-spec.md#fs-forge-scope-supported-issue-queues) | Supported issue queues |
| [§FS-forge-glossary](functional-spec.md#fs-forge-glossary-glossary) | Glossary |
| [§FS-forge-requirements](functional-spec.md#fs-forge-requirements-requirement-gates) | Requirement gates |
| [§FS-forge-host-requirements](functional-spec.md#fs-forge-host-requirements-host-requirements) | Host requirements |
| [§FS-forge-agent-runtime-selection](functional-spec.md#fs-forge-agent-runtime-selection-two-agent-inputs) | Two agent inputs |
| [§FS-forge-run-requirements](functional-spec.md#fs-forge-run-requirements-run-requirements) | Run requirements |
| [§FS-forge-outputs](functional-spec.md#fs-forge-outputs-run-outputs) | Run outputs |
| [§FS-forge-run-metrics](functional-spec.md#fs-forge-run-metrics-per-run-metrics-record) | Per-run metrics record |
| [§FS-durable-generation-logs](functional-spec.md#fs-durable-generation-logs-durable-generation-and-session-logs) | Durable generation and session logs |
| [§FS-forge-run-location-reporting](functional-spec.md#fs-forge-run-location-reporting-run-location-in-progress-and-failure-output) | Run location in progress and failure output |
| [§FS-forge-publication-readiness](publication.md#fs-forge-publication-readiness-publication-readiness) | Publication readiness |
| [§FS-local-ci-equivalent-verification](publication.md#fs-local-ci-equivalent-verification-local-pre-publication-verification) | Local pre-publication verification |
| [§FS-native-test-verification-gate](publication.md#fs-native-test-verification-gate-native-test-verification-gate) | Native test verification gate |
| [§FS-local-branch-review](publication.md#fs-local-branch-review-local-pre-push-audit) | Local pre-push audit |
| [§FS-library-update-tested-version-split](tested-version-split.md#fs-library-update-tested-version-split-library-update-tested-version-split) | Library-update tested-version split |
| [§FS-human-intervention-policy](publication.md#fs-human-intervention-policy-human-intervention-policy) | Human intervention policy |
| [§FS-published-pr-review](published-pr-review.md#fs-published-pr-review-review-of-a-published-pull-request) | Review of a published pull request |
| [§FS-forge-run-status](functional-spec.md#fs-forge-run-status-run-status-semantics) | Run status semantics |
| [§FS-unsupportable-version-diagnosis](functional-spec.md#fs-unsupportable-version-diagnosis-unsupportable-version-verdict-in-setup) | Unsupportable-version verdict in setup |
| [§FS-not-for-native-image-record](not-for-native-image-record.md#fs-not-for-native-image-record-not-for-native-image-record-in-prepare) | Not-for-native-image record in prepare |
| [§FS-forge-run-output-legibility](functional-spec.md#fs-forge-run-output-legibility-legible-run-output) | Legible run output |
| [§FS-forge-chunked-dynamic-access](functional-spec.md#fs-forge-chunked-dynamic-access-chunked-dynamic-access-semantics) | Chunked dynamic-access semantics |
| [§FS-forge-workflow-spec-catalog](functional-spec.md#fs-forge-workflow-spec-catalog-workflow-specifications) | Workflow specifications catalog |
| [§FS-workflow-strategy-registry](strategies.md#fs-workflow-strategy-registry-predefined-strategy-registry-retired) | Predefined strategy registry (retired) |
| [§FS-forge-predefined-strategy-contract](strategies.md#fs-forge-predefined-strategy-contract-predefined-strategy-contract-retired) | Predefined strategy contract (retired) |
| [§FS-predefined-strategy-parameter-families](strategies.md#fs-predefined-strategy-parameter-families-parameter-families-retired) | Parameter families (retired) |
| [§FS-forge-generation-benchmarking](benchmarking.md#fs-forge-generation-benchmarking-forge-generation-benchmarking-retired) | Forge generation benchmarking (retired) |
| [§FS-code-coverage-benchmarking](benchmarking.md#fs-code-coverage-benchmarking-code-coverage-improvement-benchmarking) | Code coverage improvement benchmarking |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [functional-spec.md](functional-spec.md) — the specification itself:
  requirements, outputs, verification, and the policies that decide a run's
  outcome. Start here.
- [tested-version-split.md](tested-version-split.md) — how a library-update run
  splits the entries that can no longer share its regenerated suite.
- [strategies.md](strategies.md) — retired: strategies are fixed per label.
- [benchmarking.md](benchmarking.md) — what the code coverage benchmark must
  measure and record when comparing agent configurations.
