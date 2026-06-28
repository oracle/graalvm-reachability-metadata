# Post-generation intervention

Library: org.clojure:tools.reader:0.9.2
Stage: metadata_fix_failed

## Summary

The native test image built successfully, but `nativeTest` failed before executing any test methods. The generated `Tools_readerTest` class initialized Clojure through `RT.var("clojure.core", "require")` in `@BeforeAll`, which triggered `clojure.lang.RT` initialization inside the native executable. At runtime, Clojure could not locate `clojure/core__init.class` or `clojure/core.clj` on the native-image classpath and raised an `ExceptionInInitializerError`.

## Root cause

This was not a reachability-metadata failure. The failure was Clojure runtime namespace loading/AOT bootstrap behavior exercised by the generated test. Codex did not find a `Missing*RegistrationError`; its log shows attempts to work around Clojure namespace resource discovery by changing test build resources, copying AOT classes, and trying direct `Class.forName(...)` initialization. Those attempts either caused Gradle/test-harness errors or still left Clojure's native runtime loader unable to resolve namespace initializer resources.

Because the failing path depends on runtime class/resource loading for Clojure namespaces, it falls under unsupported native-image runtime loading behavior for this intervention workflow. I removed the generated JUnit test class that required that path and reverted the failed test-only build/resource workarounds. Metadata files were not modified.

## Preserved generated support

The remaining generated library support should be preserved because the failure was caused by the test harness bootstrapping Clojure dynamically, not by invalid library coordinates or by a proven bad metadata entry. The generated index/metadata support still records `org.clojure:tools.reader:0.9.2` and its package ownership for future, narrower tests that avoid Clojure runtime namespace loading in a native executable.

## Verification

After removing the generated test, I ran a clean targeted verification:

```text
./gradlew clean -Pcoordinates=org.clojure:tools.reader:0.9.2
./gradlew test -Pcoordinates=org.clojure:tools.reader:0.9.2 --stacktrace
```

The clean targeted run completed successfully; `nativeTestCompile` and `nativeTest` were skipped because there are no remaining generated tests for this coordinate.
