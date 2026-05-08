# Post-generation intervention report

Library: org.playframework:play-streams_3:3.0.0
Stage: metadata_fix_failed

## Summary

The generated `Play_streams_3Test` native-image run failed before any Play Streams assertion could execute successfully. All 10 generated tests construct an Apache Pekko `ActorSystem`, and native execution failed during Pekko bootstrap with:

- `com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'pekko'`
- stack frames in `ActorSystem$Settings$.amendSlf4jConfig` and `ActorSystemImpl.<init>`

The JVM test report shows the same generated tests pass on the JVM, so this is not a test-only behavioral bug.

## Root cause by failing test

Each failing generated test has the same metadata-related root cause: Pekko's configuration resources are not fully available in the native image, so Typesafe Config cannot load the `pekko` defaults from `reference.conf` during `ActorSystem` startup.

Affected tests:

- `accumulatorSourcesDoneValuesAndJavaAdaptersRoundTrip()`
- `gzipFlowEmitsValidGzipPayload()`
- `pekkoStreamsBypassesSelectedElementsAndPassesThroughIgnoreHelpers()`
- `pekkoStreamsOnlyFirstCanFinishMergeWaitsForPrimaryInput()`
- `actorFlowPublishesRepliesFromBackingActor()`
- `accumulatorFlattensFutureAccumulatorsAndRecoversFailures()`
- `probesWrapProcessorsAndSubscriptionsWithoutChangingSignals()`
- `strictAccumulatorUsesStrictInputsAndSinkFallback()`
- `accumulatorRunsPekkoSinksAndTransformsResults()`
- `probesWrapFlowsAndPublishersWithoutChangingElements()`

The Codex metadata-fix log shows Codex made partial progress through earlier metadata failures, including missing reflection for `org.apache.pekko.event.DefaultLoggingFilter` and `org.apache.pekko.actor.LightArrayRevolverScheduler`. The Gradle daemon logs also exposed a resource-registration issue for `application.conf`, followed by the remaining `pekko` configuration failure. Codex did not complete a verified metadata set that makes Pekko's required config resources visible to the native image.

## Intervention decision

No generated tests were removed. The observed failure is metadata-related, not a native-image limitation, unsupported platform feature, or invalid generated test.

## Why the generated support should be preserved

The generated support should remain because it exercises meaningful public Play Streams functionality: Scala and Java `Accumulator` APIs, `GzipFlow`, `PekkoStreams` helpers, `ActorFlow`, and `Probes`. These tests pass on the JVM and fail natively only because shared Pekko bootstrap metadata is still incomplete. Preserving the tests keeps useful coverage for the library and provides a clear target for a future metadata-only fix without losing validated generated support.
