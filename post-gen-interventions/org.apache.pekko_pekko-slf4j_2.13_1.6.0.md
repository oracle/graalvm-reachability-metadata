# Post-generation intervention report

Library: org.apache.pekko:pekko-slf4j_2.13:1.6.0
Stage: metadata_fix_failed

## Summary

The native test executable was built successfully, but `:nativeTest` failed at runtime. Three generated tests failed while creating a Pekko `ActorSystem`:

- `slf4jLoggerActorInitializesAndHandlesAllLogEventShapes()`
- `actorSystemUsesConfiguredSlf4jLoggingFilterForLoggingAdapters()`
- `slf4jLoggingFilterCombinesEventStreamLevelWithSlf4jBackendLevel()`

The Gradle excerpt shows `ConfigException$Missing` for `pekko.logging-filter` and `pekko.version`, which means the native image did not have the Pekko `reference.conf` / `version.conf` configuration resources available when `ActorSystem` settings were initialized. That is a reachability metadata/resource-inclusion problem, not a test bug or unsupported native-image platform feature.

## Root cause by failure

- `slf4jLoggerActorInitializesAndHandlesAllLogEventShapes()` failed during `ActorSystem` startup because `pekko.logging-filter` was missing from the runtime configuration. This points to missing Pekko configuration resources in the native image.
- `actorSystemUsesConfiguredSlf4jLoggingFilterForLoggingAdapters()` failed during `ActorSystem` settings initialization because `pekko.version` was missing. This points to missing `version.conf` resource metadata.
- `slf4jLoggingFilterCombinesEventStreamLevelWithSlf4jBackendLevel()` failed for the same `pekko.logging-filter` resource lookup during `ActorSystem` startup.

The Codex metadata-fix log confirms the failures are metadata-related. Codex was iterating through native-image reachability gaps in the Pekko actor-system bootstrap path, adding resource and reflection metadata for `reference.conf`, `version.conf`, reflective actor constructors, mailboxes, dispatchers, loggers, and extension loading. The last observed blocker in the generated native test report was still metadata-related: `ClassNotFoundException` for the configured extension singleton `org.apache.pekko.serialization.SerializationExtension$` while loading Pekko extensions. Codex added an entry for that type but did not complete another successful verification run, so the metadata-fix loop remained unfinished.

## Action taken

No generated tests were removed. The failures are caused by incomplete native-image reachability metadata, not by invalid generated test behavior.

Metadata files were not modified as part of this intervention report.

## Why the generated support should be preserved

The four generated tests that do not create a full `ActorSystem` already pass in the native run and exercise useful `pekko-slf4j` API surface: logger factory delegation, string log-source categories, SLF4J marker wrapping, and lazy logger creation through `SLF4JLogging`.

The three failing tests exercise important `pekko-slf4j` behavior that requires a real Pekko `ActorSystem`: `Slf4jLoggingFilter`, configured logging adapters, and the `Slf4jLogger` actor. Their failures expose genuine missing resource/reflection metadata needed by native images. Preserving these tests keeps coverage for the actual runtime paths that metadata must support and provides a clear target for finishing the metadata repair instead of hiding the remaining reachability gaps.
