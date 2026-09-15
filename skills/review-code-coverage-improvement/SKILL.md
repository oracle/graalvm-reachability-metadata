---
name: review-code-coverage-improvement
description: Review pull requests with the `code-coverage-improvement` label in graalvm-reachability-metadata. Use when asked to review or triage a PR that broadens JaCoCo coverage for an already-supported library, especially generated PRs titled like `[GenAI] Improve code coverage for com.google.code.gson:gson:2.3 using gpt-5.6-luna`. Applies three acceptance rules: overall library coverage must improve, shipped metadata may gain entries but never lose them, and no test-only type may reach shipped metadata.
---

# Review `code-coverage-improvement` PRs

These PRs come from the Forge code coverage workflow
(§forge/AR-code-coverage-improvement). They add a tracked extension test suite
under `tests/src/<group>/<artifact>/<version>/code-coverage-improvement/` to
raise how much of an already-supported library is exercised, and they regenerate
the coordinate's committed stats.

They are not a metadata workflow. The metadata they touch is a side effect of the
native-metadata preparation the deep phase needs for its PGO sampling builds
(§forge/AR-code-coverage-improvement-architecture.1), produced by a tracing agent
run over a suite whose purpose is to reach as much code as possible.

The PR number or URL can be passed as an optional argument (for example, `9500`).
If the user says "review this PR" without one, infer it from the conversation or
`gh pr status`; only ask when it cannot be inferred.

## The three rules

Everything else in this skill supports these. Block only on a violation of one.

### 1. Overall coverage must improve

The point of the PR. The committed figure is
`versions[0].libraryCoverage` in `stats/<group>/<artifact>/<version>/stats.json`,
which finalization regenerates from the combined main-JAR-only JaCoCo report.

- The `instruction` ratio must increase. The `line` and `method` ratios must not
  decrease.
- Each metric's `total` must be unchanged. A moved denominator means the jar
  classification changed and the ratios are not comparable — ask before judging.
- `versions[0].dynamicAccess.coverageRatio` must not regress and must not become
  `N/A`. `N/A` means the native build in the stats step failed; ask for a rerun
  rather than accepting a degraded figure.

```bash
gh pr diff <pr> -- 'stats/**/stats.json'
```

The workflow's own `apiJacoco` and `deepJacoco` blocks in
`stats/<group>/<artifact>/<version>/forge-publication.json` are per-phase
internals. Read them for context, but gate on `libraryCoverage`.

### 2. Metadata is added, never removed

A coverage PR regenerates metadata wholesale, so the risk is a regeneration that
silently drops entries a consumer depends on. No library-owned entry may leave
`metadata/<group>/<artifact>/<version>/reachability-metadata.json`.

Removing an entry that rule 3 says should never have shipped is correct, not a
violation — rule 3 says where it goes instead. Every other removal is blocking.

```bash
gh pr diff <pr> -- 'metadata/**/reachability-metadata.json' | grep '^-' | grep -v '^---'
```

Additions are fine. An entry a typical consumer would not reach is
over-approximation, and the asymmetry favours keeping it: missing metadata is a
runtime failure, a surplus entry is a slightly larger image. Do not ask for
library-owned entries to be stripped because the path looks unlikely.

### 3. No test-only metadata in shipped metadata

An entry is test-only when its `type` or its `condition.typeReached` names a class
defined in a test source tree rather than in the library artifact. Those entries
belong in the test project's
`src/test/resources/META-INF/native-image/reachability-metadata.json` and must
never reach a consumer (§FS-metadata.2, §FS-repository-functional-spec.5.1).

Judge by where the class is defined, not by its package. The generated tests are
written into library packages, so `com.google.gson.GsonApiTest$Payload` and
`okhttp3.internal.http2.Http2ApiCoverageTest` are test-only despite sitting under
library package names.

```bash
gh pr diff <pr> -- 'metadata/**/reachability-metadata.json' \
  | grep -E '^\+.*"(type|typeReached)"'
```

Cross-check each added name against the classes defined in the PR's test sources
and in the existing test tree. A hit is blocking.

The remedy is relocation, never deletion — the entry records dynamic access a
real traced execution needed:

- An entry whose `type` is test-defined moves to the test project's metadata
  file.
- An entry whose `type` is library-owned but whose `condition.typeReached` is
  test-defined may stay shipped only re-conditioned on a library-defined type
  that actually reaches it; otherwise it moves too.
- The same discipline runs the other way: an entry leaves the test project's
  metadata file only by promotion into shipped metadata under a library-owned
  condition. A regeneration that plainly drops test-only entries breaks the
  suite's native run the same way rule 2's removals break a consumer's.

Nothing in the harness catches this for you:

- `splitTestOnlyMetadata` discovers test packages by walking `src/test/java`,
  `src/test/kotlin`, `src/test/groovy`, and `src/test/scala`
  (`tests/tck-build-logic/src/main/java/org/graalvm/internal/tck/utils/MetadataGenerationUtils.java`).
  The coverage suite sits at `code-coverage-improvement/src/test/java`, outside
  that list, so it can never split a coverage-suite entry out (§AR-test-harness.5).
- `checkMetadataFiles` only validates `condition.typeReached` against the
  artifact's `allowed-packages`, which a test class inside a library package
  satisfies.
- No workflow under `.github/workflows` sets `-PincludeCodeCoverageSuite=true`, so
  CI never compiles or runs the suite in either lane. Green CI says nothing about
  this PR's tests or the metadata they produced.

## Supporting checks

Not rules on their own, but worth a comment or a question:

- **Scope.** Expected files are the coordinate's `code-coverage-improvement/**`,
  its `stats.json` and `forge-publication.json`, its two metadata files, and a
  `build.gradle` touch for a test dependency. Other coordinates, build logic, or
  workflows are blocking. The regular `src/test` sources are read-only for this
  workflow (§forge/AR-code-coverage-improvement.2).
- **Test quality.** Tests should assert real behavior. Calling internal or
  package-private methods directly, or reflecting into non-public members, purely
  to move the number is against §forge/AR-code-coverage-improvement.5; the deep
  phase is where it concentrates. Raise it, and block when a test asserts nothing.
- **Package placement.** Tests in library packages bypass visibility boundaries
  (§FS-test-contract) and are what makes rule 3 need a human. Ask for a package
  outside the library's.
- **Legacy configs.** `reflect-config.json`, `jni-config.json`,
  `resource-config.json`, `serialization-config.json`, and `proxy-config.json`
  anywhere under the coordinate's test tree are tracing-agent leftovers and are
  blocking (§FS-metadata.1). The suite must not carry a metadata directory of its
  own.
- **Native skips.** Reject skips, early returns, and swallowed failures.
  `NativeImageSupport.isUnsupportedFeatureError(e)` is acceptable only for
  genuinely open-ended dynamic class loading.
- **PGO claims.** Sampled profiles are navigation, never coverage
  (§forge/AR-code-coverage-improvement.6).
- **Local evidence.** `local_ci_verification.status` in `forge-publication.json`
  must be `success` with `human_intervention_required` false.

## Decision Rules

Approve when all three rules hold, the diff is scoped to one coordinate, and
required CI checks are green.

Request changes when:

- `libraryCoverage.instruction.ratio` did not increase, or `line`/`method`
  decreased.
- `dynamicAccess.coverageRatio` regressed or became `N/A` (ask for a rerun).
- A library-owned entry was removed from shipped metadata.
- A shipped entry names a class defined in a test source tree.
- A test-only entry was deleted outright — from either file — instead of moved
  to the test project's metadata file or re-conditioned on a library-owned type.
- A legacy split-config file appears, or the suite carries its own metadata
  directory.
- A test asserts nothing, reaches coverage only by invoking internals, or skips
  the native path.
- The diff touches other coordinates or repository infrastructure.

## Output Style

Short, factual, blocking.

- Coverage: cite the old and new `libraryCoverage` ratios from `stats.json`.
- Removed metadata: name the dropped entries and say a coverage PR must not
  shrink shipped metadata.
- Test-only leak: name the entry, say the class is defined in the PR's test
  sources, ask for the move (or the library-owned re-condition), not a
  deletion, and note that `splitTestOnlyMetadata` cannot separate it because
  the coverage suite is outside the scanned source sets.
- Internals-poking: say the test raises the number without exercising behavior a
  consumer can reach.
- PGO-as-coverage: say only JaCoCo states coverage.

## Examples

Blocking — test type in shipped metadata. `GsonApiTest$Payload` is defined in the
PR's own test sources, and the condition is the library façade, so it loads for
every consumer of Gson:

```json
{
  "condition" : { "typeReached" : "com.google.gson.Gson" },
  "type" : "com.google.gson.GsonApiTest$Payload",
  "fields" : [ { "name" : "count" } ]
}
```

Fine — keep it. The library performs this lookup itself and expects it to fail;
without the entry `native-image` raises `MissingReflectionRegistrationError`
instead of `ClassNotFoundException`. Both names are library-owned:

```json
{
  "condition" : { "typeReached" : "okhttp3.internal.platform.AndroidPlatform" },
  "type" : "com.android.org.conscrypt.SSLParametersImpl"
}
```
