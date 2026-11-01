# FS-tests: The `tests/` suite

[`tests/`](../../tests) is what justifies every byte of shipped metadata. A
metadata entry exists only because a test exercises the dynamic access it
registers and would fail without it (§GOAL-tested-metadata); this suite holds
those tests. The behavioral requirements for tests are normative in
§FS-repository-functional-spec.5.2 and, point by point, in §FS-test-contract;
this document describes the test-project contract.

## 1. Coordinate test projects

- [`tests/src/<groupId>/<artifactId>/<version>/`](../../tests/src) — one
  self-contained Gradle test project per coordinate, unless an `index.json`
  entry sets `test-version` to share one suite across versions. Each project
  exercises the library's reachable surface through its public API, declares any
  Docker images it needs in `required-docker-images.txt`, and is the unit the
  harness runs on the JVM and on `native-image`.
- `src/test/resources/META-INF/native-image/reachability-metadata.json`
  — when present, the test-only reachability metadata that
  `splitTestOnlyMetadata` carved out of the shipped library metadata so the test
  image can reach its own helper types without those entries reaching consumers
  (§FS-metadata, §FS-repository-functional-spec.5.1).
- `build.gradle` may add test dependencies and, under `graalvmNative`, only
  the flags §FS-test-contract.2.7 allows: one from its allowed list when the
  failure shows the library needs it, or one meeting the uniform necessity
  rule, never one from its forbidden list. A run argument the project sets
  reaches the native run: the harness adds its own run arguments, such as the
  timeout of §FS-test-contract.1.6, beside it and never in its place.
- Test assertions use standard Java or library exceptions, not Gradle-only
  failure types such as `GradleException`.

## 2. What a good test must do

A good test drives the library the way a real consumer would, so that a missing
or wrong metadata entry makes it fail. The requirements are normative in
§FS-repository-functional-spec.5.2 and §FS-test-contract; the rules below are a
summary of what they mean when writing one.

- **Drive real behavior through the public API.** Exercise enough of the
  library's reachable surface that the test fails when metadata is wrong or
  missing. Do not reach for reflection directly unless the public API genuinely
  requires it, and keep tests outside the library's own packages — a test must
  not live in a library package just to touch package-private or internal code.
- **Test supported behavior, not bugs.** Target behavior the library actually
  supports. Never make an uncovered dynamic-access call site "covered" by
  asserting a known bug, regression, or version-specific failure; exception
  assertions are acceptable only for documented, supported negative-path APIs.
- **Stay version-agnostic.** Do not pin to a specific library version or hardcode
  the artifact version in inputs or assertions. Scaffold-only tests that do not
  actually exercise the library are not acceptable.
- **Don't fake the library.** No source stubs, fake replacements, or shadow
  classes for library or dependency types in their real packages. If a needed API
  is missing from the test classpath, add the correct test dependency or leave
  the call site unreached with an explanation — never paper over it.
- **Run under Native Image by default.** Tests execute on `native-image`, not
  only the JVM; do not disable native execution with `@DisabledInNativeImage`,
  `assumeFalse(...)`, `isNativeImageRuntime()`, or equivalent guards. When a test
  genuinely needs open-ended dynamic class loading that Native Image cannot
  support, prove it fails for the right reason — catch `Error` and verify it with
  `NativeImageSupport.isUnsupportedFeatureError(e)` — rather than skipping it.
- **Be deterministic, isolated, and bounded.** No network except against a
  declared, pre-pulled `allowed-docker-image` with networking disabled, and
  per-coordinate runs must not depend on or affect other coordinates
  (§FS-repository-functional-spec.6). Each individual test completes in under
  3 minutes, using bounded waits and closing every client, server, and executor
  it opens.
- **Keep test code original.** Do not copy upstream or other third-party test
  sources into this repository. Upstream tests may be used only as behavioral
  examples, and documentation only as API guidance. Review policy details live
  in [REVIEWING.md](../REVIEWING.md); what review may block on — rule strength,
  shape limits, coverage gates, and cheating patterns — is normative in
  §FS-contribution-contract.

## 3. How the suite is exercised

The harness compiles and runs each coordinate's project through the
`compileTestJava` → `javaTest` → `nativeTestCompile` → `nativeTest` lanes
(§AR-test-harness.3), and CI runs the same lanes across the `ci.json` JDK/OS
matrix and native-image modes (§AR-repository-ci). A version is recorded as
supported only after it passes on every required environment, and dynamic-access
coverage between consecutive versions must not regress
(§FS-repository-functional-spec.5.2, §GOAL-protect-shipped-metadata). New test
projects are created from the scaffold templates by the authoring tasks
(§AR-test-harness.5), by a human or by Forge (§forge/FS-forge-functional-spec).

### 3.1 Temporary layered-test initialization workaround

Layered tests defer `io.netty.util.NetUtil` and its gRPC-shaded counterpart
`io.grpc.netty.shaded.io.netty.util.NetUtil` to runtime initialization so they
discover the executable's network interfaces instead of retaining the image
builder's interfaces (§graal/GR-80256). Both the dedicated library base and the final
test image receive this policy. The standalone test lane keeps its existing
initialization policy; this temporary harness workaround ships no metadata.

### 3.2 Library build options across dedicated layers

A dedicated base and its final test executable use the same coordinate-specific
Native Image build policy, including options declared only on the test binary
in the coordinate's build script. A setting already applied to both binaries is
not added a second time. Option order and intentional repetitions are preserved
so initialization rules and option overrides keep their meaning (§FS-tests.3).

Each binary retains its own layer selection, classpath, module path, metadata
directories, and JUnit selectors. Application-only module roots and
test-executable instrumentation are not copied into the base; explicit JDK
module requests remain part of the shared build policy. Build
options are distinct from executable run arguments and JVM-test arguments;
sharing a build policy does not run test classes in the base. Shared JDK bases
follow the conditional selection policy in §FS-tests.3.3.

### 3.3 Custom shared bases for coordinate build options

The shared-layer lane reuses its cached common JDK base when a coordinate has
no additional Native Image build policy. A coordinate with additional build
options instead builds its own JDK-only base with the applicable policy from its
final test image (§FS-tests.3.2). Third-party security-provider registrations
remain on the application: a JDK-only base cannot resolve their classes. JDK
provider registrations remain shared, and dedicated bases retain the complete
provider list. Selection is derived from the configured binary,
including `binaries.all` settings, without a manually maintained library list.
Library JARs and test classes remain outside this base: this is still the
shared-layer scenario, not the dedicated library-layer scenario.

A custom shared base is rebuilt for each coordinate invocation and is neither
restored from the common CI cache nor reused through Gradle up-to-date or build
cache checks. Its manifest records the exact build policy and toolchain, and
the application validates that manifest before compilation. The original
common base and its cache remain available to other coordinates.

### 3.4 Dedicated layers with overlapping JDK API classes

A dedicated base keeps its original dependency JARs and Native Image's
builder-class validation. When a dependency JAR contains a class also present
in an optional JDK module, the base omits that module's explicit layer selector
so ordinary JDK class resolution can resolve the API (§FS-tests.3). Selection
checks exact class names across the complete base dependency classpath against
the builder JDK, including versioned JAR entries, and handles every overlapping
module rather than only the first diagnostic.

`java.base` is always selected. An overlap with that mandatory module still
receives Native Image's normal validation; the harness neither removes the JAR
nor adds a validation override. Modules without overlaps keep their existing
selection order. Shared JDK bases and standalone tests retain their existing
module selection. Omitted dedicated-base module selectors are reported so the
coordinate's effective layer contents remain visible.

### 3.5 Native libraries accompanying layered test executables

After a layered test executable compiles successfully, its output directory
also contains the selected base layer's dynamic libraries ending in `.so`,
`.dylib`, or `.dll` (§FS-tests.3). Copying preserves files already produced by
the application build and leaves the base unchanged. It applies to shared,
custom shared, and dedicated bases; standalone tests keep their existing
packaging.

The selected base's runtime files are build inputs, and the copied libraries
are part of the application task's tracked output directory. Rebuilds and
cached outputs therefore retain the libraries needed by ordinary native tests,
tracing runs, and profiling runs, without a separate runtime search-path
override.

### 3.6 Preparing the common base once for CI shards

Within a layered CI run, one preparation job restores or builds the common
shared JDK base before any shared shard starts (§FS-tests.3.3). It publishes
the complete base directory, including its manifest and native libraries, for
all shared shards to consume. Persistent caching remains available across
runs; shards never rebuild or publish the common base themselves. Dedicated
shards do not consume the common base and can run even if its preparation
fails; dedicated-only runs skip that work. Coordinate-specific shared bases
retain their uncached construction policy.

All jobs test the same resolved repository revision. Before using the prepared
base, a shared shard verifies that revision, the installed GraalVM build,
OS/architecture, and Native Image mode. Missing or incompatible prepared bases
fail the shard without falling back to common-base construction. Archive
transfer preserves executable permissions and symbolic links.

### 3.7 Dependency precedence across dedicated layers

A dedicated base uses the dependency JARs present on the configured native-test
classpath in that classpath's order (§FS-tests.3.2). It preserves which JAR wins
when dependencies contain the same class; it must not sort paths or resolve a
different definition of a class already selected by the application. This
keeps persisted field positions and other class metadata consistent across
layers, including when a coordinate customizes its native-test classpath.

Filtering retains resolved dependency artifacts while excluding the
coordinate's test JAR, compiled test classes, and test resources. Test-only
code therefore remains in the application layer, and no dependency JAR is
rewritten or discarded merely because it overlaps another dependency.
