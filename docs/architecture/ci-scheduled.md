# Scheduled CI workflows

These run on `cron` (and usually `workflow_dispatch`) and keep coverage current
and releases flowing without a human in the loop.

## AR-layered-tests: Shared and dedicated Native Image layer tests

Every Sunday at 00:30 UTC (`30 0 * * 0`) and on manual dispatch. The scheduled
run checks every supported library on the default branch with GraalVM `latest-ea`
and the `current-defaults` Native Image mode, using the cached shared base layer
(§AR-test-harness.3). An `all` selection uses 32 independent shared-layer
shards and 128 independent dedicated-layer shards to reduce the work each job
must finish within the six-hour hosted-runner limit (§FS-repository-functional-spec.6).
A shared pending queue preserves every selected shard until it has a recorded
outcome. A dispatcher admits at most 32 shard runs at a time, counting queued
and executing runs together. Whenever any run finishes, its place is available
to any unstarted shard; work is not tied to a batch or a chain. Each admitted
shard remains a separate job with its own six-hour execution limit. Both lanes
keep their original shard denominators, preserving assignment and coverage.

Queue state and shard run identities survive dispatcher restarts. A single
serialized dispatcher reconciles recorded submissions with GitHub before
admitting more work; ambiguous API failures do not cause blind redispatch.
Test failures are recorded and do not block later shards. Work that never
started remains pending after a runner-queue expiry; actual test failures are
not automatically retried. Cancellation stops further admission and cancels
outstanding shard runs. An aggregate result remains pending until every shard
has an outcome, and fails for failed or untested shards. Completion of the
preparation job alone never represents a passing sweep.

This bounds the sweep's submitted backlog without reserving organization
runner capacity. Unstarted work can recover from queue expiry; a platform
outage cannot silently remove it from coverage.
Manual dispatch defaults to both lanes, `master`,
`all`, and `latest-ea`; it may instead select one lane, a different ref,
coordinate, JDK, or mode. A concrete coordinate or explicit shard creates one
job per selected lane. The shared lane uses a JDK base; the dedicated lane
creates a base containing the tested library for each coordinate
(§AR-test-harness.3).
Dedicated layers are deleted after each coordinate to bound runner disk use;
failures are collected independently so the two layer layouts remain directly
comparable.

The matrix preparation resolves the requested ref to one immutable commit.
One preparation job resolves GraalVM once and transfers that installation as a
tar artifact to every shard run, so a later `latest-ea` publication cannot change
the toolchain mid-run. When shared tests are selected, the same job restores or
builds the common base with §AR-setup-native-image-base-layer and uploads its
complete directory (§FS-tests.3.6). All shards wait for preparation; dedicated
shards can still run if common-base preparation fails after publishing GraalVM.
Shared shards verify repository, JDK release/version, mode, and runner identity,
and require the base manifest to match their build policy. The sweep records
the producer run and artifact identities durably, so dispatched shards and
resumed dispatchers reuse the same successful uploads. A
dedicated-only run skips common-base preparation; custom shared bases still
rebuild per coordinate.
Serves §FS-repository-functional-spec.5.4 — the Native Image mode this lane pins.

## AR-test-all-metadata: Test all metadata

Every Sunday (`0 2 * * 0`) and on manual dispatch. Uses
`generateMatrixBatchedCoordinates` with 85 batches to build a JDK/OS matrix, runs
the full `test` lane, pulls only allowed images, then disables Docker networking.
Failed batches are isolated down to concrete library versions, publish result
and failure-log artifacts, and fail in the matrix so the Actions UI points at
the failing batch. The aggregate job publishes a failure report when failures are
found (§FS-repository-functional-spec.5.3); it surfaces sweep regressions but does
not gate the scheduled release (§AR-create-scheduled-release).

## AR-verify-new-library-version-compatibility: Verify new library version compatibility

Every day (`0 0 * * *`) and on manual dispatch. Owns the upstream-version
tracking loop fully specified in §FS-library-version-update-automation
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

## AR-publish-scheduled-coverage: Publish scheduled coverage

Every two hours (`15 */2 * * *`) and on manual dispatch. Regenerates the coverage
badges and dashboard from committed `stats/` and `metadata/**/index.json`
(§FS-repository-functional-spec.4.5), then force-pushes the published artifacts
to the `stats/coverage` branch. The published branch keeps only `COVERAGE.md`,
`latest/badges.json`,
`latest/metrics-over-time.svg`, `latest/metrics-over-time-dark.svg`, and
`history/history.json`.

## AR-create-scheduled-release: Create scheduled release

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

## AR-create-snapshot-release: Create snapshot release

On pushes to `master` and on manual dispatch. Publishes a floating `SNAPSHOT`
GitHub Release on the `SNAPSHOT` tag when metadata changed since the previous
`SNAPSHOT` tag; if that tag does not exist yet, it bootstraps the diff from the
latest numbered release tag. The workflow packages metadata with repository
version `SNAPSHOT`, deletes the previous snapshot release/tag when present,
force-pushes a fresh `SNAPSHOT` tag, and marks the release as not GitHub's
Latest release (§FS-repository-functional-spec.4.4, §GOAL-fresh-metadata).

## AR-test-all-metadata-crema: Test all metadata on the Crema JVM

Every Saturday (`0 2 * * 6`) and on manual dispatch. Runs the repository's JVM
test lane against Crema — Native Image's run-time class loading VM — to find
where Crema cannot yet run real library test suites. It is a bug-finding sweep
aimed at Crema, not a metadata gate: `metadata/` correctness is not what it
measures, and its result never blocks a release. Saturday keeps it clear of the
Sunday metadata sweep (§AR-test-all-metadata) and the Monday release
(§AR-create-scheduled-release) so the three never compete for runners.
Serves §FS-repository-functional-spec.5.4 — the Crema lane is a Native Image mode.

The scheduled run takes every input's default — all coordinates over 85 shards on
the `crema` lane with the normal test configuration — because the `inputs`
context is empty on a `schedule` event; the workflow restates each default rather
than resolving an empty matrix. The weekly trigger is confined to the canonical
repository so a fork does not sweep on its own schedule, while manual dispatch
stays available everywhere.

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
execute on Crema while Gradle, which cannot run on Crema (§graal/GR-80250), keeps
running on the runner's stock JDK. No Crema-specific JVM flag is ever added: a
library that fails only because Crema rejects an argument the JVM lane normally
passes is a finding, not something the workflow works around. JaCoCo is disabled
via `-PskipJacoco=true` because Crema ignores `-javaagent` (§graal/GR-77787),
which would otherwise publish empty coverage as though it were real.

The `vm` input selects the lane: `crema`, `hotspot`, or `both`. `hotspot` runs
the identical tree on the identical JDK with the stock VM, and only
`crema`-fails-while-`hotspot`-passes is reportable — this repository has
coordinates that fail for environmental reasons under any VM. Failures are
attributed per coordinate and published as NDJSON plus log artifacts in the same
shape as §AR-test-all-metadata, so the existing failure tooling applies. The
matrix comes from `generateMatrixBatchedCoordinates` (§AR-test-harness.7) via
the `batches` input; `coordinates` narrows a run to one shard or one library.
