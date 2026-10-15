# AR-real-use-test-harness: Real-use test harness

The real-use suite (§FS-real-use-tests) runs on the same harness as `tests/`:
the same per-coordinate build, coordinate filter, and Docker handling, behind
its own task names so the two suites never run each other's projects. This
document covers the project build, the tasks, the CI job, and the repository
wiring.

## 1. Project build

A real-use project applies the same per-coordinate convention plugin as a
`tests/` project (§root/AR-build-infrastructure.2), so it compiles, runs JUnit, and
builds its native test image the same way. The plugin also puts AssertJ on a
real-use project's test classpath, because a scenario declares libraries it
uses, never test tooling. That plugin enables the
native-build-tools metadata repository rooted at the repository's `metadata/`
directory, which is what makes the native build consumer-like: every library on
the classpath gets its shipped metadata (§FS-real-use-tests.2.1). The project's
test-local `reachability-metadata.json` sits in its test resources, so it is on
the image classpath as well (§FS-real-use-tests.2.2). Docker images in
`required-docker-images.txt` are pulled and checked as for `tests/`
(§root/AR-test-harness.6).

## 2. Tasks

Every task takes the standard `-Pcoordinates=` filter — `all`,
`group:artifact`, `group:artifact:version`, or a `k/n` shard — through the
shared resolver (§root/AR-test-harness.1), rooted at `real-use-tests/src/`. The
selection is the filter applied to the coordinates that have a real-use
project; a coordinate without one is skipped, not failed. Shards split that
set, and `-Ptck.excludedCoordinatesFile` applies as it does elsewhere.
`scaffoldRealUseTest` instead takes one supported coordinate that has no
project yet.

| Task | Purpose |
| --- | --- |
| `realUseJavaTest` | Run the selected real-use projects on the JVM. |
| `realUseNativeTest` | Build the native test image and run it. |
| `scaffoldRealUseTest` | Create a coordinate project from the real-use template. |
| `generateChangedRealUseMatrix` | The CI matrix of the real-use projects changed between `-PbaseCommit` and `-PnewCommit` (§AR-real-use-test-harness.4). |
| `generateRealUseFilter` | Write `user-code-filter.json` covering every package in the JARs of the target and of the libraries `build.gradle` declares, one `<package>.**` rule per package not nested in another; the harness's own JUnit, AssertJ, and test-support libraries are not declarations. |

`realUseNativeTest` accepts `-Ptck.realUse.exactReachability=true`, which adds
`--exact-reachability-metadata` to the native build as the trace image does
(§root/AR-test-harness.4), so a missing registration fails naming the missing
element. The generation workflow's native fix agent uses it so it reads a
precise failure (§FS-real-use-test-generation.2.5); the workflow's programs,
the publish check, and CI build without it, the way a consumer does.

`realUseJavaTest` accepts `-Ptck.realUse.agentOutput=<directory>`, which runs
the tests with the native-image agent in conditional mode, filtered by the
project's `user-code-filter.json`, and writes the collected metadata to that
directory. The native fix uses it once (§FS-real-use-test-generation.2.5).

Style has no task of its own: the repository's style checks scan the suite
(§FS-real-use-test-generation.2.7). The root `spotlessCheck` already holds every
Java, Groovy, Gradle, shell, and Python file under `real-use-tests/` to the CC0
header, and its JSON format covers `real-use-tests/src/` beside `metadata/` and
`tests/src/`. `checkstyle -Pcoordinates=` runs checkstyle in the coordinate's
real-use project, when it has one, after its `tests/` project, and the
checkstyle gate's changed-coordinate matrix includes every coordinate whose
real-use project a pull request changes (§root/AR-checkstyle).

## 3. Separation from the `tests/` lanes

The `tests/` resolver is rooted at `tests/src/` and the real-use resolver at
`real-use-tests/src/`, so each suite's test tasks run only its own projects:
`test`, `javaTest`, and `nativeTest` never run a real-use test, and the
`realUse*` tasks never run a `tests/` test. CI keeps the same split: the
`tests/` test jobs never run a real-use test, and the real-use job of §4 never
runs a `tests/` test (§FS-real-use-tests.3). The style checks run no tests and
cover both suites, so `checkstyle`, `testAllInfra`, and the checkstyle CI
matrix also check changed real-use projects. Stats, coverage, and
dynamic-access reports read only `tests/`.

## 4. CI job

A pull-request workflow runs the real-use projects the pull request changes
(§FS-real-use-tests.3):

- it starts for any change under `real-use-tests/`, gated on the
  detect-file-changes action (§root/AR-detect-file-changes), and
  `generateChangedRealUseMatrix` maps the changed paths to the coordinates of
  the real-use projects that still exist, failing for a project whose
  coordinate the repository does not support (§FS-real-use-tests.1);
- it runs `realUseJavaTest`, then `realUseNativeTest`, for those coordinates
  on the same `ci.json` matrix the `tests/` PR workflow uses
  (§root/AR-test-changed-metadata, §root/AR-matrix-source);
- it reports one always-present status check, which passes without running
  anything when no real-use project changed, and that check joins the required
  set (§root/AR-required-status-checks).

## 5. Repository wiring

- `real-use-tests/` is a grund workspace member with its own `grund.toml`, as
  Forge is. Its coordinate projects under `src/` are a listed, unscanned kind
  like the root `tests` kind, and the Rhei template folder is a scanned kind
  that must cite `FS` (§AR-real-use-test-workflow). The root `grund check`
  validates it, and the grund CI job's path filter includes it.
- The fissile scan covers `real-use-tests/docs/` and the Rhei template, held to
  the same budgets as the root docs and Forge, and leaves `src/` out as it does
  `tests/`.
