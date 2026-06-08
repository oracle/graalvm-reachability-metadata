# Post-generation intervention report

Library: org.aesh:readline:3.2
Stage: metadata_fix_failed

## Summary

The generated test `org_aesh.readline.AbstractPtyInnerReflectionFileDescriptorCreatorTest` failed during `nativeTest`. It forced JLine's JNI PTY provider path and attempted to build a real JNI PTY terminal. The native executable failed with:

```text
java.lang.IllegalStateException: Unable to create a terminal
Suppressed: java.lang.UnsatisfiedLinkError: org.jline.nativ.CLibrary$Termios.init()V
```

## Root cause

This failure is not a missing reachability-metadata entry for `org.aesh:readline:3.2`. The Codex metadata-fix log shows that the same test passed on the JVM, the bundled `libjlinenative.so` exported the expected `Java_org_jline_nativ_CLibrary_00024Termios_init` symbol, and the native-image executable extracted/opened the native library before failing. Codex also reproduced the same `CLibrary$Termios.init()` `UnsatisfiedLinkError` in the control project `org.jline:jline-terminal-jni:3.30.7` under the same GraalVM/future-defaults configuration.

That points to a broader JLine JNI/native-image runtime limitation or platform interaction, not metadata that can be added safely to `metadata/org.aesh/readline/3.2/reachability-metadata.json`.

## Intervention

Removed the generated failing test:

```text
tests/src/org.aesh/readline/3.2/src/test/java/org_aesh/readline/AbstractPtyInnerReflectionFileDescriptorCreatorTest.java
```

No metadata files were modified.

## Why the remaining generated support should be preserved

The rest of the generated test suite still exercises useful native-image reachability for `org.aesh:readline:3.2`: bundled resources, Maven `pom.properties`, terminal capability loading, service-provider loading, signal/reflection handling, ProcessHandle access, Jansi output behavior, and JLine native-library extraction without forcing the unsupported JNI PTY terminal path.

After removing only the unsupported JNI PTY test, `GVM_TCK_NATIVE_IMAGE_MODE=future-defaults-all ./gradlew test -Pcoordinates=org.aesh:readline:3.2 --stacktrace` passed with 10 native tests successful.
