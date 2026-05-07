# Post-generation intervention report

Library: com.typesafe.akka:akka-slf4j_3:2.6.21
Stage: metadata_fix_failed

## Summary

The remaining failures are metadata-related. The generated native image builds, but four ActorSystem-backed tests fail at native runtime while Akka initializes classes loaded through configuration and Scala 3 lazy-val support. I did not remove any generated tests or test-only files.

## Root cause by failing test

- `slf4jLoggerPublishesAkkaLogEventsWithMarkerAndMdc()` fails during `actorSystem(...)` startup. The Gradle excerpt shows `ExceptionInInitializerError` caused by `java.lang.NoSuchFieldException: 0bitmap$1` from `scala.runtime.LazyVals$.getOffset(...)` while initializing `akka.actor.ActorSystemImpl`. This is missing reflection metadata for Scala 3 synthetic lazy-val bitmap fields.
- `markerLoggingAdapterEmitsFormattedMessagesThroughSlf4jLogger()` fails for the same startup path after the first class initialization failure, reported as `NoClassDefFoundError: Could not initialize class akka.actor.ActorSystemImpl` in the excerpt.
- `loggingFilterCombinesAkkaEventStreamLevelWithSlf4jLevelAndMarkers()` fails for the same ActorSystem startup metadata gap, again reported as `NoClassDefFoundError` after `ActorSystemImpl` initialization failed.
- `slf4jLoggerPreservesThrowableCausesForErrorEvents()` fails for the same ActorSystem startup metadata gap, again reported as `NoClassDefFoundError` after `ActorSystemImpl` initialization failed.

The Codex metadata-fix log confirms this was not a test bug or unsupported native-image feature. Codex iteratively uncovered metadata gaps: Akka config resources such as `reference.conf`/`version.conf`, Scala 3 `0bitmap$*` lazy-val fields in transitive `akka-actor_3` classes such as `akka.actor.ActorSystemImpl`, `akka.event.EventStream`, and `akka.actor.Address`, and finally reflective configuration loading of `akka.event.slf4j.Slf4jLoggingFilter`. The current `metadata/com.typesafe.akka/akka-slf4j_3/2.6.21/reachability-metadata.json` is still empty, so the final Codex-observed blocker remains missing reflection metadata for `akka.event.slf4j.Slf4jLoggingFilter` and likely related config-loaded SLF4J logging classes such as `akka.event.slf4j.Slf4jLogger`.

## Why generated support should be preserved

The passing tests already exercise direct `akka-slf4j_3` APIs for logger creation, `SLF4JLogging`, and marker wrapping. The failing tests are valid coverage for the same library's integration with Akka's event stream, MDC propagation, throwable handling, and marker-aware logging adapters. They fail only when native-image metadata is incomplete for Akka's reflective configuration and Scala 3 lazy-val internals. Removing them would hide real reachability requirements instead of isolating a non-metadata limitation.
