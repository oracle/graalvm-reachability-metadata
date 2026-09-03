# AR-repository-ci: Recurring CI workflows and composite actions

This is the inventory and behavioral contract for the repository's continuous
integration: every GitHub Actions workflow, composite action, and shared script
that runs on a schedule or in response to a pull request or issue. CI is the
authoritative quality gate — local runs are best-effort, and no metadata ships
without passing the relevant gates here (§FS-repository-functional-spec.4,
§GOAL-protect-shipped-metadata, §PRCPL-prefer-algorithmic). The normative run-size limits and per-gate
requirements are stated in the functional spec's CI-gates section; this document
maps each requirement to the concrete workflow that enforces it.

Each workflow under `.github/workflows/`, and each composite action under
`.github/actions/`, carries a `§`-citation back to its declaration below, so the
source of truth for *what a workflow does* is this document and the workflow
file points here rather than the reverse. Mutating scheduled steps run only on
the canonical `oracle/graalvm-reachability-metadata` repository.

## AR-matrix-source: Matrix source of truth (`ci.json`)

`ci.json` is the single source of truth for which GraalVM JDK versions and OS
runners the test matrix uses, plus the build arguments for the build. The Gradle
matrix-generation tasks (§AR-test-harness) read it and emit the GitHub Actions
matrices the test workflows consume. It supports optional per-native-image-mode
Java overrides via `nativeImageModeJavaVersions` when a mode should run on only a
subset of the configured JDKs. Because the whole matrix derives from this file,
changing it re-runs everything: the infrastructure workflow
(§AR-test-changed-infrastructure) treats a `ci.json` change as an infrastructure
change.
Serves §FS-repository-functional-spec.4.3 — ci.json is that section's single source of
truth for the OS/JDK matrix.

## Pull-request validation workflows

These run on `pull_request` and are the per-PR merge gates
(§FS-repository-functional-spec.5.3). Each gates its real steps on the
`detect-file-changes` action (§AR-detect-file-changes) so a PR that does not
touch the guarded paths is a no-op.

### AR-test-changed-metadata: Test changed metadata

Triggers on PRs touching `metadata/` or `tests/src/`. Runs
`generateChangedMetadataTestMatrix` with the PR base/head SHAs to test only what
changed: it batches each changed metadata version's `tested-versions` into chunks
of up to 30 versions per job, includes newly added tested versions from
artifact-level index diffs, pulls allowed Docker images, disables Docker
networking, validates config, and runs only that batch (§FS-metadata,
§FS-tests).

### AR-test-changed-infrastructure: Test changed infrastructure

Triggers on PRs touching `tests/tck-build-logic/`, `gradle/`, `build.gradle`,
`settings.gradle`, or `gradle.properties`. Runs
`generateInfrastructureChangedCoordinatesMatrix`, then pulls allowed images,
disables Docker networking, validates config, and runs tests for the affected
coordinates. This is the CI counterpart of the local `testInfra` and
`testAllInfra` harness tasks (§AR-test-harness).
Serves §FS-repository-functional-spec.4.3 — the PR-scoped changed-infrastructure lane.

### AR-test-affected-spring-aot: Test affected Spring AOT smoke tests

Triggers on PRs touching `metadata/`. Runs `generateAffectedSpringTestMatrix` to
compute impacted Spring AOT projects, then runs triaged native tests via
`run-spring-aot-triaged-test.sh` (§AR-shared-scripts). Only runs when metadata
changes actually affect a Spring AOT project (§FS-repository-functional-spec.5.3).

### AR-index-file-validation: Validate index.json files

Triggers on PRs touching `metadata/*/*/index.json`. Runs
`generateChangedIndexFileCoordinatesList` to find the changed index coordinates,
then `validateIndexFiles` to check index schema and
`metadata-version`/`tested-versions` consistency (§FS-metadata).

### AR-library-stats-validation: Validate library stats

Triggers on PRs changing exploded stats files under `stats/`, the stats schema,
or mirrored files under `metadata/`. Runs `validateLibraryStats` to enforce
schema compliance and normalized sorting of the `stats/` files, including the
Forge run records (§forge/FS-forge-run-metrics, §FS-repository-functional-spec.4.1).

### AR-library-and-framework-list-validation: Validate library-and-framework list

Triggers on PRs touching `metadata/library-and-framework-list.json` or its
schema. Validates and sorts the master supported-library list against its schema
(§FS-metadata).

### AR-grund-check: Validate grund citations

Triggers on PRs touching either project's scanned roots — `docs/`, `skills/`,
the workflows and composite actions, the top-level Markdown, or a `grund.toml`.
Installs a pinned `grund` release and runs `grund check` once from the
repository root, which is the only scope where the workspace alias table
resolves `§forge/<ID>`, so the run covers both namespaces. It fails on a
citation that no longer resolves, on an `AGENTS.md` managed block that has
drifted from its `grund.toml`, and on a `must`-level citation direction that is
not met (§FS-repository-functional-spec.5.6).

The `grund` version is pinned in the workflow rather than floating: a newer
release can render a newer managed block, which would fail every PR until
someone re-runs `grund init`. Bump the version and its checksum together.

### AR-checkstyle: Checkstyle

Triggers on PRs that change code (excluding `docs/**`, `**.md`, and the framework
list). Runs the `checkstyle` harness task against the changed coordinates; the
source of truth for style rules is `gradle/checkstyle.xml` (§AR-test-harness).
Serves §FS-repository-functional-spec.5.3 — checkstyle is one of the named PR gates.

### AR-scan-docker-images: Scan Docker images

On PRs touching `allowed-docker-images/**`, runs `checkAllowedDockerImages`
between the base and head commits to scan only the changed images with grype; on
a weekly schedule (`0 0 * * 6`) it scans all allowed images. Image management is
part of the harness (§AR-test-harness).
Serves §FS-repository-functional-spec.4.3 — the schedule-driven Docker image
vulnerability scan.

### AR-sync-docker-images: Sync Docker images

On PRs touching `allowed-docker-images/**` (Dependabot updates), synchronizes
Docker image tags across the repository and back-commits the synchronized tags
directly into the Dependabot PR, making it merge-ready (§FS-repository-functional-spec.5.3).

### AR-macaron-check: Check GitHub Actions with Macaron

On PRs touching `.github/workflows/**` and on manual dispatch, runs Oracle
Macaron against the `check-github-actions` policy to enforce supply-chain hygiene
on the workflow definitions themselves (for example, pinned action SHAs).
Serves §FS-repository-functional-spec.4.3 — it gates the workflow definitions that
section enumerates.

## Scheduled workflows

These run on `cron` (and usually `workflow_dispatch`) and keep coverage current
and releases flowing without a human in the loop.

### AR-layered-tests: Shared and dedicated Native Image layer tests

Every Sunday at 00:30 UTC (`30 0 * * 0`) and on manual dispatch. The scheduled
run checks every supported library on the default branch with GraalVM `latest-ea`
and the `current-defaults` Native Image mode, using the cached shared base layer
(§AR-test-harness.3). An `all` selection uses 16 independent shared-layer
shards and 128 independent dedicated-layer shards, allows up to 64 matrix jobs
to run in parallel, and permits six hours per shard. Manual dispatch defaults
to both lanes, `master`, `all`, and `latest-ea`; it may instead run only the
shared or dedicated lane and select a different ref, coordinate, JDK, or mode.
A concrete coordinate creates one job per selected lane. The scheduled
workflow runs both independent matrix lanes: the original shared JDK-layer
lane and a library-layer lane that creates one base layer per coordinate with
the tested library code included (§AR-test-harness.3).
Dedicated layers are deleted after each coordinate to bound runner disk use;
failures are collected independently so the two layer layouts remain directly
comparable.
Serves §FS-repository-functional-spec.5.4 — the Native Image mode this lane pins.

### AR-test-all-metadata: Test all metadata

Every Sunday (`0 2 * * 0`) and on manual dispatch. Uses
`generateMatrixBatchedCoordinates` with 85 batches to build a JDK/OS matrix, runs
the full `test` lane, pulls only allowed images, then disables Docker networking.
Failed batches are isolated down to concrete library versions, publish result
and failure-log artifacts, and fail in the matrix so the Actions UI points at
the failing batch. The aggregate job publishes a failure report when failures are
found (§FS-repository-functional-spec.5.3); it surfaces sweep regressions but does
not gate the scheduled release (§AR-create-scheduled-release).

### AR-verify-new-library-version-compatibility: Verify new library version compatibility

Every day (`0 0 * * *`) and on manual dispatch. Owns the upstream-version
tracking loop fully specified in §FS-repository-functional-spec.9
(§GOAL-broad-version-coverage, §GOAL-fresh-metadata): it discovers newer versions
only for libraries whose latest index entry sets `auto-update: true`, using
`fetchExistingLibrariesWithNewerVersions`, and builds a matrix with
`generateNewLibraryVersionCompatibilityMatrix` (capped per
§FS-repository-functional-spec.5.3), tests every candidate across the matrix
using `run-consecutive-tests.sh` (§AR-shared-scripts), records versions that pass
everywhere via `addTestedVersion` in one
`library-bulk-update` PR (refreshing the root coverage table), and files one
aggregated `fails-*` failure issue per failing version. A failure for an artifact
whose latest index entry sets `high-priority: true` also receives the
`high-priority` label. Those failure issues are
the entry point of the Forge repair queue (§forge/FS-forge-functional-spec).

### AR-publish-scheduled-coverage: Publish scheduled coverage

Every two hours (`15 */2 * * *`) and on manual dispatch. Regenerates the coverage
badges and dashboard from committed `stats/` and `metadata/**/index.json`
(§FS-repository-functional-spec.4.5), then force-pushes the published artifacts
to the `stats/coverage` branch. The published branch keeps only `COVERAGE.md`,
`latest/badges.json`,
`latest/metrics-over-time.svg`, `latest/metrics-over-time-dark.svg`, and
`history/history.json`.

### AR-create-scheduled-release: Create scheduled release

Every Monday (`0 3 * * 1`) and on manual dispatch. Packages metadata only if it
changed; runs `spotlessCheck` before packaging
(§FS-repository-functional-spec.5.3). It is deliberately not gated on the periodic
`test-all-metadata` sweep (§AR-test-all-metadata) so bleeding-edge sweep failures
cannot stall the release cadence.
The workflow considers only semantic version tags when choosing the previous
numbered release tag, so floating snapshot tags such as `SNAPSHOT` are ignored.
It then creates the next `<major>.<minor>.<patch>` release. The packaged ZIP is
the numbered artifact native-build-tools consumes (§FS-repository-functional-spec.4,
§GOAL-fresh-metadata).

### AR-create-snapshot-release: Create snapshot release

On pushes to `master` and on manual dispatch. Publishes a floating `SNAPSHOT`
GitHub Release on the `SNAPSHOT` tag when metadata changed since the previous
`SNAPSHOT` tag; if that tag does not exist yet, it bootstraps the diff from the
latest numbered release tag. The workflow packages metadata with repository
version `SNAPSHOT`, deletes the previous snapshot release/tag when present,
force-pushes a fresh `SNAPSHOT` tag, and marks the release as not GitHub's
Latest release (§FS-repository-functional-spec.4.4, §GOAL-fresh-metadata).

### AR-test-all-metadata-crema: Test all metadata on the Crema JVM

Every Saturday (`0 2 * * 6`) and on manual dispatch. Runs the repository's JVM
test lane against Crema — Native Image's run-time class loading VM — to find
where Crema cannot yet run real library test suites. It is a bug-finding sweep
aimed at Crema, not a metadata gate: `metadata/` correctness is not what it
measures, and its result never blocks a release. Saturday keeps it clear of the
Sunday metadata sweep (§AR-test-all-metadata) and the Monday release
(§AR-create-scheduled-release) so the three never compete for runners.
Serves §FS-repository-functional-spec.5.4 — the Crema lane is a Native Image mode.

The scheduled run takes every input's default — all coordinates over 85 shards on
the `crema` lane with assertions cleared — because the `inputs` context is empty
on a `schedule` event; the workflow restates each default rather than resolving
an empty matrix. The weekly trigger is confined to the canonical repository so a
fork does not sweep on its own schedule, while manual dispatch stays available
everywhere.

The JDK is built once by a dedicated job via §AR-setup-crema-jdk and shared with
every shard as an artifact, because the macro build takes about ten minutes and
produces the same library every time — building it per shard would spend hours of
runner time reproducing identical work. Debug info and sources are stripped
before upload as run-time-irrelevant bulk. A single JDK serves both lanes: the
action leaves `jvm.cfg.hotspot-default` in the tree next to the Crema-default
`jvm.cfg`, so a shard selects its VM by choosing between the two files. Each
shard re-asserts the VM identity after unpacking rather than trusting the build
job, so an unpack or selection mistake cannot let a shard silently measure the
wrong VM.

Shards resolving Maven Central concurrently draw HTTP 429, which fails a shard
during dependency resolution and loses every coordinate in it. Four measures
address it, in order of how directly they attack the cause. The build job
resolves the build-logic classpath once and shares it as a Gradle module cache,
so the sweep no longer repeats one identical request burst 85 times; shards
restore that cache read-only. `max-parallel` caps how many shards resolve at
once. The shard's setup commands are retried with minute-scale waits, because
both observed failures struck in setup before any coordinate ran — the
per-coordinate tests are deliberately *not* retried, so a genuine Crema failure
fails once and fast. Finally, Gradle's own retry backoff is stretched on both the
network-operation and module-repository layers, written to
`GRADLE_USER_HOME/gradle.properties`: `GRADLE_OPTS` configures the Gradle client
JVM while resolution runs in the daemon, which does not inherit those properties,
and the user-home file is also the only one every per-coordinate build reads,
each being a separate Gradle build rooted in its own directory.

A shard lost to rate limiting is never reported as green. Its coordinates went
untested, and a clean sweep over libraries nobody measured is precisely the
outcome this workflow's guards exist to prevent.

Each coordinate's test run is separately time-bounded. A Crema fatal error can
leave the test worker wedged with Gradle waiting on it indefinitely, which
consumed a whole shard and discarded every coordinate queued behind it before the
bound existed. A hang is itself a finding, so it is recorded against the one
library that caused it rather than being allowed to swallow its shard. The job's
own timeout stays below the hosted-runner ceiling so an overrun fails on the
workflow's terms instead of arriving as an unexplained cancellation.

Tests run through the ordinary `javaTest` lane (§AR-test-harness.3) with
`GVM_TCK_TEST_JAVA_HOME` pointing at that JDK (§AR-test-harness.3.1), so workers
execute on Crema while Gradle keeps running on the runner's stock JDK. No Crema-specific JVM flag is ever added: a
library that fails only because Crema rejects an argument the JVM lane normally
passes is a finding, not something the workflow works around. JaCoCo is disabled
via `-PskipJacoco=true` because Crema ignores `-javaagent`, which would otherwise
publish empty coverage as though it were real.

The `vm` input selects the lane: `crema`, `hotspot`, or `both`. `hotspot` runs
the identical tree on the identical JDK with the stock VM, and only
`crema`-fails-while-`hotspot`-passes is reportable — this repository has
coordinates that fail for environmental reasons under any VM. Failures are
attributed per coordinate and published as NDJSON plus log artifacts in the same
shape as §AR-test-all-metadata, so the existing failure tooling applies. The
matrix comes from `generateMatrixBatchedCoordinates` (§AR-test-harness.7) via
the `batches` input; `coordinates` narrows a run to one shard or one library.

Crema currently rejects `-ea` at VM startup, and Gradle puts `-ea` on every test
worker, so leaving it in place fails every coordinate identically before any test
executes and the sweep returns one finding repeated per coordinate instead of a
survey. The `disable-assertions` input therefore defaults to true, clearing
Gradle's `enableAssertions` (§AR-test-harness.3.1) so the sweep reaches the
failures behind that blocker. Setting it false re-checks whether the blocker is
still present. Because assertions do not fire under this default, tests that
verify via `assert` pass vacuously: a green coordinate here means Crema ran the
code, never that the library is supported.


## Event-triggered automation

### AR-triage-new-issues: Triage new issues

Runs when a GitHub issue is opened (ignores pull requests). For user-created
requests, the `library-new-request` label from the issue template makes the issue
eligible. For automated native-build-tools issues with no labels and the standard
`Support for groupId:artifactId:version` title, the workflow adds
`library-new-request` and `priority` first. Once eligible it extracts and
validates the Maven coordinates, closes invalid/duplicate/already-supported
requests, and also closes requests whose `groupId:artifactId` already has an
`index.json` recorded as `not-for-native-image` even when that index carries no
per-version `tested-versions`.

It then closes requests whose artifact cannot be resolved from any repository the
test builds configure (Maven Central and Confluent, per
`org.graalvm.internal.tck.gradle`). Resolution is a closed-world search: a miss
proves only that the artifact is absent from the repositories we query, never
that it does not exist, so the close comment asks the reporter for the repository
URL when the artifact is published somewhere public and points them at the
tracing agent when it is private. The check distinguishes an unknown
`groupId:artifactId` (no `maven-metadata.xml` anywhere) from a known artifact
whose requested version is missing, and reports each with its own message. Only a
definite `404` from every configured repository closes an issue; transport
failures and `5xx` responses leave the issue open for normal triage, so an
upstream outage cannot mass-close valid requests.

The repository list the gate probes is maintained by hand in the workflow and
must be kept in sync with the `repositories` block in
`org.graalvm.internal.tck.gradle`: `mavenCentral()` contributes no URL literal to
extract, so the list cannot be derived from the build. Adding a repository to the
build therefore requires adding it to the workflow in the same change. A workflow
list that has fallen behind the build closes requests the build could in fact
resolve. It then — via
`open-dependency-issues-and-link-blockers.js`
(§AR-shared-scripts) — generates a deps.dev dependency graph and opens or reuses
`library-new-request` issues for unsupported transitive dependencies, linking
them as blockers. Newly created transitive dependency issues do not receive
`priority`, even when the direct request has it. The label vocabulary it applies
is defined in
§FS-repository-functional-spec.4.

## Composite actions

### AR-detect-file-changes: detect-file-changes action

A `node20` action that returns `changed: true/false` for whether any file
matching the given glob `file-patterns` changed in the current PR (supports
`**`, `*`, `?`, and `!` negation). Every PR-validation workflow above gates its
real steps on this output so unrelated PRs cost almost nothing.
Serves §FS-repository-functional-spec.4.3 — every PR-scoped workflow there gates on it.

### AR-setup-native-build-tools: setup-native-build-tools action

A composite action that publishes and uses a native-build-tools (NBT) snapshot
when a branch with the same name as the caller's branch exists in the NBT
repository (or when `enabled-by-default` is set). It checks out that NBT ref,
reads `nativeBuildTools` from its `libs.versions.toml`, publishes it to
`mavenLocal`, and updates the caller repo's catalog to match — so a PR can be
tested against an in-progress NBT change just by matching branch names.
Serves §FS-repository-functional-spec.4.3 — the native-build-tools ref the CI lanes
build against.

### AR-setup-native-image-base-layer: setup-native-image-base-layer action

A composite action that restores or builds the shared JDK-module Native Image
base layer used by the layered TCK lane (§AR-test-harness.3). Its cache key is
derived from the actual installed `native-image --version`, runner
OS/architecture, selected native-image mode, `ci.json`, and TCK build
logic inputs. The action exports `GVM_TCK_BASE_LAYER_DIR` so subsequent Gradle
invocations in the manual layered workflow consume the exact cached layer
directory.
Serves §FS-repository-functional-spec.5.4 — the shared base layer belongs to the mode
matrix.

### AR-setup-crema-jdk: setup-crema-jdk action

A composite action that turns a downloaded GraalVM into a Crema JDK. GraalVM
early-access builds ship the `jvm-library` native-image macro and
`lib/graalvm/svm-libjvm.jar` but deliberately omit the built library, so the
action runs `native-image --macro:jvm-library`, which writes `lib/svm/libjvm.so`
— the Substrate VM with `-H:+RuntimeClassLoading` enabled. All Crema build
options come from the macro and from `svm-libjvm.jar`'s own
`native-image.properties`; the action adds none.
Serves §FS-repository-functional-spec.5.4 — the Crema JDK is a Native Image mode input.

It then rewrites `lib/jvm.cfg` to list `-svm` first, making the Substrate VM the
JDK's default so that a plain `java` invocation is Crema and no caller needs a VM
selection flag. The original file is kept as `jvm.cfg.hotspot-default` and
restored when the `default-vm` input is `hotspot`, which yields a byte-identical
tree running the stock VM — the baseline half of §AR-test-all-metadata-crema.
The action fails if the macro is absent from the downloaded JDK, because a
silently-HotSpot run would report a meaningless clean pass.

## AR-shared-scripts: Shared scripts and test isolation

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
  version and stopping the chain. Drives §AR-verify-new-library-version-compatibility.
- **Spring AOT triage** — `run-spring-aot-triaged-test.sh` runs the triaged
  native Spring AOT smoke tests for the projects computed by
  §AR-test-affected-spring-aot.
- **Dependency issue linking** — `open-dependency-issues-and-link-blockers.js`
  opens/reuses issues for unsupported transitive dependencies and links them as
  blockers, used by §AR-triage-new-issues.
