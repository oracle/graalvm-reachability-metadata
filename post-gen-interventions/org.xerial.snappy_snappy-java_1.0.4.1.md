# Post-generation intervention report

Library: org.xerial.snappy:snappy-java:1.0.4.1
Stage: metadata_fix_failed

## Summary

The metadata-fix attempt did not leave a runnable coordinate. The latest Gradle run failed in `:tckTest` before executing tests because `org.xerial.snappy:snappy-java:1.0.4.1` no longer matched the metadata index: the worktree already had the coordinate replaced by a `not-for-native-image` entry, and the generated test project had been deleted.

The earlier native test failures were not metadata-fixable. The generated tests exercised `snappy-java` 1.0.4.1 native-library bootstrapping, which reads `org/xerial/snappy/SnappyNativeLoader.bytecode` from the JAR and defines/loads the native loader class at runtime. Runtime class definition/loading is unsupported native-image behavior, so these tests should be removed instead of preserved with additional metadata.

## Failure root causes

- `SnappyLoaderSystemLibraryTest.loadsNativeLibraryFromJavaLibraryPath()` failed in native image with `org.xerial.snappy.SnappyError: [FAILED_TO_LOAD_NATIVE_LIBRARY] /org/xerial/snappy/SnappyNativeLoader.bytecode is not found` from `SnappyLoader.injectSnappyNativeLoader`. Although the immediate message mentions a missing bytecode resource, this path exists only to perform runtime loader injection/class definition, which is unsupported in native image.
- `SnappyLoaderTest.loadsBundledNativeLibraryAndRoundTripsData(...)` failed after the same `Snappy` class initialization path, reported as `NoClassDefFoundError: Could not initialize class org.xerial.snappy.Snappy`. This is the same unsupported bootstrap mechanism, not an independent missing-metadata problem.
- The final Gradle excerpt failed with `No matching coordinates found for 'org.xerial.snappy:snappy-java:1.0.4.1'` because the coordinate had already been removed from the tested-version index/not-for-native-image classified. That failure is not a missing reachability metadata error.

## Intervention

Removed the generated test project files for `tests/src/org.xerial.snappy/snappy-java/1.0.4.1`, including the generated test classes and test-only project configuration. I did not modify metadata files.

## Why preserve the remaining generated support

The remaining support should be preserved because it records the outcome that `snappy-java` 1.0.4.1 is not suitable for native-image testing through generated metadata: its public compression APIs in this version depend on runtime native-loader bytecode injection. Keeping that classification prevents further metadata-only retries from chasing an unsupported native-image behavior while preserving the useful investigation result for reviewers and future automation.
