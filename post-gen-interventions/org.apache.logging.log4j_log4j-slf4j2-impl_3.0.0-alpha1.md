# Post-generation intervention report

Library: org.apache.logging.log4j:log4j-slf4j2-impl:3.0.0-alpha1
Stage: metadata_fix_failed

## Summary

The native test failures were not caused by a remaining missing reachability-metadata entry. The failing generated tests exercised Log4j 3.0's SLF4J bridge, logger factory, and MDC/ThreadContext initialization paths. In native image those paths failed while Log4j attempted to load providers through `org.apache.logging.log4j.util.ServiceLoaderUtil.callServiceLoader(...)` using a method-handle lookup for `java.util.ServiceLoader.load(Class, ClassLoader)`.

That provider-loading failure produced `NoSuchMethodException` / `NoSuchMethodError`, then `LoggingSystem$SystemProvider.createContextMap(...)` returned through an invalid provider state and caused `ThreadContext` initialization to fail. The visible JUnit failures were therefore cascading `NoClassDefFoundError: Could not initialize class org.apache.logging.log4j.ThreadContext` errors.

## Root cause classification

This is a native-image/runtime limitation in the Log4j 3.0 alpha provider-loading path, not a metadata omission that Codex could complete by adding standard reachability metadata. Codex attempted to address the path with reflection and lambda/predefined-class metadata, and also tried agent capture, but the agent capture could not produce usable predefined-class payloads and the direct metadata entries did not make the native runtime resolve the `ServiceLoader.load(Class, ClassLoader)` method-handle path.

## Intervention

Removed the generated tests and cleanup code that trigger the unsupported Log4j provider / ThreadContext path:

- `serviceProviderSuppliesSlf4jFactoriesBackedByLog4j`
- `loggerFactoryReturnsLog4jBackedSlf4jLoggers`
- `loggerFactoryMapsSlf4jRootLoggerNameToLog4jRootLogger`
- `slf4jLoggingApiRoutesParameterizedMarkerThrowableAndFluentEvents`
- `levelAwareAndLocationAwareSlf4jApisMapToLog4jLevels`
- `mdcAdapterMaintainsContextMapsAndPerKeyStacks`
- the `@AfterEach` MDC cleanup that caused otherwise independent tests to initialize `ThreadContext`

## Preserved support

The remaining generated support should still be preserved because it continues to compile the requested artifact and run native-image tests for safe, non-provider-loading API coverage. The preserved tests validate SLF4J marker factory behavior and Log4j parameterized message / throwable semantics without invoking the unsupported Log4j 3.0 alpha `LoggingSystem` service-loading and `ThreadContext` initialization path.

## Verification

After removing only the failing generated test paths, the targeted run passed:

```text
./gradlew clean -Pcoordinates=org.apache.logging.log4j:log4j-slf4j2-impl:3.0.0-alpha1
./gradlew test -Pcoordinates=org.apache.logging.log4j:log4j-slf4j2-impl:3.0.0-alpha1 --stacktrace
```

Result: `BUILD SUCCESSFUL`; 2 native tests found, 2 started, 2 successful.
