# Post-generation intervention report

Library: com.typesafe.akka:akka-stream_3:2.6.21

Stage: metadata_fix_failed

## Summary

The generated Akka Streams test suite still fails in native execution, but the failures are metadata-related. No generated tests or test-only support files were removed, and no metadata files were modified during this intervention.

All eight reported test methods fail while creating the shared `ActorSystem` in `withStreamSystem`, before their individual stream assertions run:

- `writesAndReadsByteStringsWithFileIO`
- `resumesStreamAfterSupervisedElementFailure`
- `transformsAndReducesFiniteSource`
- `foldsGroupedSubstreamsIndependently`
- `materializesSourceQueueWithBackpressure`
- `framesDelimitedByteStrings`
- `completesStreamsThroughSharedKillSwitchAfterShutdown`
- `combinesBranchesWithGraphDsl`

The first failure initializes `akka.actor.ActorSystemImpl` and reaches Scala lazy-val support:

```text
Caused by: java.lang.NoSuchFieldException: 0bitmap$1
  java.lang.Class.getDeclaredField(...)
  scala.runtime.LazyVals$.getOffset(LazyVals.scala:156)
  akka.actor.ActorSystemImpl.<clinit>(ActorSystem.scala:806)
```

The remaining tests then fail with `NoClassDefFoundError: Could not initialize class akka.actor.ActorSystemImpl` because that class initialization has already failed.

## Root cause classification

This is a reachability metadata failure, not a test bug or unsupported Akka Streams feature. The native image needs reflective field metadata for Scala/Akka lazy-val bitmap state used during `ActorSystemImpl` initialization, specifically the synthetic `0bitmap$1` field on `akka.actor.ActorSystemImpl`. Without effective field metadata, `scala.runtime.LazyVals$` cannot resolve the field by reflection in the native executable.

The Codex metadata-fix log shows a partial metadata repair loop. It identified and added several earlier Akka bootstrap edges, including reflective access for actor-system infrastructure, routers, actor providers, mailbox requirement interfaces, mailbox implementations, and resources such as `reference.conf`. The log then ends after adding mailbox-related entries and starting another Gradle verification run, without a completed passing run. Therefore Codex did not converge on a verified complete metadata set for this coordinate.

## Why the generated support should be preserved

The generated tests exercise meaningful Akka Streams behavior: finite source transformations, GraphDSL fan-out/zip, byte-string framing, FileIO, grouped substreams, source queues with backpressure, supervision, and kill-switch shutdown. The observed failure happens before those scenarios execute, at Akka actor-system bootstrap in the native image. Preserving the tests keeps useful coverage for the generated support and provides the necessary reproducer for completing the missing metadata rather than hiding a metadata gap by deleting valid tests.
