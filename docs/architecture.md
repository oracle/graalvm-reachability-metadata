# AR-repository-architecture: Repository architecture and high-level overview

This is the structural map of the repository: what the major components are, how
they fit together, and how a change flows from request to released artifact. It
is the entry point for understanding the system; the behavioral contract for
each part is in the functional spec (§FS-repository-functional-spec) and the
per-component module docs cited below.

## 1. What the repository is

At its core the repository is a **distribution of additive GraalVM reachability
metadata** for community JVM libraries, wrapped in everything needed to keep
that metadata correct: a test harness, continuous integration, a derived metrics
mirror, and AI automation. The defining architectural constraint is additivity —
shipping this metadata into a consumer's build must only fill in registrations
`native-image` cannot infer, never change how the consumer's code runs. That
invariant and its consequences (no build-time-initialization tweaks, no library
patching, no `Feature` classes, no untested metadata) are specified in
§FS-repository-functional-spec.3 and are why every other component exists.

## 2. Components

| Component | Role | Spec |
| --- | --- | --- |
| `metadata/` | The shipped product: per-artifact `index.json` and per-version `reachability-metadata.json`, plus the master library list and schemas. | §METADATA-suite |
| `tests/` suite | The tests that justify each metadata entry, plus the harness build logic and support library. | §TESTS-suite |
| `stats/` | The derived per-version metrics mirror and Forge run records that feed the coverage dashboard. | §forge/FS-forge-run-metrics |
| Test harness (TCK) | The Gradle task surface that validates, compiles, and tests each coordinate on the JVM and `native-image`. | §TCK-test-harness |
| Build infrastructure | The two-layer Gradle build that wires the harness, the convention plugins, scaffolding, and packaging. | §AR-build-infrastructure |
| Infrastructure E2E tests | `testInfra`/`testAllInfra`, which exercise the whole task surface to prove the infrastructure itself works. | §E2E-infrastructure-tests |
| CI | GitHub Actions workflows that gate PRs, sweep metadata on a schedule, track upstream versions, scan images, and publish coverage and releases. | §CI-repository-ci |
| Forge | The automation subproject that turns labeled issues into review-ready PRs. | §forge/AR-forge-architecture |

## 3. How work flows through the system

A change enters as an issue (a support request or a compatibility failure) and
leaves as released metadata:

1. **Authoring.** A contributor or Forge scaffolds a coordinate, writes tests
   that fail without metadata, and collects metadata through the harness
   authoring tasks (§TCK-test-harness, §TESTS-suite).
2. **Local verification.** The harness validates the index and metadata and runs
   the JVM and native-image lanes for that one coordinate
   (§FS-repository-functional-spec.8.2).
3. **Review and CI.** A single-library PR is gated by the matching CI workflows,
   which re-run the same harness tasks as the authority and enforce licensing,
   security, and non-regression rules (§CI-repository-ci,
   §FS-repository-functional-spec.5).
4. **Recording coverage.** Newly passing versions are recorded into `index.json`
   and mirrored into `stats/`; the coverage dashboard is regenerated and
   published (§forge/FS-forge-run-metrics, §CI-repository-ci).
5. **Release.** On `master` pushes and on a weekly cadence the metadata
   directory is packaged and attached as a GitHub Release, which
   native-build-tools resolves at build time
   (§FS-repository-functional-spec.4, §GOAL-fresh-metadata).

Two scheduled loops keep this self-sustaining: the full metadata sweep guards
against regressions before releases, and the upstream-version tracker discovers
new versions, records the passing ones, and files failure issues that become the
Forge repair queue (§FS-repository-functional-spec.9, §CI-repository-ci).

## 4. Implementation overview

- **Gradle, two layers.** A root harness layer owns coordinate selection,
  validation, reporting, matrix generation, and packaging; per-coordinate
  sub-builds compile and run each library's tests in isolation. Adding a library
  needs no build-logic change — discovery is metadata-driven
  (§AR-build-infrastructure, §TCK-test-harness).
- **GitHub Actions, matrix-driven.** All test workflows derive their JDK/OS
  matrix from `ci.json`, the single source of truth, and run the same Gradle
  tasks a contributor runs locally (§CI-repository-ci).
- **Schemas everywhere.** Metadata, index, stats, and the framework list each
  validate against a vendored JSON schema. Minor and patch schema bumps stay
  backward-compatible so older consumers keep working; a major bump represents a
  change native-build-tools must adopt, released in lockstep with the plugin
  (§METADATA-suite, §FS-repository-functional-spec.4).
- **Separation of automation.** Forge is an independently checked subproject in
  its own grund namespace; from the repository's side it is just another
  contributor bound by the same constraints and gates
  (§forge/AR-forge-architecture, §FS-repository-functional-spec.4).
