# Post-generation intervention report

Library: com.typesafe.akka:akka-stream_2.13:2.6.21
Stage: metadata_fix_failed

## Summary

The native test executable failed all 12 generated `Akka_stream_2_13Test` methods during `ActorSystem` startup. The Gradle excerpt reports `com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'akka'`, and the native JUnit XML also shows earlier `ClassNotFoundException` failures for Akka serializer classes such as `akka.serialization.DisabledJavaSerializer`.

The requested Codex metadata-fix log was not present at `logs/com.typesafe.akka:akka-stream_2.13:2.6.21/metadata-fix/codex.log` in this worktree, so the conclusion is based on the supplied Gradle failure excerpt, the native test result XML, the generated test source, and the generated metadata state.

## Root cause

All failing tests share the same root cause: they call the generated `withStream` helper, which constructs an `akka.actor.ActorSystem`. In the native image, Akka cannot load the configuration and reflective classes it needs while creating that actor system.

This is metadata-related, not unsupported native-image behavior from the tests. The failures happen before the stream operations under test execute, and the JVM test run passed all 12 methods. The remaining missing support appears to include Akka/Typesafe Config resource metadata for Akka `reference.conf`/configuration resources, because the generated native resource config has no includes and Akka reports the top-level `akka` config key as missing. The native XML also indicates reflective class-loading metadata was incomplete for serializer classes such as `akka.serialization.DisabledJavaSerializer`; Codex partially addressed reflection in `reachability-metadata.json`, but the native run still failed before `ActorSystem` could start cleanly.

## Test disposition

No generated tests were removed. The failures are caused by incomplete reachability metadata needed by Akka startup rather than by a native-image limitation, runtime bytecode generation, self-attach, instrumentation, or Byte Buddy-style mocking.

Affected generated tests:

- `runTypedSourceFlowAndSinkPipeline()`
- `materializeRunnableGraphBuiltWithGraphDsl()`
- `frameByteStringsDelimitedAcrossChunks()`
- `roundTripGzipCompressedByteStrings()`
- `readInputStreamThroughStreamConverters()`
- `writeAndReadFilesWithFileIO()`
- `offerElementsToBackpressuredSourceQueue()`
- `publishAndConsumeThroughMergeHubAndBroadcastHub()`
- `resumeAfterElementFailureWithSupervisionAttribute()`
- `stopOpenStreamWithUniqueKillSwitch()`
- `mergeFoldedSubstreamsByKey()`
- `combineMaterializedValuesWithKeepBoth()`

## Why the generated support should be preserved

The generated test suite exercises real Akka Streams behavior across sources, flows, sinks, graph DSL materialization, framing, compression, stream converters, file I/O, queues, hubs, supervision, kill switches, substreams, and materialized values. These tests passed on the JVM and fail uniformly only when native-image metadata is missing during `ActorSystem` initialization, so they remain useful coverage for validating future metadata fixes for `com.typesafe.akka:akka-stream_2.13:2.6.21`.
