# Post-generation intervention report

Library: org.springframework.boot:spring-boot-integration:4.0.2
Stage: metadata_fix_failed

## Summary

The generated tests compile and the JVM test report shows all 9 tests passing, but the native test run fails. The Gradle excerpt shows every JUnit test failing during native execution because Spring Boot cannot read class metadata for `org.springframework.boot.integration.autoconfigure.IntegrationAutoConfiguration`:

```text
java.lang.IllegalStateException: Unable to read meta-data for class org.springframework.boot.integration.autoconfigure.IntegrationAutoConfiguration
Caused by: java.io.FileNotFoundException: class path resource [org/springframework/boot/integration/autoconfigure/IntegrationAutoConfiguration.class] cannot be opened because it does not exist
```

The requested Codex metadata-fix log was not present at `logs/org.springframework.boot:spring-boot-integration:4.0.2/metadata-fix/codex.log` in this checkout, so the exact Codex-side stopping point could not be inspected from that file.

## Root cause

This is metadata-related, not a bad generated test or an unsupported platform feature. `spring-boot-integration-4.0.2.jar` contains `org/springframework/boot/integration/autoconfigure/IntegrationAutoConfiguration.class`, but the native executable cannot access that `.class` file as a classpath resource when `AutoConfigurationSorter` reads annotation metadata.

The remaining missing metadata is resource metadata for the Spring Boot integration auto-configuration class metadata, at least:

- `org/springframework/boot/integration/autoconfigure/IntegrationAutoConfiguration.class`

The generated metadata appears incomplete or ineffective for this native execution path. Because the failure is missing native-image reachability/resource metadata, the generated test should not be removed.

## Test handling decision

No generated tests or generated test-only files were removed. The failures are exercising valid Spring Boot Integration behavior that works on the JVM and exposes missing native-image resource reachability.

## Why preserve the remaining generated support

The test suite covers meaningful support for `spring-boot-integration`: configuration property binding/defaults, JDBC defaults, auto-configuration behavior, poller/task scheduler setup, integration flow execution, and the integration graph endpoint. Since those checks pass on the JVM and the native failure points to missing metadata, preserving the generated support keeps useful coverage for the library and provides a concrete reproducer for the remaining metadata gap.
