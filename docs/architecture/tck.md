# AR-test-harness: Test harness (TCK) task groups

The repository ships a Gradle-based Technology Compatibility Kit (TCK) that,
given a library coordinate, validates its metadata and runs its tests on both
the JVM and `native-image`. The TCK is what makes every shipped metadata file
demonstrably backed by a test (§GOAL-tested-metadata): nothing is published until
it passes here and in CI (§AR-repository-ci).

This document specifies the harness as task groups, one citable section per
group. Code that implements a task cites its group — for example
`§AR-test-harness.2` — and names the task in the surrounding comment, so a
citation records both where the behavior is specified and which task realizes it.
How the build that exposes these tasks is wired is §AR-build-infrastructure; the
`testInfra` and `testAllInfra` tasks exercise the whole task surface end to end;
the per-task invocation reference (exact flags and examples) lives in
`DEVELOPING.md` and the cheat sheet in `AGENTS.md`. Tasks are registered in the
convention plugins under
`tests/tck-build-logic/src/main/groovy/`.

## 1. Coordinate selection and discovery

Every coordinate-scoped task accepts the single filter `-Pcoordinates=`, which
takes `all`, `group:artifact`, `group:artifact:version`, or a shard `k/n` so CI
can parallelize the work (§FS-repository-functional-spec.6). The tasks here
resolve that filter into concrete coordinates and inspect what they select.
Fixture coordinates such as `org.example:*` and `samples:*` are selectable by
the coordinate resolver and runnable through style and test lanes, but they must
not be counted as public supported-library output. The `testInfra` bundle uses
the public-library coordinate set and excludes `samples:*`, because the bundle
includes reporting/public-output tasks that do not operate on fixture-only
coordinates.
`-Ptck.excludedCoordinatesFile=<file>` optionally removes coordinates whose
`group:artifact` library key occurs in the supplied UTF-8 file. Versions are
ignored; blank lines and lines beginning with `#` are ignored. This common
filter applies to coordinate-scoped tasks using the shared resolver, so callers
can reuse a single known-failure list across test or validation lanes when
appropriate.

| Task | Purpose |
| --- | --- |
| `listCoordinates` | Enumerate the coordinates the filter currently selects. |
| `diff` / `testDiff` | Compute and test the coordinates affected by a Git diff. |
| `discoverArtifactMetadata`, `listLibraryJars`, `populateArtifactURLs` | Inspect resolved artifacts and backfill index URL fields (§FS-metadata). |

## 2. Validation gates

These enforce the metadata and style contracts before any test runs; CI runs the
same tasks as the authoritative gate (§AR-repository-ci,
§FS-repository-functional-spec.5.3).

| Task | Gate |
| --- | --- |
| `validateIndexFiles` | Index schema plus `metadata-version`/`tested-versions` consistency (§FS-metadata). |
| `checkMetadataFiles` | Every entry uses `condition.typeReached`, stays inside `allowed-packages`, and references no test-only types (§FS-metadata, §FS-repository-functional-spec.5.1). |
| `checkstyle` | Checkstyle across the selected coordinates' sources. |
| `checkTestTimeoutAnnotations` | Rejects oversized JUnit `@Timeout` values. |
| `validateLibraryStats` | Schema and normalized sorting of the `stats/` mirror, including the Forge run records (§forge/FS-forge-run-metrics). |
| `checkDocLinks` | Validates documentation links. |

## 3. Test execution lanes

The core per-coordinate lifecycle, delegated to the per-coordinate build
(§AR-build-infrastructure). The lanes run in order — compile, JVM tests, native
build, native tests.

The JVM and full test lanes accept `-PskipJacoco=true` to run without JaCoCo
instrumentation when the caller needs only pass/fail execution and not coverage
data.

| Task | Lane |
| --- | --- |
| `compileTestJava` | Compile the coordinate's test sources. |
| `javaTest` | Run the tests on the JVM. |
| `nativeTestCompile` | Build the native image used by native tests (compile-only). |
| `buildBaseLayer` | Build or validate the shared JDK-module Native Image layer used by layered tests. |
| `testSharedLayer` | Run the native tests with `LayerUse` pointing at the shared base layer. |
| `testDedicatedLayer` | Build one base layer per coordinate containing the tested library, then run its native tests with that layer. |
| `test` / `tckTest` | The full lane: validation, JVM tests, then native-image tests. |
| `clean` / `tckClean` | Clear a coordinate's build outputs. |

Layered test tasks use `-Ptck.baseLayerDir=<dir>` or `GVM_TCK_BASE_LAYER_DIR`
when supplied; otherwise they default to `build/native-base-layer`. The base
layer directory contains `base-layer.nil` plus a manifest keyed by the exact
`native-image --version`, OS/architecture, native-image mode,
base-layer module set (`java.base`, `java.management`, `java.naming`, `java.sql`,
`jdk.unsupported`, `java.desktop`, `java.scripting`, `jdk.httpserver`,
`java.net.http`, `java.sql.rowset`, `jdk.jfr`, `java.smartcardio`,
`java.transaction.xa`, `java.security.sasl`, `java.xml`, `jdk.dynalink`,
`jdk.jsobject`, `jdk.localedata`, and `jdk.xml.dom`), and build arguments,
so a stale or mismatched layer is rejected before any per-coordinate native
image is built.

The dedicated-layer lane keeps the shared-layer lane intact but moves layer
creation into each coordinate build. Each layer includes the same JDK modules
as the shared layer plus every class and resource in the resolved tested-library
JARs and the resolved JUnit runtime JARs, including JUnit's support artifacts
and the Native Build Tools `junit-platform-native` artifact that supplies
`JUnitPlatformFeature`. The base analysis classpath contains resolved dependency
JARs but excludes the coordinate's compiled test classes, test resources, and
test JAR. Both layer builds activate `JUnitPlatformFeature`. The base invocation
uses infrastructure-generated selectors for the JUnit engine roots, establishing
JUnit's initialization and reachability policy without trying to resolve absent
test classes. The application invocation uses the coordinate's real unique-ID
files to discover and register those classes. The written test code therefore
remains exclusively in the final application layer.
The base build uses the same resolved Native Image configuration directories as
the final test image. For both builds, it stages test-scoped
`reachability-metadata.json` without conditions because LayerCreate does not
preserve runtime `typeReached` tracking for selected JAR types. The normal test
lane still validates the original conditions; the dedicated lane applies the
same metadata unconditionally in both analyses to keep class-initialization
policy stable without including test classes or unrelated test resources in the
base layer.
Gradle rebuilds the layer when its coordinate, Native Image settings,
configuration, or base-analysis classpath changes. The final test image retains
the complete standalone test runtime classpath while using the
coordinate-specific layer.
CI supplies `-Ptck.layered.deleteDedicatedLayerAfterTest=true` to delete each
large coordinate layer after its test; local runs retain layers for reuse unless
they explicitly request the same cleanup behavior.

`testSharedLayer` can run a coordinate batch in collecting mode with
`-Ptck.layered.continueOnCoordinateFailure=true`; when combined with
`-Ptck.layered.coordinateFailureReport=<file>`, it writes one failed coordinate
per line before failing the task at the end of the batch.

The manual layered workflow supplies `-Ptck.excludedCoordinatesFile` with the
shared-layer residual-failure list to the shared lane and the dedicated-layer
residual-failure list to the dedicated lane. This keeps known failures from
hiding new failures while allowing each lane's failures to be triaged
independently. Other workflows do not supply an exclusion file.

### 3.1 Running the JVM lane on a different JDK

`GVM_TCK_TEST_JAVA_HOME` selects the JDK that executes the forked JVM test
workers, independently of the JDK running Gradle itself. When it is unset — the
default — workers inherit the Gradle daemon's JVM and nothing changes. When it
is set, the `test` task runs `$GVM_TCK_TEST_JAVA_HOME/bin/java` with the JVM
arguments it would use anyway: the variable selects an interpreter, it never
adds, removes, or rewrites a JVM flag. Coordinates that pin a toolchain (the
Kotlin and Scala projects) have their toolchain-derived launcher cleared so the
selected JDK wins uniformly across every coordinate.

This exists so the whole test suite can be run against a JDK that is not able to
host Gradle — the Crema JVM of §AR-test-all-metadata-crema is the motivating
case. Because the selected JDK is used unmodified, a failure under it is a
property of that JDK and not of the harness, which is what makes the resulting
run usable as a bug report.

`GVM_TCK_TEST_DISABLE_ASSERTIONS=true` is the one exception, and it is opt-in and
off by default. Gradle enables assertions on every test worker, so a JDK that
rejects `-ea` outright fails all workers at VM startup and the run yields a
single finding repeated once per coordinate instead of a survey. Setting this
clears Gradle's `enableAssertions` so the sweep can reach the failures behind
that one. It changes what the tests check: assertions no longer fire, so a test
that verifies via `assert` passes vacuously. A run with this set is a
bug-discovery run, never evidence that a library is supported.

## 4. Native-image metadata tracing

Helpers for collecting metadata with the native-image tracing agent (see also
`CollectingMetadata.md`).

| Task | Purpose |
| --- | --- |
| `nativeTraceImage` | Build a native image instrumented with the tracing agent. |
| `runNativeTraceImage` | Run it to collect access traces. |
| `mergeNativeTraceMetadata` | Merge collected traces into metadata. |
| `generateDynamicAccessReport` | Report the dynamic access a coordinate exercises. |

## 5. Metadata authoring helpers

Tasks that create or update metadata, tests, and index entries.

| Task | Purpose |
| --- | --- |
| `scaffold` | Create the test project and metadata skeleton for a new coordinate from the scaffold templates (§AR-build-infrastructure). |
| `contribute` | Guided contribution flow for a new coordinate. |
| `generateMetadata` | Generate metadata for a coordinate (optionally deriving `user-code-filter.json` from the resolved JAR). |
| `splitTestOnlyMetadata` | Move test-only metadata into test resources (§FS-repository-functional-spec.5.1, §FS-metadata). |
| `routeForeignMetadata` | After metadata validation fails, relocate uniquely owned foreign-condition entries to supported artifact/version buckets, validate the affected owners, and regenerate their statistics (§FS-metadata). |
| `fixTestNativeImageRun` | Regenerate metadata for a new version failing a native-image run. |
| `addTestedVersion` | Record a newly passing version in the artifact's `index.json` and refresh the mirrored stats and shared test sources; used by the compatibility workflow (§FS-repository-functional-spec.9). |
| `addLibraryMetadataIndexJson`, `addLibraryAsLatestMetadataIndexJson`, `extractLibraryTestParams` | Lower-level index and parameter helpers. |

## 6. Docker image management

| Task | Purpose |
| --- | --- |
| `pullAllowedDockerImages` | Pre-pull only the images a coordinate's metadata allows, before networking is disabled for isolated runs (§FS-repository-functional-spec.5.3). |
| `checkAllowedDockerImages` | Scan allowed images for vulnerabilities with grype, either all images or only those changed between two commits (§AR-scan-docker-images). |

## 7. CI matrix generation

These emit the GitHub Actions matrices the workflows consume, all driven by
`ci.json` (§AR-matrix-source).

| Task | Matrix |
| --- | --- |
| `generateMatrixMatchingCoordinates`, `generateMatrixBatchedCoordinates` | Full and batched matrices (the latter powers §AR-test-all-metadata). |
| `generateChangedCoordinatesMatrix`, `generateChangedMetadataTestMatrix`, `generateChangedCoordinatesOnlyMatrix`, `generateChangedIndexFileCoordinatesList` | PR-scoped matrices for changed metadata and index files. |
| `generateInfrastructureChangedCoordinatesMatrix` | Matrix for build-logic changes; also selects the coordinate for `testAllInfra`. |
| `generateAffectedSpringTestMatrix` | Impacted Spring AOT projects. |
| `fetchExistingLibrariesWithNewerVersions`, `generateNewLibraryVersionCompatibilityMatrix` | Discover newer upstream versions for `auto-update` libraries and build the compatibility matrix (§FS-library-version-update-automation.1). |

## 8. Reporting, stats, coverage, and packaging

| Task | Output |
| --- | --- |
| `jacocoTestReport` | JaCoCo coverage for a coordinate's regular test suite. |
| `codeCoverageTest` | Run the tracked `code-coverage-improvement/` extension suite of a coordinate on the JVM. |
| `jacocoCodeCoverageReport` | Combined JaCoCo coverage over the regular and extension suites (§forge/AR-code-coverage-improvement.3.1). |
| `generateDynamicAccessCoverageReport`, `analyzeExternalLibraryDynamicAccess` | Dynamic-access coverage reporting (§FS-repository-functional-spec.4.5). |
| `nativeTestPGOSampling` | Build coordinate native tests with sampled PGO and the analysis call-tree CSV dump for Forge deep-coverage navigation (§forge/AR-code-coverage-improvement.3.2). |
| `runNativeTestPGO` | Run the sampling image and write its sampled `.iprof` to the required absolute `pgoProfilePath`. |
| `generateLibraryStats`, `listTopCoordinatesByMetric`, `generateTopCoordinatesByMetricMatrix`, `generateReadmeBadgeSummary`, `generateDependencyGraph` | Produce and query the stats mirror, README badge inputs, and dependency graphs that feed the coverage dashboard (§AR-publish-scheduled-coverage). Library coverage analyzes only the coordinate's unclassified main JAR and includes both the regular and tracked extension suites. |
| `package` | Zip the `metadata/` directory into the release artifact consumed by native-build-tools (§FS-repository-functional-spec.4). |

`generateDynamicAccessCoverageReport` derives its call sites from the native
test build's `dynamic-access` output. When that input cannot be produced — the
underlying `generateDynamicAccessReport` build fails, or its output directory is
absent afterwards — the task fails instead of writing a report over the missing
input. An empty report therefore always means the library reported no
dynamic access, never that nothing was built, so a consumer may treat a zero-call
report as a property of the library (§forge/AR-dynamic-access-fallback-and-failure).

Dynamic-access coverage normally marks a call site covered when its stack frame
carries a source line and JaCoCo covered that line. Some jars are compiled
without a `LineNumberTable`, so every dynamic-access frame is line-less
(`Unknown Source`) and line-based matching can never succeed. In that case only
— the report has call sites but none carries a line number — the harness
collects `native-image-agent` configuration origins from a JVM `test` run and
falls back to matching the complete origin path against the statically reported
call sites. The harness streams the agent's compressed origin tree and marks a
line-less call site covered when the same path contains both its exact tracked
API and its caller class and method, with the caller preceding the API. It keeps
only the current origin-tree branch and the matched call-site identities in
memory. If multiple reported callers precede the API, the nearest one wins; a
nested tracked API ends the caller search so delegated JDK calls do not cover a
different API with the same method name. Line-based matching stays the primary
path and is unchanged for jars that carry line information.

The code-coverage extension suite lives at the tracked
`code-coverage-improvement/` directory inside a coordinate's test project
(`src/test/java`, optional `src/test/resources`, optional supplemental
`metadata/`). It maps to a dedicated `codeCoverage` source set, so ordinary
metadata-generation and validation commands never compile or run it. Native
lanes opt in with `-PincludeCodeCoverageSuite=true`, which widens the test
source set for that invocation because the plugin-managed native test binary
is derived from it; compile, JVM test, JaCoCo, Checkstyle, native compile/run,
and sampled-PGO root tasks forward that property to the coordinate project.
This keeps broad coverage tests separate from metadata-generation tests while
reusing the coordinate's dependencies and build configuration
(§forge/AR-code-coverage-improvement.3.1).

JaCoCo coverage reports analyze only the coordinate's unclassified main JAR;
classified artifacts such as upstream test JARs stay on the execution classpath
but never contribute classes to the coverage denominator. `generateLibraryStats`
uses the combined `jacocoCodeCoverageReport`, so newly added regular tests and
tracked code-coverage extension tests are reflected in the committed
`libraryCoverage` line, instruction, and method statistics.

`nativeTestPGOSampling` builds with `--pgo-sampling`, a positive
`-H:PGOSamplingPeriodMicros=<micros>`, `-H:+PrintAnalysisCallTree`, and
`-H:PrintAnalysisCallTreeType=CSV`. The optional Gradle property
`pgoSamplingPeriodMicros` defaults to `100` (the Native Image minimum) and must be forwarded through the
root coordinate fan-out for both build-only and build-and-run invocations.
`runNativeTestPGO` depends on that sampling build and dumps the profile through
`-XX:ProfilesDumpFile=<absolute path>`. A nonzero sampling-image exit fails the
task, so a profile from a failing native suite cannot be accepted. Sampling is
guidance only; JaCoCo remains the coverage metric
(§forge/AR-code-coverage-improvement.3.2).
