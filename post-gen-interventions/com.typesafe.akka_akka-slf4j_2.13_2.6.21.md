# Post-generation intervention report

Library: com.typesafe.akka:akka-slf4j_2.13:2.6.21
Stage: metadata_fix_failed

## Summary

The native-image build completed, but `nativeTest` failed for four generated tests that create an Akka `ActorSystem`:

- `markerLoggingAdapterPublishesEventsToSlf4jLogger()`
- `diagnosticMarkerLoggingAdapterPublishesMdcAndMarkerProperties()`
- `slf4jLoggerAcknowledgesInitialization()`
- `loggingFilterCombinesAkkaLogLevelWithSlf4jLoggerLevels()`

All four failures have the same root cause in the provided Gradle output: Akka configuration initialization cannot find the required `akka.version` setting:

```text
com.typesafe.config.ConfigException$Missing: String: 2: No configuration setting found for key 'akka.version'
```

The requested Codex metadata-fix log path, `logs/com.typesafe.akka:akka-slf4j_2.13:2.6.21/metadata-fix/codex.log`, is not present in this worktree, so the diagnosis is based on the Gradle failure output and the generated test/metadata state.

## Root cause

This is metadata-related and the generated tests should not be removed.

The failing tests call `ActorSystem(systemName, config)`. The test config uses `ConfigFactory.parseString(...).withFallback(ConfigFactory.load())`, so on the JVM Akka should obtain its defaults and version from Akka classpath resources, including `reference.conf` and `version.conf`. In the native executable, that fallback configuration is incomplete, causing Akka's `ActorSystem.Settings` constructor to fail while reading `akka.version`.

The remaining missing metadata is resource metadata for Akka configuration resources needed at native-image runtime, most directly `version.conf` containing `akka.version = "2.6.21"`; Akka's `reference.conf` defaults are also part of the same resource-loading path. Codex appears to have only partially addressed the native-image requirements: the metadata includes reflection entries for `akka.event.slf4j.Slf4jLoggingFilter` and `akka.event.slf4j.Slf4jLogger`, but the native run still cannot load all required Akka configuration resources.

## Per-test classification

- `markerLoggingAdapterPublishesEventsToSlf4jLogger()` — metadata-related; fails during `ActorSystem` startup before the logging adapter behavior is exercised because `akka.version` is unavailable in native image resources.
- `diagnosticMarkerLoggingAdapterPublishesMdcAndMarkerProperties()` — metadata-related; same `ActorSystem` initialization failure before the diagnostic marker/MDC behavior is exercised.
- `slf4jLoggerAcknowledgesInitialization()` — metadata-related; same missing Akka configuration resource before the `Slf4jLogger` actor initialization can run.
- `loggingFilterCombinesAkkaLogLevelWithSlf4jLoggerLevels()` — metadata-related; same missing Akka configuration resource before the `Slf4jLoggingFilter` behavior can be tested.

## Preservation decision

No generated tests or generated test-only files were removed. The failures are not caused by an unsupported platform feature or a bad test assertion; they expose a real native-image metadata gap in Akka's runtime configuration loading. The remaining generated support should be preserved because three tests already pass in native image and the four failing tests cover legitimate `akka-slf4j` functionality around actor-system logging, SLF4J logger startup, marker logging, MDC propagation, and logging filters. Keeping these tests preserves coverage for the metadata that Codex already generated and documents the next missing metadata needed for the library to work under native image.
