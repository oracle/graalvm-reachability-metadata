# METADATA-suite: The `metadata/` suite

[`metadata/`](../metadata) is the repository's product: the curated GraalVM
reachability metadata that native-build-tools resolves and passes to
`native-image`. Everything else in the repository exists to produce, validate,
or describe it. Its contract toward consumers is normative in
§FS-repository-functional-spec.4 (distribution and the native-build-tools
interface); this document describes the suite's purpose and layout.

The metadata is purely additive — it only fills in registrations `native-image`
would otherwise miss, and must never change how a consumer's code runs
(§FS-repository-functional-spec.3, §GRUND-repository-motivation).

## 1. Layout

- `metadata/<groupId>/<artifactId>/index.json` — one per supported artifact: a
  JSON array recording, per metadata version, the `tested-versions`,
  `allowed-packages`, the source/test/documentation/repository URLs, and optional
  `default-for`, `latest`, `override`, `requires`, `test-version`,
  `skipped-versions`, and `language`. This is how native-build-tools selects a
  metadata version for a dependency (§FS-repository-functional-spec.4).
- `metadata/<groupId>/<artifactId>/<metadata-version>/reachability-metadata.json`
  — the only file `native-image` loads. It carries `reflection`, `jni`,
  `resources`, `bundles`, `serialization`, and `foreignCalls` entries in the
  single-file format of the vendored `reachability-metadata` schema. The legacy
  split-config files (`reflect-config.json`, `jni-config.json`,
  `resource-config.json`, `serialization-config.json`, `proxy-config.json`) are
  never used here.
- `metadata/library-and-framework-list.json` — the master list of every
  supported library with its `test_level`, driving the
  libraries-and-frameworks page.
- [`metadata/schemas/`](../metadata/schemas) — vendored JSON schemas
  (`reachability-metadata`, `metadata-library-index`,
  `library-and-framework-list`) used for offline validation; also packaged into
  the release.

### `not-for-native-image` index entries

An `index.json` may instead be a single-element array whose one entry sets
`not-for-native-image: true`, with a required `reason` and an optional
`replacement`. This marks an artifact that is intentionally **not** a
native-image metadata target — a Scala.js artifact, a pure test framework, an
artifact superseded by another coordinate — so the absence of any
`metadata-version` directory is a recorded decision rather than a missing-support
gap. The entry carries no `tested-versions` or `allowed-packages`, and
native-build-tools loads no metadata for such a coordinate.

## 2. Invariants

- **Conditional on `typeReached`, and nothing else.** Every reachability-metadata
  entry is gated on a `condition` whose only key is `typeReached`.
  `typeReached` is the sole condition key the vendored schema accepts; no other
  condition form — including the older `typeReachable` — is valid, and
  `checkMetadataFiles` rejects any entry that uses one. The reached type must lie
  inside the artifact's `allowed-packages`, and no entry may target a test-only
  type (§FS-repository-functional-spec.5.1). No build-time-initialization
  directives, library patches, or `Feature` classes ship here
  (§FS-repository-functional-spec.3).
- **Schema semver tracks the native-build-tools contract.** The
  `reachability-metadata` and `metadata-library-index` schemas follow semver
  against that contract: **minor and patch bumps stay backward-compatible** with
  already-released native-build-tools (they add optional fields or auxiliary,
  non-plugin formats), while a **major bump (e.g. `reachability-metadata` 1.2.0 →
  2.0.0) represents a required change in native-build-tools** and is released in
  lockstep with a plugin update — it is not an additive change older plugins
  silently absorb (§FS-repository-functional-spec.4).
- **Schema-valid and sorted.** JSON validates against its vendored schema and is
  key-sorted; `index.json` integrity and entry validity are enforced by
  `validateIndexFiles` and `checkMetadataFiles` (§TCK-test-harness.2) and the
  matching CI workflows (§CI-repository-ci).

### Test-only metadata is kept out of the shipped metadata

Tests routinely need reachability metadata for their *own* helper types — the
test classes, fixtures, and resources that exercise the library — but those
entries must never reach a consumer (§FS-repository-functional-spec.5.1). The
harness `splitTestOnlyMetadata` task (§TCK-test-harness.5) maintains that
separation: any entry whose `type` or `condition.typeReached` names a test
package, or whose resource is a test resource, is moved out of the library's
`metadata/<group>/<artifact>/<version>/reachability-metadata.json` and into the
test project's
`tests/src/<group>/<artifact>/<version>/src/test/resources/META-INF/native-image/reachability-metadata.json`.
The shipped metadata therefore stays free of test-only types, while the test
native image still loads everything it needs from its own resources.

## 3. Provenance

No file here is hand-trusted: each metadata entry is justified by a test in the
`tests/` suite (§TESTS-suite, §GOAL-tested-metadata) and is added or updated only
through the harness authoring tasks (§TCK-test-harness.5) by a human contributor
or by Forge (§forge/FS-forge-functional-spec). Coverage grows without weakening
what already ships (§GOAL-protect-shipped-metadata).
