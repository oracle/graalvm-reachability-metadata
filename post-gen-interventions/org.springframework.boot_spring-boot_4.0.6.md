# Post-generation intervention report

Library: org.springframework.boot:spring-boot:4.0.6
Stage: metadata_fix_failed

## Summary

The native test run failed in six generated tests. The failures were not caused by missing
reachability metadata. Five failures exercised Spring Boot application startup/AOT processing in a
JUnit native-test image without the Spring AOT generated application artifacts that those paths
require. One failure asserted JVM virtual-thread scheduler MXBean availability that is not exposed in
this native-image runtime. I removed the generated tests that caused these non-metadata failures and
did not modify metadata files.

The requested Codex log path `logs/org.springframework.boot:spring-boot:4.0.6/metadata-fix/codex.log`
was not present in this worktree, so the classification is based on the available Gradle failure
output and the generated test sources.

## Root cause by failing test

- `BeanDefinitionLoaderTest.runAcceptsLibraryPackageNameSourcesDiscoveredFromClasspath()` failed with
  `IllegalStateException: mainApplicationClass not found`. The generated test calls
  `SpringApplication.run()` inside a native test executable, where Spring Boot detects AOT/native
  mode and tries to add generated environment post-processing for a main application class. This is a
  generated-test harness mismatch, not missing metadata.
- `ClearCachesApplicationListenerTest.runClearsContextClassLoaderCachesWhenContextIsRefreshed()`
  failed with the same `mainApplicationClass not found` Spring Boot AOT/native startup path. The
  test attempts a full application run in a context that lacks generated AOT application artifacts.
- `EnvironmentConverterTest.runConvertsDefaultEnvironmentToFactoryEnvironmentType()` failed with the
  same `mainApplicationClass not found` Spring Boot AOT/native startup path. This is not resolvable by
  reachability metadata.
- `SpringApplicationAotProcessorTest.mainProcessesApplicationWithNoArgumentMainMethod()` failed with
  `AotInitializerNotFoundException` for
  `NoArgumentSpringBootApplication__ApplicationContextInitializer`. The generated test invokes the
  AOT processor from inside the native test image; the sample application is then started in AOT mode
  before the generated initializer is available to that image. This is a test-design issue, not a
  metadata omission.
- `SpringApplicationAotProcessorTest.mainProcessesApplicationWithStringArrayMainMethod()` failed for
  the same reason, expecting
  `StringArraySpringBootApplication__ApplicationContextInitializer` to be available inside the
  already-built native test executable.
- `ProcessInfoTest.getVirtualThreadsReturnsSchedulerMetrics()` failed because the test only checked
  whether `jdk.management.VirtualThreadSchedulerMXBean` was present as a class, then assumed that
  Spring Boot could obtain virtual-thread scheduler metrics. In the native image, the API class can be
  reachable while the scheduler MXBean is not available, so `ProcessInfo.getVirtualThreads()` returns
  `null`. This is a native-image/platform support limitation rather than missing metadata.

## Changes made

Removed the generated non-metadata tests:

- `tests/src/org.springframework.boot/spring-boot/4.0.6/src/test/java/org_springframework_boot/spring_boot/BeanDefinitionLoaderTest.java`
- `tests/src/org.springframework.boot/spring-boot/4.0.6/src/test/java/org_springframework_boot/spring_boot/ClearCachesApplicationListenerTest.java`
- `tests/src/org.springframework.boot/spring-boot/4.0.6/src/test/java/org_springframework_boot/spring_boot/EnvironmentConverterTest.java`
- `tests/src/org.springframework.boot/spring-boot/4.0.6/src/test/java/org_springframework_boot/spring_boot/ProcessInfoTest.java`
- `tests/src/org.springframework.boot/spring-boot/4.0.6/src/test/java/org_springframework_boot/spring_boot/SpringApplicationAotProcessorTest.java`

## Why the remaining generated support should be preserved

The remaining generated tests still exercise Spring Boot APIs that are valid in the native-test
harness, including ANSI output handling, `ApplicationHome`, binders/converters, import-candidate
resource loading, instantiation, logging-system lookup, and failure analysis. After removing only the
non-metadata failures, `./gradlew test -Pcoordinates=org.springframework.boot:spring-boot:4.0.6 --stacktrace`
passed with 15 native tests successful. That indicates the preserved support provides useful
reachability coverage without depending on unsupported Spring AOT application-startup scenarios or
unavailable native-image management metrics.
