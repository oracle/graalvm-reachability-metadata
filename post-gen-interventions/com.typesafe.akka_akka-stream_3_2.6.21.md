# Post-generation intervention report

Library: com.typesafe.akka:akka-stream_3:2.6.21
Stage: metadata_fix_failed

## Summary

The generated Akka Streams test suite fails in the native-image test executable during `ActorSystem` startup. All 10 generated tests use `withMaterializer`, which creates an `akka.actor.ActorSystem`; the failure happens before the individual stream operations run, so the failures share one root cause rather than separate test bugs.

The Gradle output reports `NoClassDefFoundError: Could not initialize class akka.actor.ActorSystemImpl` for the generated tests. The native test result and Codex metadata-fix log show this is caused by missing native-image reachability metadata for Akka's config-driven and Scala 3 lazy-val bootstrap path.

## Root cause classification

This is metadata-related. No generated tests were removed.

Observed missing metadata during Codex's repair attempts included:

- Scala 3 lazy-val bitmap fields such as `0bitmap$1` on Akka startup classes including `akka.actor.ActorSystemImpl`, `akka.actor.Address`, `akka.event.EventStream`, `akka.actor.ActorSelection`, `akka.actor.CoordinatedShutdown`, and `akka.actor.LocalActorRefProvider`.
- Reflectively loaded Akka classes instantiated from `reference.conf`, including `akka.event.DefaultLoggingFilter` and `akka.event.Logging$DefaultLogger`.
- The remaining native test report still shows `java.lang.ClassNotFoundException: akka.actor.LightArrayRevolverScheduler` from `akka.actor.ReflectiveDynamicAccess.createInstanceFor` via `ActorSystemImpl.createScheduler`, meaning the configured scheduler is still not available to the native image at runtime.

Codex made partial progress by identifying and adding several Akka bootstrap registrations, but it did not complete a successful reproduce-fix-verify loop. The log ends after Codex identified the scheduler gap and started another Gradle run, without a verified passing native test run. Therefore the remaining failures should be treated as unfinished reachability metadata work, not as unsupported Akka Stream behavior or invalid tests.

## Why generated support should be preserved

The generated tests exercise real `akka-stream_3` APIs: finite `Source`/`Flow`/`Sink` pipelines, graph DSL composition, queue materialization, supervision, framing, substreams, `unfoldResource`, recovery, fold sinks, and restart sources. These are meaningful coverage areas for Akka Streams native-image support.

Because every failing test stops at common `ActorSystem` bootstrap before reaching its stream-specific assertions, removing individual tests would hide a shared metadata gap and reduce valid coverage. The tests should remain so that future metadata fixes can verify Akka actor-system and stream materialization support end to end.
