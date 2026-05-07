# Post-generation intervention report

Library: com.typesafe.play:play-streams_3:2.9.7
Stage: metadata_fix_failed

## Summary

The native test run failed after image build at runtime. Nine of the ten generated tests fail when they create an Akka `ActorSystem` through `Play_streams_3Test.withActorSystem`; the only passing test is the processor-probe test that does not start Akka.

This is metadata-related, not a generated-test bug or unsupported native-image platform feature. The failures are all on Akka bootstrap paths reached by Play Streams APIs: `ActorSystemImpl` reflectively loads config-named Akka classes and then initializes the scheduler. The Codex metadata-fix log shows successive missing metadata discoveries for Akka startup classes, and the latest verified runtime failure is the scheduler bootstrap failing because `akka.util.Unsafe` cannot reflectively obtain `sun.misc.Unsafe`.

## Root cause by failed test

| Test | Root cause |
| --- | --- |
| `scalaAccumulatorTransformsInputsAndResults()` | Metadata-related: fails in `ActorSystem(...)` while `ActorSystemImpl` creates the Akka scheduler. |
| `scalaAccumulatorRecoversStrictlyAndForStreamFallback()` | Metadata-related: same `ActorSystemImpl` / scheduler bootstrap failure. |
| `accumulatorCanExposeSourcesFlattenFuturesAndRoundTripToJavaApi()` | Metadata-related: `ExceptionInInitializerError` in `akka.dispatch.AbstractNodeQueue`, caused by `akka.util.Unsafe` failing with `Can't find instance of sun.misc.Unsafe`. |
| `akkaStreamsBypassRoutesSelectedElementsAroundInnerFlow()` | Metadata-related: same `ActorSystemImpl` / scheduler bootstrap failure. |
| `onlyFirstCanFinishMergeIgnoresSecondaryInputCompletion()` | Metadata-related: same `ActorSystemImpl` / scheduler bootstrap failure. |
| `gzipFlowProducesDataThatAkkaCompressionCanInflate()` | Metadata-related: same `ActorSystemImpl` / scheduler bootstrap failure. |
| `actorFlowDelegatesStreamElementsToActor()` | Metadata-related: same `ActorSystemImpl` / scheduler bootstrap failure. |
| `probesDelegateReactiveStreamsSignalsAndFlowElements()` | Metadata-related: same `ActorSystemImpl` / scheduler bootstrap failure. |
| `ignoreAfterCancellationKeepsUpstreamDrainedForRemainingConsumers()` | Metadata-related: same `ActorSystemImpl` / scheduler bootstrap failure. |

## Metadata still unresolved

Codex partially advanced the metadata repair loop. The log shows it added or investigated config/resource and reflection metadata for Akka startup, including `reference.conf` / `version.conf`, `akka.event.DefaultLoggingFilter`, `akka.event.Logging$DefaultLogger`, `akka.actor.LightArrayRevolverScheduler`, and `akka.actor.LocalActorRefProvider`.

The latest verified failure after those fixes was still metadata-related:

- `akka.actor.LightArrayRevolverScheduler$TaskQueue` could not initialize.
- The first causal failure was `akka.util.Unsafe.<clinit>` throwing `IllegalStateException: Can't find instance of sun.misc.Unsafe`.
- Codex identified the next required reflective field accesses as `sun.misc.Unsafe.theUnsafe`, `java.lang.String.value`, and `akka.dispatch.AbstractNodeQueue._tailDoNotCallMeDirectly`.

Codex could not complete the repair because the log ends while `./gradlew nativeTestCompile -Pcoordinates=com.typesafe.play:play-streams_3:2.9.7` was still rebuilding after those bootstrap-field registrations were added. There is no completed verification run after that point, so the unsafe/bootstrap metadata remains unverified and additional follow-on Akka scheduler or actor-system registrations may still be required.

## Intervention decision

No generated tests were removed. The failures exercise real Play Streams support through `Accumulator`, `AkkaStreams`, `GzipFlow`, `ActorFlow`, and `Probes`, and they all fail before the test assertions because shared Akka `ActorSystem` startup metadata is incomplete. Removing these tests would discard meaningful generated coverage for the target library rather than removing a bad test.

The remaining generated support should be preserved because it covers the primary Play Streams APIs and has already exposed concrete missing reachability metadata in Akka/Play runtime initialization. Once the remaining metadata loop is completed and verified, the same tests should validate useful native-image support for `com.typesafe.play:play-streams_3:2.9.7`.
