# Post-generation intervention

Library: org.junit.platform:junit-platform-suite-api:6.1.0

Stage: `metadata_fix_failed`

## Failure summary

The coordinate's `nativeTest` executable failed before any generated test method ran. During native JUnit launcher discovery, `org.junit.platform.engine.UniqueId` initialized and Java serialization requested a serialization constructor accessor for `org.junit.platform.engine.UniqueId$SerializedForm`. Native Image reported that this class was not registered for serialization. The outer `tckTest` failure is only the wrapper reporting the same non-zero `nativeTest` exit.

This is a metadata-related failure, not unsupported runtime behavior and not a defect in a generated test. No generated test or test-only support file was removed.

## Missing metadata and Codex outcome

The metadata still needs a serialization registration for `org.junit.platform.engine.UniqueId$SerializedForm`, appropriately conditioned on `org.junit.platform.engine.UniqueId` being reached. The existing `6.1.0` metadata only registers the generated test's `LifecycleFixture` for reflection and therefore does not satisfy the launcher failure.

Codex could not finish because its workspace was read-only and Gradle could not create its lock file under `/tmp`; elevated execution was rejected. Consequently it could neither edit nor verify metadata. Its proposed registration of the generated `beforeAllSuites()` and `afterAllSuites()` methods also did not address the observed serialization failure, so the attempted diagnosis was incomplete.

## Why the generated support should be preserved

The generated suite meaningfully exercises the public Suite API's annotations, selectors, repeatable containers, inheritance, default values, and lifecycle annotations. The failure occurs in common JUnit launcher setup before those tests execute, so removing any individual test would discard valid coverage without correcting the missing serialization registration. Preserving the suite keeps the evidence needed to validate the coordinate once the metadata gap is fixed (§FS-repository-functional-spec.5.2).
