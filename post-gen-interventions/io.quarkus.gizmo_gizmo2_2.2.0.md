# Post-generation intervention report

Library: io.quarkus.gizmo:gizmo2:2.2.0
Stage: metadata_fix_failed

## Summary

The Codex metadata-fix log showed one native test failure in the generated `TestClassMakerInnerLoaderTest.rejectsInvalidLocalClassResource()` test. The failure was not metadata-related: the native image built successfully, then the test failed at runtime with `com.oracle.svm.core.jdk.UnsupportedFeatureError: The assertion status of classes is fixed at image build time` from `ClassLoader.setDefaultAssertionStatus(...)` inside `io.quarkus.gizmo2.testing.TestClassMaker.create()`.

The Gradle failure excerpt is also not metadata-related. It failed while resolving the `:tck-build-logic:compileClasspath` for `:tck-build-logic:compileJava` with `java.io.IOException: No space left on device`, before any library metadata or generated test assertion could run.

## Intervention

Removed the generated `rejectsInvalidLocalClassResource()` test and its now-unused invalid-class constant from:

- `tests/src/io.quarkus.gizmo/gizmo2/2.2.0/src/test/java/io_quarkus_gizmo/gizmo2/TestClassMakerInnerLoaderTest.java`

No metadata files were modified.

## Metadata assessment

No remaining missing reachability metadata was identified. The failing `UnsupportedFeatureError` is a native-image runtime limitation around class assertion status, not a missing reflection, resource, JNI, proxy, or serialization registration.

## Why preserve the remaining generated support

The remaining `gizmo2` support still exercises meaningful library behavior: annotation creation, field descriptors, lambda/functional-interface generation, generated class loading, and parallel loader behavior. Codex's verification log showed the other generated tests passing in both JVM/native flow once the unsupported invalid-resource test path was handled, so the remaining tests continue to validate useful reachability metadata coverage for `io.quarkus.gizmo:gizmo2:2.2.0` and should be preserved.
