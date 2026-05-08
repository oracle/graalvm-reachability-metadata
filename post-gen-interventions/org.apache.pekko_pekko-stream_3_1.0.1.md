# Post-generation intervention report

Library: org.apache.pekko:pekko-stream_3:1.0.1
Stage: metadata_fix_failed

## Summary

The generated native tests all failed during `ActorSystem` startup, before the individual Pekko Stream operations could run. The reported native-image/JUnit failures are metadata-related, not test bugs or unsupported platform features, so no generated tests were removed.

All 9 failures share the same immediate exception:

`com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'pekko'`

The affected generated tests are:

- `framesByteStringDataFromFileIO()`
- `aggregatesSubstreamsAndMergesThem()`
- `composesFanOutGraphWithBroadcastAndZip()`
- `compressesAndDecompressesByteStringStream()`
- `materializesQueueAndProcessesBackpressuredElements()`
- `shutsDownStreamWithKillSwitch()`
- `runsFiniteSourceThroughFlowAndSink()`
- `recoversFromStreamFailure()`
- `runsCustomGraphStage()`

## Root cause

This is a missing reachability metadata/resource-loading problem. The Codex metadata-fix log shows a direct native executable run with `-Dconfig.trace=loads`; Typesafe Config reported that the native-image class loader had no resources named `reference.conf`:

`Loading config from class loader ... but there were no resources called reference.conf`

Pekko's default `reference.conf` files define the top-level `pekko` configuration section. Without those resources being enumerable through Typesafe Config in the native image, `ActorSystem$Settings$.amendSlf4jConfig` reads an effectively empty config and every test fails immediately with the missing `pekko` key.

The log also shows that Codex only partially advanced the metadata fix. It first tried Pekko-local config resource registrations, then identified the resource enumeration as belonging to `com.typesafe:config`, and after moving past the config-resource failure it encountered additional Pekko dynamic-access reflection gaps such as `DefaultLoggingFilter`, `LightArrayRevolverScheduler`, `LocalActorRefProvider`, `ConsistentHashingPool`, and `RoundRobinPool`. The log ends while another verification run is in progress, so the workflow did not complete a clean `./gradlew test -Pcoordinates=org.apache.pekko:pekko-stream_3:1.0.1` pass.

## Intervention decision

No tests were deleted. The failures are metadata-related because they occur in native-image resource lookup and Pekko reflective bootstrap paths. They are not caused by a generated test exercising an invalid API, relying on an unsupported native-image platform feature, or asserting incorrect behavior.

## Why the remaining generated support should be preserved

The generated test suite covers meaningful Pekko Stream behavior: finite sources and sinks, backpressured queues, graph DSL fan-out/fan-in, substreams, file IO/framing, compression, recovery, custom graph stages, and kill switches. All of those tests fail at the same shared `ActorSystem` bootstrap point, before their actual stream assertions execute. Once the missing metadata for Typesafe Config resource enumeration and Pekko's dynamic startup classes is completed, these tests should provide useful coverage for the library's native-image support rather than masking a non-metadata failure.
