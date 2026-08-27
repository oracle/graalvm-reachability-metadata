# Architecture

This directory holds everything that explains *how* the repository is wired —
build logic, task graphs, the CI that drives them, directory layout, component
boundaries. What the repository must *do* belongs in
[../functional-spec/](../functional-spec/README.md) instead.

One kind lives here: every declaration in this folder is an `AR-` ID, whichever
file it sits in. The harness task surface and the CI that drives it are
architecture like the rest, so they carry the same prefix and are grouped by file
rather than by kind.

| ID | Subject |
| --- | --- |
| [§AR-repository-architecture](architecture.md#ar-repository-architecture-repository-architecture-and-high-level-overview) | Repository architecture and high-level overview |
| [§AR-build-infrastructure](build-infra.md#ar-build-infrastructure-build-infrastructure) | Build infrastructure |
| [§AR-repository-ci](ci.md#ar-repository-ci-recurring-ci-workflows-and-composite-actions) | Recurring CI workflows and composite actions |
| [§AR-matrix-source](ci.md#ar-matrix-source-matrix-source-of-truth-cijson) | Matrix source of truth (`ci.json`) |
| [§AR-test-changed-metadata](ci.md#ar-test-changed-metadata-test-changed-metadata) | Test changed metadata |
| [§AR-test-changed-infrastructure](ci.md#ar-test-changed-infrastructure-test-changed-infrastructure) | Test changed infrastructure |
| [§AR-test-affected-spring-aot](ci.md#ar-test-affected-spring-aot-test-affected-spring-aot-smoke-tests) | Test affected Spring AOT smoke tests |
| [§AR-index-file-validation](ci.md#ar-index-file-validation-validate-indexjson-files) | Validate index.json files |
| [§AR-library-stats-validation](ci.md#ar-library-stats-validation-validate-library-stats) | Validate library stats |
| [§AR-library-and-framework-list-validation](ci.md#ar-library-and-framework-list-validation-validate-library-and-framework-list) | Validate library-and-framework list |
| [§AR-grund-check](ci.md#ar-grund-check-validate-grund-citations) | Validate grund citations |
| [§AR-checkstyle](ci.md#ar-checkstyle-checkstyle) | Checkstyle |
| [§AR-scan-docker-images](ci.md#ar-scan-docker-images-scan-docker-images) | Scan Docker images |
| [§AR-sync-docker-images](ci.md#ar-sync-docker-images-sync-docker-images) | Sync Docker images |
| [§AR-macaron-check](ci.md#ar-macaron-check-check-github-actions-with-macaron) | Check GitHub Actions with Macaron |
| [§AR-layered-tests](ci.md#ar-layered-tests-shared-and-dedicated-native-image-layer-tests) | Shared and dedicated Native Image layer tests |
| [§AR-test-all-metadata](ci.md#ar-test-all-metadata-test-all-metadata) | Test all metadata |
| [§AR-verify-new-library-version-compatibility](ci.md#ar-verify-new-library-version-compatibility-verify-new-library-version-compatibility) | Verify new library version compatibility |
| [§AR-publish-scheduled-coverage](ci.md#ar-publish-scheduled-coverage-publish-scheduled-coverage) | Publish scheduled coverage |
| [§AR-create-scheduled-release](ci.md#ar-create-scheduled-release-create-scheduled-release) | Create scheduled release |
| [§AR-create-snapshot-release](ci.md#ar-create-snapshot-release-create-snapshot-release) | Create snapshot release |
| [§AR-test-all-metadata-crema](ci.md#ar-test-all-metadata-crema-test-all-metadata-on-the-crema-jvm) | Test all metadata on the Crema JVM |
| [§AR-triage-new-issues](ci.md#ar-triage-new-issues-triage-new-issues) | Triage new issues |
| [§AR-detect-file-changes](ci.md#ar-detect-file-changes-detect-file-changes-action) | detect-file-changes action |
| [§AR-setup-native-build-tools](ci.md#ar-setup-native-build-tools-setup-native-build-tools-action) | setup-native-build-tools action |
| [§AR-setup-native-image-base-layer](ci.md#ar-setup-native-image-base-layer-setup-native-image-base-layer-action) | setup-native-image-base-layer action |
| [§AR-setup-crema-jdk](ci.md#ar-setup-crema-jdk-setup-crema-jdk-action) | setup-crema-jdk action |
| [§AR-shared-scripts](ci.md#ar-shared-scripts-shared-scripts-and-test-isolation) | Shared scripts and test isolation |
| [§AR-test-harness](tck.md#ar-test-harness-test-harness-tck-task-groups) | Test harness (TCK) task groups |

This index is navigational. Cite the specific declaration ID rather than this
file.

Files:

- [architecture.md](architecture.md) — the repository as a whole: components,
  how work flows through the system, and the implementation overview.
- [build-infra.md](build-infra.md) — the two-layer Gradle build, convention
  plugins, and scaffolding the suites run on.
- [tck.md](tck.md) — the harness task groups: selection, validation, test lanes,
  tracing, authoring, matrices, and reporting.
- [ci.md](ci.md) — the workflows, composite actions, and shared scripts, one
  citable declaration each.
