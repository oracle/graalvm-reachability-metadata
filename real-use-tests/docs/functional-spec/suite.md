# FS-real-use-tests: The `real-use-tests/` suite

`real-use-tests/src/` holds tests that use a supported library the way an
application does. The repository's `tests/` suite exists to justify shipped
metadata, so its tests chase the paths that trigger dynamic access
(§root/FS-tests). A real-use test instead runs one realistic scenario end to
end, with whatever libraries that scenario needs on the classpath, and shows
that a native-built consumer of the library works with the metadata the
repository ships (§GOAL-consumer-evidence).

Real-use tests consume shipped metadata and never change it. They justify no
entry in `metadata/` — that stays the job of §root/FS-tests under
§root/GOAL-tested-metadata. Whatever a scenario needs beyond the shipped
metadata is kept test-local and reported, so a maintainer can decide what
deserves to ship (§GOAL-reviewable-metadata-gaps).

## 1. Coordinate projects

- `real-use-tests/src/<groupId>/<artifactId>/<version>/` — one self-contained
  Gradle test project per coordinate, the same shape as a `tests/` coordinate
  project (§root/FS-tests.1). The coordinate must be a supported library version:
  one listed in the `tested-versions` of its
  `metadata/<groupId>/<artifactId>/index.json`.
- `src/test/java/` — the scenarios, one JUnit test class each
  (§FS-real-use-test-contract.1.1).
- `build.gradle` — the target library plus every supporting library the
  scenarios use (§FS-real-use-test-contract.1.4).
- `user-code-filter.json` — the same filter a `tests/` project carries, scoped
  to the target library and the supporting libraries `build.gradle` declares.
  It bounds which libraries the project's test-local metadata may describe
  (§FS-real-use-tests.2.2). The other filter files a `tests/` project may carry
  are allowed with the same meaning.
- `required-docker-images.txt` — every Docker image a scenario needs, each one
  an allowed image, exactly as for `tests/` (§root/FS-repository-functional-spec.5.2).
- `src/test/resources/META-INF/native-image/reachability-metadata.json` — the
  project's test-local metadata (§FS-real-use-tests.2.2), when it needs any.

## 2. Metadata

### 2.1 Shipped metadata is consumed, never changed

The native build of a real-use project resolves shipped metadata for every
library on its classpath from the repository's `metadata/`, the way a
native-build-tools consumer does (§root/FS-repository-functional-spec.4.7). Work on
this suite never adds, edits, or removes anything under `metadata/`.

### 2.2 Test-local metadata

Every entry a scenario needs beyond the shipped metadata goes into the
project's test-local `reachability-metadata.json`, whether it describes the
target library, a supporting library, or the test code. Each entry:

- validates against the vendored `reachability-metadata` schema;
- carries a `typeReached` condition and no other condition kind;
- names in that condition a type from a package that `user-code-filter.json`
  includes, or from the test's own package.

These are the shipped-metadata rules of §root/FS-repository-functional-spec.5.1
applied to a test-local file, so an entry is already in shipped form if a
maintainer later promotes it.

### 2.3 Promotion is a separate change

Every test-local entry is reviewed in the diff of the pull request that adds
it; the description reports the run's coverage and cost, not the entries
(§FS-real-use-test-generation.2.7). Those entries are candidates,
not metadata: moving one into `metadata/` is a separate change held to
§root/FS-tests, so a test in `tests/` must justify it.

## 3. How the suite is exercised

- The suite runs only through its own tasks, one for the JVM and one for
  Native Image, with the standard `-Pcoordinates=` filter
  (§AR-real-use-test-harness.2). In CI, the `tests/` jobs never run a
  real-use test, and the real-use job never runs a `tests/` test. Style
  checks run no tests, so they cover both suites.
- Locally, real-use tests are run to verify the suite: by a contributor before
  opening a pull request, and by the generation workflow before it publishes,
  each scenario on the JVM as it is written and the whole project on Native
  Image (§FS-real-use-test-generation.2.3, §FS-real-use-test-generation.2.4).
- CI runs every real-use project a pull request changes, on the JVM and on
  Native Image, as a PR gate (§root/FS-repository-functional-spec.5.3). A pull
  request that changes no real-use project reports success without running
  one. A change under `metadata/` does not trigger the suite: shipped metadata
  is additive, and real-use work never changes it (§FS-real-use-tests.2.1).
