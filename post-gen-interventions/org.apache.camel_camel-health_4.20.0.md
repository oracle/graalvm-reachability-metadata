# Post-generation intervention report

Library: org.apache.camel:camel-health:4.20.0
Stage: metadata_fix_failed

## Summary

The native test run failed in exactly one generated test:

- `Camel_healthTest.defaultLoaderDiscoversClasspathHealthChecksFromCamelServiceDescriptors()`

The failure was an assertion failure, not a `Missing*RegistrationError`: `DefaultHealthChecksLoader` returned no health checks in the native image, so the generated assertion could not find the expected `route-controller` id.

## Root cause

This remaining failure is not metadata-related. The Codex metadata-fix log shows that direct service descriptor resources were present and readable in the native image, but Camel's classpath package scan path still diverged from JVM behavior. In native image, `ClassLoader.getResources("META-INF/services/org/apache/camel/health-check/")` exposes the root as a `resource:/...` URL. Camel's `DefaultPackageScanResourceResolver` walks jar and directory URLs, but it does not enumerate that native-image `resource:` URL as a classpath directory. As a result, `DefaultHealthChecksLoader` sees zero scan results even though individual descriptor files can be included by metadata.

Because this is a Camel/native-image resource scanning limitation rather than missing reachability metadata, I removed the generated test `defaultLoaderDiscoversClasspathHealthChecksFromCamelServiceDescriptors()` and its now-unused imports. I did not modify metadata files.

## Preserved generated support

The remaining generated tests should be preserved because they exercise supported native-image behavior in `camel-health` without relying on classpath directory enumeration. They cover:

- `ContextHealthCheck` status reporting and invocation counters.
- `AbstractHealthCheck` state handling, disabled checks, and custom result strategies.
- `DefaultHealthCheckRegistry` direct registration, lookup, repository resolution, and exclusion rules.
- `ProducersHealthCheckRepository`, `ConsumersHealthCheckRepository`, and `HealthCheckRegistryRepository` behavior.

After removing only the unsupported loader scan test, `./gradlew test -Pcoordinates=org.apache.camel:camel-health:4.20.0 --stacktrace` passes with 7 native tests successful and 0 failures.
