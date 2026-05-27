# TESTS-suite: The `tests/` suite

[`tests/`](../tests) is what justifies every byte of shipped metadata. A
metadata entry exists only because a test exercises the dynamic access it
registers and would fail without it (§GOAL-tested-metadata); this suite holds
those tests. The behavioral requirements for tests are normative in
§FS-repository-functional-spec.5.2; this document describes the test-project
contract.

## 1. Coordinate test projects

- [`tests/src/<groupId>/<artifactId>/<version>/`](../tests/src) — one
  self-contained Gradle test project per coordinate, unless an `index.json`
  entry sets `test-version` to share one suite across versions. Each project
  exercises the library's reachable surface through its public API, declares any
  Docker images it needs in `required-docker-images.txt`, and is the unit the
  harness runs on the JVM and on `native-image`.
- `src/test/resources/META-INF/native-image/reachability-metadata.json`
  — when present, the test-only reachability metadata that
  `splitTestOnlyMetadata` carved out of the shipped library metadata so the test
  image can reach its own helper types without those entries reaching consumers
  (§METADATA-suite, §FS-repository-functional-spec.5.1).
- `build.gradle` may add test dependencies and, when there is no better public
  API path, restrict Native Image configuration edits to `--add-opens` /
  `--add-exports` under `graalvmNative`.
- Test assertions use standard Java or library exceptions, not Gradle-only
  failure types such as `GradleException`.

## 2. What a good test must do

A good test drives the library the way a real consumer would, so that a missing
or wrong metadata entry makes it fail. The requirements are normative in
§FS-repository-functional-spec.5.2; the rules below are what they mean when
writing one.

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
  60 seconds, using bounded waits and closing every client, server, and executor
  it opens.
- **Keep test code original.** Do not copy upstream or other third-party test
  sources into this repository. Upstream tests may be used only as behavioral
  examples, and documentation only as API guidance. Review policy details live
  in [REVIEWING.md](REVIEWING.md).

## 3. How the suite is exercised

The harness compiles and runs each coordinate's project through the
`compileTestJava` → `javaTest` → `nativeTestCompile` → `nativeTest` lanes
(§TCK-test-harness.3), and CI runs the same lanes across the `ci.json` JDK/OS
matrix and native-image modes (§CI-repository-ci). A version is recorded as
supported only after it passes on every required environment, and dynamic-access
coverage between consecutive versions must not regress
(§FS-repository-functional-spec.5.2, §GOAL-protect-shipped-metadata). New test
projects are created from the scaffold templates by the authoring tasks
(§TCK-test-harness.5), by a human or by Forge (§forge/FS-forge-functional-spec).
