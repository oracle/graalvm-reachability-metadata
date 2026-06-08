# CI-repository-ci: Recurring CI workflows and composite actions

This is the inventory and behavioral contract for the repository's continuous
integration: every GitHub Actions workflow, composite action, and shared script
that runs on a schedule or in response to a pull request or issue. CI is the
authoritative quality gate — local runs are best-effort, and no metadata ships
without passing the relevant gates here (§FS-repository-functional-spec.4,
§GOAL-protect-shipped-metadata). The normative run-size limits and per-gate
requirements are stated in the functional spec's CI-gates section; this document
maps each requirement to the concrete workflow that enforces it.

Each workflow under `.github/workflows/`, and each composite action under
`.github/actions/`, carries a `§`-citation back to its declaration below, so the
source of truth for *what a workflow does* is this document and the workflow
file points here rather than the reverse. Mutating scheduled steps run only on
the canonical `oracle/graalvm-reachability-metadata` repository.

## CI-matrix-source: Matrix source of truth (`ci.json`)

`ci.json` is the single source of truth for which GraalVM JDK versions and OS
runners the test matrix uses, plus the build arguments for the build. The Gradle
matrix-generation tasks (§TCK-test-harness) read it and emit the GitHub Actions
matrices the test workflows consume. It supports optional per-native-image-mode
Java overrides via `nativeImageModeJavaVersions` when a mode should run on only a
subset of the configured JDKs. Because the whole matrix derives from this file,
changing it re-runs everything: the infrastructure workflow
(§CI-test-changed-infrastructure) treats a `ci.json` change as an infrastructure
change.

## Pull-request validation workflows

These run on `pull_request` and are the per-PR merge gates
(§FS-repository-functional-spec.5.3). Each gates its real steps on the
`detect-file-changes` action (§CI-detect-file-changes) so a PR that does not
touch the guarded paths is a no-op.

### CI-test-changed-metadata: Test changed metadata

Triggers on PRs touching `metadata/` or `tests/src/`. Runs
`generateChangedMetadataTestMatrix` with the PR base/head SHAs to test only what
changed: it batches each changed metadata version's `tested-versions` into chunks
of up to 30 versions per job, includes newly added tested versions from
artifact-level index diffs, pulls allowed Docker images, disables Docker
networking, validates config, and runs only that batch (§METADATA-suite,
§TESTS-suite).

### CI-test-changed-infrastructure: Test changed infrastructure

Triggers on PRs touching `tests/tck-build-logic/`, `gradle/`, `build.gradle`,
`settings.gradle`, or `gradle.properties`. Runs
`generateInfrastructureChangedCoordinatesMatrix`, then pulls allowed images,
disables Docker networking, validates config, and runs tests for the affected
coordinates. This is the CI counterpart of the local infrastructure end-to-end
tests (§E2E-infrastructure-tests).

### CI-test-affected-spring-aot: Test affected Spring AOT smoke tests

Triggers on PRs touching `metadata/`. Runs `generateAffectedSpringTestMatrix` to
compute impacted Spring AOT projects, then runs triaged native tests via
`run-spring-aot-triaged-test.sh` (§CI-shared-scripts). Only runs when metadata
changes actually affect a Spring AOT project (§FS-repository-functional-spec.5.3).

### CI-index-file-validation: Validate index.json files

Triggers on PRs touching `metadata/*/*/index.json`. Runs
`generateChangedIndexFileCoordinatesList` to find the changed index coordinates,
then `validateIndexFiles` to check index schema and
`metadata-version`/`tested-versions` consistency (§METADATA-suite).

### CI-library-stats-validation: Validate library stats

Triggers on PRs changing exploded stats files under `stats/`, the stats schema,
or mirrored files under `metadata/`. Runs `validateLibraryStats` to enforce
schema compliance and normalized sorting of the `stats/` files, including the
Forge run records (§forge/FS-forge-run-metrics, §FS-repository-functional-spec.4.1).

### CI-library-and-framework-list-validation: Validate library-and-framework list

Triggers on PRs touching `metadata/library-and-framework-list.json` or its
schema. Validates and sorts the master supported-library list against its schema
(§METADATA-suite).

### CI-checkstyle: Checkstyle

Triggers on PRs that change code (excluding `docs/**`, `**.md`, and the framework
list). Runs the `checkstyle` harness task against the changed coordinates; the
source of truth for style rules is `gradle/checkstyle.xml` (§TCK-test-harness).

### CI-scan-docker-images: Scan Docker images

On PRs touching `allowed-docker-images/**`, runs `checkAllowedDockerImages`
between the base and head commits to scan only the changed images with grype; on
a weekly schedule (`0 0 * * 6`) it scans all allowed images. Image management is
part of the harness (§TCK-test-harness).

### CI-sync-docker-images: Sync Docker images

On PRs touching `allowed-docker-images/**` (Dependabot updates), synchronizes
Docker image tags across the repository and back-commits the synchronized tags
directly into the Dependabot PR, making it merge-ready (§FS-repository-functional-spec.5.3).

### CI-macaron-check: Check GitHub Actions with Macaron

On PRs touching `.github/workflows/**` and on manual dispatch, runs Oracle
Macaron against the `check-github-actions` policy to enforce supply-chain hygiene
on the workflow definitions themselves (for example, pinned action SHAs).

## Scheduled workflows

These run on `cron` (and usually `workflow_dispatch`) and keep coverage current
and releases flowing without a human in the loop.

### CI-test-all-metadata: Test all metadata

Every Sunday (`0 2 * * 0`) and on manual dispatch. Uses
`generateMatrixBatchedCoordinates` with 85 batches to build a JDK/OS matrix, runs
the full `test` lane, pulls only allowed images, then disables Docker networking.
Failed batches are isolated down to concrete library versions, publish result
and failure-log artifacts, and fail in the matrix so the Actions UI points at
the failing batch. The aggregate job remains release-blocking when failures are
found (§FS-repository-functional-spec.5.3) and gates the scheduled release
(§CI-create-scheduled-release).

### CI-verify-new-library-version-compatibility: Verify new library version compatibility

Every six hours (`0 */6 * * *`) and on manual dispatch. Owns the upstream-version
tracking loop fully specified in §FS-repository-functional-spec.9
(§GOAL-broad-version-coverage, §GOAL-fresh-metadata): it discovers newer versions
with `fetchExistingLibrariesWithNewerVersions`, builds a matrix with
`generateNewLibraryVersionCompatibilityMatrix` (capped per
§FS-repository-functional-spec.5.3), tests every candidate across the matrix
using `run-consecutive-tests.sh` (§CI-shared-scripts), records versions that pass
everywhere via `addTestedVersion` in one
`library-bulk-update` PR (refreshing the root coverage table), and files one
aggregated `fails-*` failure issue per failing version. Those failure issues are
the entry point of the Forge repair queue (§forge/FS-forge-functional-spec).

### CI-publish-scheduled-coverage: Publish scheduled coverage

Every two hours (`15 */2 * * *`) and on manual dispatch. Regenerates the coverage
badges and dashboard from committed `stats/` and `metadata/**/index.json`
(§FS-repository-functional-spec.4.5), then force-pushes the published artifacts
to the `stats/coverage` branch. The published branch keeps only `COVERAGE.md`,
`latest/badges.json`,
`latest/metrics-over-time.svg`, `latest/metrics-over-time-dark.svg`, and
`history/history.json`.

### CI-create-scheduled-release: Create scheduled release

Every Monday (`0 3 * * 1`) and on manual dispatch. Packages metadata only if it
changed and the latest completed test-all-metadata workflow passed
(§CI-test-all-metadata); runs `spotlessCheck` before packaging
(§FS-repository-functional-spec.5.3). Manual dispatches bypass the test-all gate.
The workflow ignores the floating `latest` tag when choosing the previous
numbered release tag, then creates the next `<major>.<minor>.<patch>` release.
The packaged ZIP is the numbered artifact native-build-tools consumes
(§FS-repository-functional-spec.4, §GOAL-fresh-metadata).

### CI-create-latest-release: Create latest release

On pushes to `master` and on manual dispatch. Publishes a floating `latest`
GitHub Release when metadata changed since the previous `latest` tag; if that
tag does not exist yet, it bootstraps the diff from the latest numbered release
tag. The workflow packages metadata with repository version `latest`, deletes
the previous `latest` release/tag when present, and force-pushes a fresh
`latest` tag for consumers that need a continuously updated snapshot-style
bundle (§FS-repository-functional-spec.4.4, §GOAL-fresh-metadata).

## Event-triggered automation

### CI-triage-new-issues: Triage new issues

Runs when a GitHub issue is opened (ignores pull requests). For user-created
requests, the `library-new-request` label from the issue template makes the issue
eligible. For automated native-build-tools issues with no labels and the standard
`Support for groupId:artifactId:version` title, the workflow adds
`library-new-request` and `priority` first. Once eligible it extracts and
validates the Maven coordinates, closes invalid/duplicate/already-supported
requests, and — via `open-dependency-issues-and-link-blockers.js`
(§CI-shared-scripts) — generates a deps.dev dependency graph and opens or reuses
`library-new-request` issues for unsupported transitive dependencies, linking
them as blockers. The label vocabulary it applies is defined in
§FS-repository-functional-spec.4.

## Composite actions

### CI-detect-file-changes: detect-file-changes action

A `node20` action that returns `changed: true/false` for whether any file
matching the given glob `file-patterns` changed in the current PR (supports
`**`, `*`, `?`, and `!` negation). Every PR-validation workflow above gates its
real steps on this output so unrelated PRs cost almost nothing.

### CI-setup-native-build-tools: setup-native-build-tools action

A composite action that publishes and uses a native-build-tools (NBT) snapshot
when a branch with the same name as the caller's branch exists in the NBT
repository (or when `enabled-by-default` is set). It checks out that NBT ref,
reads `nativeBuildTools` from its `libs.versions.toml`, publishes it to
`mavenLocal`, and updates the caller repo's catalog to match — so a PR can be
tested against an in-progress NBT change just by matching branch names.

## CI-shared-scripts: Shared scripts and test isolation

Helpers under `.github/workflows/scripts/` used by the workflows above (these are
`.sh`/`.js` files and are referenced from the workflows, not cited individually):

- **Docker isolation** — `disable-docker.sh` / `restore-docker.sh`, plus
  `dockerd.service` and `discard-port.conf`. After allowed images are pulled, CI
  disables Docker networking so tests run deterministically against pre-pulled
  images with no network access (§FS-repository-functional-spec.5.3,
  §FS-repository-functional-spec.6). You usually do not need this locally.
- **Consecutive version testing** — `run-consecutive-tests.sh` walks a library's
  candidate versions in ascending order, runs the full `test` lane per version,
  and on failure bisects the failing stage (`compileTestJava` → `javaTest` →
  `nativeTestCompile` → `test`) to classify it, recording the first failing
  version and stopping the chain. Drives §CI-verify-new-library-version-compatibility.
- **Spring AOT triage** — `run-spring-aot-triaged-test.sh` runs the triaged
  native Spring AOT smoke tests for the projects computed by
  §CI-test-affected-spring-aot.
- **Dependency issue linking** — `open-dependency-issues-and-link-blockers.js`
  opens/reuses issues for unsupported transitive dependencies and links them as
  blockers, used by §CI-triage-new-issues.
