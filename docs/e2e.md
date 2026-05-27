# E2E-infrastructure-tests: Infrastructure end-to-end tests

`testInfra` and `testAllInfra` are the repository's end-to-end tests for the
*infrastructure itself*. Where the per-coordinate test lanes (§TCK-test-harness)
verify that a library's metadata is correct, these tasks verify that the harness
and build infrastructure (§AR-build-infrastructure) — every validation,
reporting, authoring, and test task — still run correctly end to end over real
library coordinates. They are how a change to the build logic is proven before
it ships, and they back the changed-infrastructure CI workflow
(§CI-repository-ci). Both are registered in the root `build.gradle` and declared
in the `verification` group.

## 1. `testInfra`

Given a single target (`-Pcoordinate`/`-Pcoordinates` accepting
`group:artifact:version`, `k/n`, or `all`), `testInfra` runs `clean` and
`pullAllowedDockerImages` for the resolved coordinate, then runs the full infra
task surface **concurrently** with isolated per-task logs:

`validateIndexFiles`, `checkstyle`, `spotlessCheck`, `checkMetadataFiles`,
`fetchExistingLibrariesWithNewerVersions`, `test`, `jacocoTestReport`,
`listLibraryJars`, `generateDynamicAccessReport`,
`generateDynamicAccessCoverageReport`, `generateLibraryStats`,
`listCoordinates`, `generateDependencyGraph`, and `splitTestOnlyMetadata`.

Concurrency is bounded by `-Pparallelism` (default 4). Because the task set spans
validation, the test lanes, reporting, stats, and authoring helpers
(§TCK-test-harness), a single `testInfra` run is a broad smoke test of the whole
harness against one coordinate. It prints a reproducer line per coordinate and
writes each task's output to its own log under `build/command-logs`.

## 2. `testAllInfra`

`testAllInfra` is the entry point used in development and CI. It selects a
representative artifact coordinate — from `generateInfrastructureChangedCoordinatesMatrix`
(the same matrix the changed-infrastructure workflow uses, §CI-repository-ci),
or from an explicit `-Pcoordinate`/`-Pcoordinates` override — and then runs
`testInfra` twice: once for that single coordinate and once for the `1/64`
shard. Running both a concrete artifact and a shard exercises the
coordinate-resolution paths (single coordinate and fractional batch) as well as
the task surface. `-Pparallelism` is forwarded to each `testInfra` invocation.

The recommended full local infrastructure check is
`./gradlew testAllInfra -Pparallelism=4 --stacktrace`.
