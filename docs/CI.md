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
- Gradle tasks read it to generate GitHub Actions matrices consumed by the workflows below.
- Way to define build arguments for the build. If this file is changed everything is re-run.

Workflows testing metadata using [ci.json](../ci.json):
- Test all metadata ([.github/workflows/test-all-metadata.yml](../.github/workflows/test-all-metadata.yml))
  - Triggers: manual (workflow_dispatch) and PRs that touch [ci.json](../ci.json).
  - Uses: generateMatrixBatchedCoordinates to build a matrix (java + os). Runs full tests, pulls only allowed Docker images, then disables Docker networking for deterministic runs.
- Test changed metadata ([.github/workflows/test-changed-metadata.yml](../.github/workflows/test-changed-metadata.yml))
  - Triggers: PRs to master touching [metadata/](metadata/) or [tests/src/](tests/src/).
  - Uses: generateChangedCoordinatesMatrix with base/head SHAs to test only what changed. Pulls allowed images, disables Docker networking, validates config, then runs tests.
- Test changed build logic ([.github/workflows/test-changed-infrastructure.yml](../.github/workflows/test-changed-infrastructure.yml))
  - Triggers: PRs to master touching [tests/tck-build-logic/](tests/tck-build-logic/), [gradle/](gradle/), [build.gradle](../build.gradle), [settings.gradle](../settings.gradle), or [gradle.properties](../gradle.properties).
  - Uses: generateInfrastructureChangedCoordinatesMatrix. Pulls allowed images, disables Docker networking, validates config, then runs tests.
- Test affected Spring AOT smoke tests ([.github/workflows/test-affected-spring-aot-3.5.x.yml](../.github/workflows/test-affected-spring-aot-3.5.x.yml), [.github/workflows/test-affected-spring-aot-4.0.x.yml](../.github/workflows/test-affected-spring-aot-4.0.x.yml), [.github/workflows/test-affected-spring-aot-main.yml](../.github/workflows/test-affected-spring-aot-main.yml))
  - Triggers: PRs to master touching [metadata/](metadata/).
  - Uses: generateAffectedSpringTestMatrix to compute impacted Spring AOT projects and produce a matrix; runs triaged native tests via [.github/workflows/scripts/run-spring-aot-triaged-test.sh](../.github/workflows/scripts/run-spring-aot-triaged-test.sh).

Workflow for testing latest library versions from Maven: [.github/workflows/verify-new-library-version-compatibility.yml](../.github/workflows/verify-new-library-version-compatibility.yml): scheduled verifier for newer upstream library versions; uses pinned Java/OS in the workflow.

Workflows for style and security:
- [.github/workflows/checkstyle.yml](../.github/workflows/checkstyle.yml): code style checks.
- [.github/workflows/library-and-framework-list-validation.yml](../.github/workflows/library-and-framework-list-validation.yml): validates and sorts [library-and-framework-list.json](../library-and-framework-list.json), checks schema.
- [.github/workflows/scan-docker-images.yml](../.github/workflows/scan-docker-images.yml): scans allowed Docker images on PR/schedule.
- [.github/workflows/sync-docker-tags.yml](../.github/workflows/sync-docker-tags.yml): automatically synchronizes Docker image tags across the repository when Dependabot updates `allowed-docker-images`. Commits replacements directly into the Dependabot PR, making it merge-ready.
