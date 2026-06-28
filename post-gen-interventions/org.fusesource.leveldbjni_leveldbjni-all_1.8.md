# Post-generation intervention report

Library: org.fusesource.leveldbjni:leveldbjni-all:1.8
Stage: metadata_fix_failed

## Summary

The generated native test `openDatabaseLoadsBundledNativeLibraryFromClasspath(Path)` failed during `:nativeTest` with `UnsatisfiedLinkError` for `org.fusesource.leveldbjni.internal.NativeOptions.init()V`. The test opened a real LevelDB database through `JniDBFactory`, which forces HawtJNI to extract and dynamically load the bundled `libleveldbjni.so` and then call native JNI entry points from the native image.

## Root cause

This failure is not a missing reachability-metadata failure. The Gradle output and Codex metadata-fix log do not show a `Missing*RegistrationError` or inactive conditional metadata. Instead, the image starts, `org.fusesource.hawtjni.runtime.Library` calls `System.loadLibrary`, and the runtime then fails to resolve the JNI symbol for `NativeOptions.init`:

```text
java.lang.UnsatisfiedLinkError: org.fusesource.leveldbjni.internal.NativeOptions.init()V
[symbol: Java_org_fusesource_leveldbjni_internal_NativeOptions_init or Java_org_fusesource_leveldbjni_internal_NativeOptions_init__]
```

Codex attempted metadata repairs around JNI accessibility, native methods, and bundled native resources, but the failure remained. A diagnostic rebuild with `-H:+PrintJNIMethods` showed that `NativeOptions.init()` was present in the native image JNI registry, so the remaining problem is native-image runtime linkage of the dynamically loaded HawtJNI/LevelDB native library, not absent metadata.

## Intervention

Removed the generated test that exercised the unsupported path:

- `tests/src/org.fusesource.leveldbjni/leveldbjni-all/1.8/src/test/java/org_fusesource_leveldbjni/leveldbjni_all/LibraryTest.java`

Also removed the generated AssertJ test dependency from:

- `tests/src/org.fusesource.leveldbjni/leveldbjni-all/1.8/build.gradle`

No metadata files were modified as part of this intervention.

## Why preserve the remaining generated support

The generated library scaffold and metadata should still be preserved because Codex produced support for the library's reachable classes, JNI-accessible types, and bundled native resource discovery. The deleted test is specifically the part that requires native-image runtime loading and JNI symbol resolution for the bundled LevelDB shared library. Keeping the remaining support avoids discarding useful reachability metadata while removing only the generated test that depends on unsupported native-image behavior.

## Verification

Ran:

```bash
./gradlew test -Pcoordinates=org.fusesource.leveldbjni:leveldbjni-all:1.8 --stacktrace
```

Result: `BUILD SUCCESSFUL`; `nativeTestCompile` and `nativeTest` were skipped because the failing generated test was removed.
