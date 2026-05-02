# Post-generation intervention report

Library: ant:ant:1.6
Stage: metadata_fix_failed

## Summary

The native test run failed in `ant.ant.ClassConstantsTest#readInitializesByteArrayParameterTypeInFreshAntClassLoader` with:

```text
java.lang.ClassNotFoundException: org.apache.tools.ant.filters.ClassConstants
```

The failure occurs when the generated test creates a fresh `AntClassLoader` over `System.getProperty("java.class.path")` and asks it to `forceLoadClass(ClassConstants.class.getName())`.

## Root cause

This failure is not metadata-related. The Codex metadata-fix log shows no `Missing*RegistrationError`; Codex also tried metadata-oriented fixes around the `ClassConstants` class resource and reflection array types, then verified that they did not change the failure. A trace-enabled native run did not show a missing `ClassConstants.class` resource access either.

The remaining failure is caused by the generated test relying on JVM classpath/JAR loading semantics through an isolated Ant class loader. In the native test executable, that path cannot locate `org.apache.tools.ant.filters.ClassConstants` even though direct use of `ClassConstants` in the same test class succeeds.

## Intervention

Removed the generated test method `readInitializesByteArrayParameterTypeInFreshAntClassLoader()` and its helper code from:

```text
tests/src/ant/ant/1.6/src/test/java/ant/ant/ClassConstantsTest.java
```

No metadata files were modified.

## Why the remaining generated support should be preserved

The rest of the generated support is valuable and should remain. The Gradle output shows that the other generated native tests pass, including coverage for Ant dynamic access paths such as `AntClassLoader`, `Definer`, `IntrospectionHelper`, `ProjectHelper`, `TaskAdapter`, XML catalog resolution, XSLT processor lookup, compiler adapter lookup, RMI adapters, and the direct `ClassConstants` constant-reading path. Preserving these tests keeps broad reachability coverage for `ant:ant:1.6` while removing only the single test that depends on unsupported isolated runtime classpath loading behavior.
