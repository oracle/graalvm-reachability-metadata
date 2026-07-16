# TCK-test-harness: Test harness (TCK) task groups

The repository ships a Gradle-based Technology Compatibility Kit (TCK) that,
given a library coordinate, validates its metadata and runs its tests on both
the JVM and `native-image`. The TCK is what makes every shipped metadata file
demonstrably backed by a test (§GOAL-tested-metadata): nothing is published until
it passes here and in CI (§CI-repository-ci).

This document specifies the harness as task groups, one citable section per
group. Code that implements a task cites its group — for example
`§TCK-test-harness.2` — and names the task in the surrounding comment, so a
citation records both where the behavior is specified and which task realizes it.
How the build that exposes these tasks is wired is §AR-build-infrastructure; the
tests that exercise the whole task surface end to end are
§E2E-infrastructure-tests; the per-task invocation reference (exact flags and
examples) lives in `DEVELOPING.md` and the cheat sheet in `AGENTS.md`. Tasks are
registered in the convention plugins under
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
| `discoverArtifactMetadata`, `listLibraryJars`, `populateArtifactURLs` | Inspect resolved artifacts and backfill index URL fields (§METADATA-suite). |

## 2. Validation gates

These enforce the metadata and style contracts before any test runs; CI runs the
same tasks as the authoritative gate (§CI-repository-ci,
§FS-repository-functional-spec.5.3).

| Task | Gate |
| --- | --- |
| `validateIndexFiles` | Index schema plus `metadata-version`/`tested-versions` consistency (§METADATA-suite). |
| `checkMetadataFiles` | Every entry uses `condition.typeReached`, stays inside `allowed-packages`, and references no test-only types (§METADATA-suite, §FS-repository-functional-spec.5.1). |
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

The manual layered workflow supplies `-Ptck.excludedCoordinatesFile` with its
maintained residual-failure list, so known LayerUse incompatibilities do not
hide new failures. Other workflows do not supply an exclusion file.

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
| `splitTestOnlyMetadata` | Move test-only metadata into the test resources file (§FS-repository-functional-spec.5.1, §METADATA-suite). |
| `fixTestNativeImageRun` | Regenerate metadata for a new version failing a native-image run. |
| `addTestedVersion` | Record a newly passing version in the artifact's `index.json` and refresh the mirrored stats and shared test sources; used by the compatibility workflow (§FS-repository-functional-spec.9). |
| `addLibraryMetadataIndexJson`, `addLibraryAsLatestMetadataIndexJson`, `extractLibraryTestParams` | Lower-level index and parameter helpers. |

## 6. Docker image management

| Task | Purpose |
| --- | --- |
| `pullAllowedDockerImages` | Pre-pull only the images a coordinate's metadata allows, before networking is disabled for isolated runs (§FS-repository-functional-spec.5.3). |
| `checkAllowedDockerImages` | Scan allowed images for vulnerabilities with grype, either all images or only those changed between two commits (§CI-scan-docker-images). |

## 7. CI matrix generation

These emit the GitHub Actions matrices the workflows consume, all driven by
`ci.json` (§CI-matrix-source).

| Task | Matrix |
| --- | --- |
| `generateMatrixMatchingCoordinates`, `generateMatrixBatchedCoordinates` | Full and batched matrices (the latter powers §CI-test-all-metadata). |
| `generateChangedCoordinatesMatrix`, `generateChangedMetadataTestMatrix`, `generateChangedCoordinatesOnlyMatrix`, `generateChangedIndexFileCoordinatesList` | PR-scoped matrices for changed metadata and index files. |
| `generateInfrastructureChangedCoordinatesMatrix` | Matrix for build-logic changes; also selects the coordinate for §E2E-infrastructure-tests. |
| `generateAffectedSpringTestMatrix` | Impacted Spring AOT projects. |
| `fetchExistingLibrariesWithNewerVersions`, `generateNewLibraryVersionCompatibilityMatrix` | Discover newer upstream versions and build the compatibility matrix (§GOAL-broad-version-coverage). |

## 8. Reporting, stats, coverage, and packaging

| Task | Output |
| --- | --- |
| `jacocoTestReport` | JaCoCo coverage for a coordinate. |
| `generateDynamicAccessCoverageReport`, `analyzeExternalLibraryDynamicAccess` | Dynamic-access coverage reporting (§FS-repository-functional-spec.4.5). |
| `generateLibraryStats`, `listTopCoordinatesByMetric`, `generateTopCoordinatesByMetricMatrix`, `generateReadmeBadgeSummary`, `generateDependencyGraph` | Produce and query the stats mirror, README badge inputs, and dependency graphs that feed the coverage dashboard (§CI-publish-scheduled-coverage). |
| `package` | Zip the `metadata/` directory into the release artifact consumed by native-build-tools (§FS-repository-functional-spec.4). |
