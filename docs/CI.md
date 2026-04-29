# Continuous Integration (CI)

This repository uses GitHub Actions and Gradle-based checks to ensure metadata quality, stability, and automated releases.
Below is an overview of the CI pipelines and the configuration they rely on.

- GitHub Actions workflows: [.github/workflows](../.github/workflows)/*.yml
- Source of truth for style checks: [gradle/checkstyle.xml](../gradle/checkstyle.xml)

CI prefetches docker images and temporarily disables Docker networking for test isolation via [`.github/workflows/disable-docker.sh`](../.github/workflows/scripts/disable-docker.sh).
You usually do not need this locally.

## Types of jobs in the CI

The release is made every two weeks if there are metadata changes: [.github/workflows/create-scheduled-release.yml](../.github/workflows/create-scheduled-release.yml).

The test matrix definition starts with [ci.json](../ci.json):
- A single source of truth for which Java versions and OS runners to test on.
- Supports optional per-native-image-mode Java overrides via `nativeImageModeJavaVersions` when a mode should run on only a subset of the configured JDKs.
- Gradle tasks read it to generate GitHub Actions matrices consumed by the workflows below.
- Way to define build arguments for the build. If this file is changed everything is re-run.

Workflows testing metadata using [ci.json](../ci.json):
- Test all metadata ([.github/workflows/test-all-metadata.yml](../.github/workflows/test-all-metadata.yml))
  - Triggers: manual (workflow_dispatch) and PRs that touch [ci.json](../ci.json).
  - Uses: generateMatrixBatchedCoordinates to build a matrix (java + os). Runs full tests, pulls only allowed Docker images, then disables Docker networking for deterministic runs.
- Test changed metadata ([.github/workflows/test-changed-metadata.yml](../.github/workflows/test-changed-metadata.yml))
  - Triggers: PRs to master touching [metadata/](metadata/) or [tests/src/](tests/src/).
  - Uses: [`generateChangedMetadataTestMatrix`](../tests/tck-build-logic/src/main/groovy/org.graalvm.internal.tck-harness.gradle) with base/head SHAs to test only what changed. It batches each changed metadata version's [`tested-versions`](../metadata/com.google.protobuf/protobuf-java-util/index.json) into chunks of up to 30 versions per job, then pulls allowed images, disables Docker networking, validates config, and runs only that batch.
- Test new library versions ([.github/workflows/test-new-library-versions.yml](../.github/workflows/test-new-library-versions.yml))
  - Triggers: PRs to master touching artifact-level [metadata/**/index.json](../metadata/).
  - Uses: [`generateChangedTestedVersionsMatrix`](../tests/tck-build-logic/src/main/groovy/org.graalvm.internal.tck-harness.gradle) to compute only newly added [`tested-versions`](../metadata/com.google.protobuf/protobuf-java-util/index.json) from the base/head diff and pairs them with the matching [`metadata-version`](../metadata/com.google.protobuf/protobuf-java-util/index.json), then runs [`run-consecutive-tests.sh`](../.github/workflows/scripts/run-consecutive-tests.sh) only for those added versions.
- Test changed build logic ([.github/workflows/test-changed-infrastructure.yml](../.github/workflows/test-changed-infrastructure.yml))
  - Triggers: PRs to master touching [tests/tck-build-logic/](tests/tck-build-logic/), [gradle/](gradle/), [build.gradle](../build.gradle), [settings.gradle](../settings.gradle), or [gradle.properties](../gradle.properties).
  - Uses: generateInfrastructureChangedCoordinatesMatrix. Pulls allowed images, disables Docker networking, validates config, then runs tests.
- Test affected Spring AOT smoke tests ([.github/workflows/test-affected-spring-aot-main.yml](../.github/workflows/test-affected-spring-aot-main.yml))
  - Triggers: PRs to master touching [metadata/](metadata/).
  - Uses: generateAffectedSpringTestMatrix to compute impacted Spring AOT projects and produce a matrix; runs triaged native tests via [.github/workflows/scripts/run-spring-aot-triaged-test.sh](../.github/workflows/scripts/run-spring-aot-triaged-test.sh).
- Validate library stats ([.github/workflows/library-stats-validation.yml](../.github/workflows/library-stats-validation.yml))
  - Triggers: PRs to master that change exploded stats files under [stats/](../stats/), [stats/schemas/library-stats-schema-v1.0.2.json](../stats/schemas/library-stats-schema-v1.0.2.json), or mirrored files under [metadata/](../metadata/).
  - Uses: [`validateLibraryStats`](../tests/tck-build-logic/src/main/groovy/org.graalvm.internal.tck-harness.gradle) to enforce schema compliance and normalized sorting.
- Verify new library version compatibility ([.github/workflows/verify-new-library-version-compatibility.yml](../.github/workflows/verify-new-library-version-compatibility.yml))
  - Triggers: every 8 hours and manual ([`workflow_dispatch`](../.github/workflows/verify-new-library-version-compatibility.yml)).
  - Uses: [`fetchExistingLibrariesWithNewerVersions`](../tests/tck-build-logic/src/main/groovy/org.graalvm.internal.tck-harness.gradle) plus [`generateNewLibraryVersionCompatibilityMatrix`](../tests/tck-build-logic/src/main/groovy/org.graalvm.internal.tck-harness.gradle) to build a [`ci.json`](../ci.json)-driven matrix. The matrix generator limits the number of libraries per run, limits each library to at most 30 newer versions per run before expanding across the configured GraalVM JDK/OS combinations, records tested versions only after they pass on every required environment, and creates a single aggregated failure issue per failed library version while preserving the prior failure issue format with added OS and JDK lines.

Workflows for style and security:
- [.github/workflows/checkstyle.yml](../.github/workflows/checkstyle.yml): code style checks.
- [.github/workflows/library-and-framework-list-validation.yml](../.github/workflows/library-and-framework-list-validation.yml): validates and sorts [library-and-framework-list.json](../metadata/library-and-framework-list.json), checks schema.
- [.github/workflows/library-stats-validation.yml](../.github/workflows/library-stats-validation.yml): validates and sorts mirrored stats files under [stats/](../stats/), checks schema.
- [.github/workflows/publish-scheduled-coverage.yml](../.github/workflows/publish-scheduled-coverage.yml): scheduled/manual workflow that generates repository coverage badges and the coverage dashboard from exploded stats files under [stats/](../stats/) and repository [metadata/**/index.json](../metadata/), then force-pushes the published artifacts to the `stats/coverage` branch. The published branch only keeps `latest/badges.json`, `latest/metrics-over-time.svg`, and `history/history.json`.
- [.github/workflows/scan-docker-images.yml](../.github/workflows/scan-docker-images.yml): scans allowed Docker images on PR/schedule.
- [.github/workflows/sync-docker-tags.yml](../.github/workflows/sync-docker-tags.yml): automatically synchronizes Docker image tags across the repository when Dependabot updates `allowed-docker-images`. Commits replacements directly into the Dependabot PR, making it merge-ready.

## Native Build Tools snapshot setup

The `setup-native-build-tools` action automatically publishes and uses an NBT snapshot if a branch with the same name exists in the native-build-tools repository. To test against a specific NBT branch, simply ensure both branches share the same name.
