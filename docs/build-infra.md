# AR-build-infrastructure: Build infrastructure

The repository's build is a two-layer Gradle build, designed so that adding a
library never requires build-logic changes (§FS-repository-functional-spec.6) — coordinate discovery is
purely metadata-driven. This document specifies how that build is wired; the
task surface it exposes is grouped in §TCK-test-harness, and the tests that
prove the infrastructure works are in §E2E-infrastructure-tests.

## 1. Harness layer (repository root)

`settings.gradle` includes the build under `tests/tck-build-logic/`, which
publishes the `org.graalvm.internal.tck-harness` convention plugin applied by
the root `build.gradle`. This layer owns the `-Pcoordinates=` filter, metadata,
index and stats validation, reporting, CI matrix generation, and packaging
(§TCK-test-harness). It does not itself compile or run library tests; it
resolves the selected coordinates and delegates each one to the per-coordinate
layer.

## 2. Per-coordinate layer

For each selected coordinate the harness delegates to a separate Gradle build
rooted at the coordinate's test project under `tests/src/<group>/<artifact>/<version>/`
(§TESTS-suite). That project applies `org.graalvm.internal.tck`, built on
`org.graalvm.internal.tck-base` and `org.graalvm.internal.tck-settings`, which
wires the GraalVM native-build-tools plugin, the `javaTest`/`nativeTest` lanes,
JaCoCo, and guards such as `checkTestTimeoutAnnotations`. Per-coordinate builds
are isolated: one coordinate's outputs never affect another's (§FS-repository-functional-spec.6).

## 3. Support library

`tests/tck-test-support/` provides the `NativeImageSupport` helpers that test
code uses to assert native-image behavior. It is shared across all
per-coordinate builds.

## 4. Scaffolding

Test-project and metadata templates live under
`tck-build-logic/src/main/resources/scaffold/` — Java/Kotlin/Groovy/Scala test
stubs, `build.gradle`, `settings.gradle`, `reachability-metadata.json`, and
`user-code-filter.json`. The harness `scaffold` task instantiates them for a new
coordinate (§TCK-test-harness), so a contributor or Forge starts from a working
project rather than a blank directory.
