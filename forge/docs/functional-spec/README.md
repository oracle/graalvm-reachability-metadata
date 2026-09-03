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
| [§FS-foreign-metadata-owner-follow-ups](functional-spec.md#fs-foreign-metadata-owner-follow-ups-foreign-metadata-owner-follow-ups) | Foreign metadata owner follow-ups |
| [§FS-forge-glossary](functional-spec.md#fs-forge-glossary-glossary) | Glossary |
| [§FS-forge-requirements](functional-spec.md#fs-forge-requirements-requirement-gates) | Requirement gates |
| [§FS-forge-host-requirements](functional-spec.md#fs-forge-host-requirements-host-requirements) | Host requirements |
| [§FS-forge-agent-runtime-selection](functional-spec.md#fs-forge-agent-runtime-selection-configurable-agent-roles) | Configurable agent roles |
| [§FS-forge-run-requirements](functional-spec.md#fs-forge-run-requirements-run-requirements) | Run requirements |
| [§FS-forge-outputs](functional-spec.md#fs-forge-outputs-run-outputs) | Run outputs |
| [§FS-forge-run-metrics](functional-spec.md#fs-forge-run-metrics-per-run-metrics-record) | Per-run metrics record |
| [§FS-durable-generation-logs](functional-spec.md#fs-durable-generation-logs-durable-generation-and-session-logs) | Durable generation and session logs |
| [§FS-forge-run-location-reporting](functional-spec.md#fs-forge-run-location-reporting-run-location-in-progress-and-failure-output) | Run location in progress and failure output |
| [§FS-forge-publication-readiness](publication.md#fs-forge-publication-readiness-publication-readiness) | Publication readiness |
| [§FS-local-ci-equivalent-verification](publication.md#fs-local-ci-equivalent-verification-local-pre-publication-verification) | Local pre-publication verification |
| [§FS-native-test-verification-gate](publication.md#fs-native-test-verification-gate-native-test-verification-gate) | Native test verification gate |
| [§FS-local-branch-review](publication.md#fs-local-branch-review-local-pre-push-branch-review) | Local pre-push branch review |
| [§FS-library-update-tested-version-split](publication.md#fs-library-update-tested-version-split-library-update-tested-version-split) | Library-update tested-version split |
| [§FS-human-intervention-policy](publication.md#fs-human-intervention-policy-human-intervention-policy) | Human intervention policy |
| [§FS-automated-pr-review](publication.md#fs-automated-pr-review-automated-pull-request-review) | Automated pull request review |
| [§FS-forge-run-status](functional-spec.md#fs-forge-run-status-run-status-semantics) | Run status semantics |
| [§FS-forge-run-output-legibility](functional-spec.md#fs-forge-run-output-legibility-legible-run-output) | Legible run output |
| [§FS-forge-chunked-dynamic-access](functional-spec.md#fs-forge-chunked-dynamic-access-chunked-dynamic-access-semantics) | Chunked dynamic-access semantics |
| [§FS-forge-workflow-spec-catalog](functional-spec.md#fs-forge-workflow-spec-catalog-workflow-specifications) | Workflow specifications catalog |
| [§FS-forge-run-continuation](continuation.md#fs-forge-run-continuation-run-continuation-and-resume) | Run continuation and resume |
| [§FS-workflow-strategy-registry](strategies.md#fs-workflow-strategy-registry-predefined-strategy-configuration-architecture) | Predefined strategy configuration architecture |
| [§FS-forge-predefined-strategy-contract](strategies.md#fs-forge-predefined-strategy-contract-predefined-strategy-configuration-contract) | Predefined strategy configuration contract |
| [§FS-predefined-strategy-loader](strategies.md#fs-predefined-strategy-loader-strategy-loading-boundary) | Strategy loading boundary |
| [§FS-predefined-strategy-fields](strategies.md#fs-predefined-strategy-fields-strategy-bundle-fields) | Strategy bundle fields |
| [§FS-predefined-strategy-example](strategies.md#fs-predefined-strategy-example-representative-predefined-strategy-bundle) | Representative predefined strategy bundle |
| [§FS-predefined-strategy-parameter-families](strategies.md#fs-predefined-strategy-parameter-families-parameter-families) | Parameter families |
| [§FS-java-fail-fix-composite-strategy-config](strategies.md#fs-java-fail-fix-composite-strategy-config-java-fail-fix-composite-strategy-configuration) | Java fail-fix composite strategy configuration |
| [§FS-predefined-strategy-extension](strategies.md#fs-predefined-strategy-extension-adding-or-changing-a-strategy-bundle) | Adding or changing a strategy bundle |
| [§FS-forge-generation-benchmarking](benchmarking.md#fs-forge-generation-benchmarking-forge-generation-benchmarking) | Forge generation benchmarking |

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
