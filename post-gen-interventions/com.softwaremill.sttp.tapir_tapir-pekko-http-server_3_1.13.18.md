# Post-generation intervention report

Library: com.softwaremill.sttp.tapir:tapir-pekko-http-server_3:1.13.18
Stage: metadata_fix_failed

## Summary

The generated `Tapir_pekko_http_server_3Test` native-image run failed all seven generated tests during Pekko `ActorSystem` startup, before any Tapir endpoint assertions ran. The Gradle failure excerpt shows each test failing with:

```text
com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'pekko'
```

The Codex metadata-fix log shows an incomplete iterative metadata repair. Codex fixed earlier Pekko startup metadata gaps, including reflective access for scheduler/provider/router classes, but the workflow did not reach a passing native test run.

## Root cause classification

This is metadata-related, so no generated tests were removed.

The remaining failure is caused by missing or ineffective resource metadata for Pekko/Typesafe Config configuration resources. Pekko expects its `reference.conf` configuration to be present in the native image so the root `pekko` configuration namespace can be loaded. In the failing native executable, Typesafe Config only sees system properties and cannot find the `pekko` key, which means the required Pekko configuration resource metadata has not been completely resolved.

Codex could not resolve it because the metadata-fix loop was still progressing through Pekko native-image startup requirements. The log shows successive metadata-related blockers (`sun.misc.Unsafe`, `LocalActorRefProvider`, `ConsistentHashingPool`, and `RoundRobinPool`) and ends after another native-image cycle was started, before the later `ConfigException$Missing` resource-metadata failure was fixed.

## Failing generated tests

All failures share the same root cause at `ActorSystem` initialization:

- `serialisesServerSentEventsWithPekkoStreams()`
- `appliesCustomRequestInterceptorBeforeEndpointLogic()`
- `streamsBinaryResponseBodyWithPekkoStreams()`
- `handlesBinaryRequestAndResponseBodies()`
- `routesTypedPathQueryHeadersAndErrorOutputs()`
- `readsRequestBodyAndExtractsPekkoRequestMetadata()`
- `upgradesEndpointToWebSocketAndStreamsTextFrames()`

## Why the generated support should be preserved

The generated tests exercise real public Tapir Pekko HTTP server behavior: typed routing, request extraction, binary bodies, Pekko stream responses, server-sent events, WebSocket support, and custom request interceptors. The failure occurs before that behavior is reached and points to incomplete Pekko/Typesafe Config resource metadata rather than to invalid tests or an unsupported test scenario. Keeping the generated support preserves useful coverage for this library once the remaining Pekko configuration resource metadata is completed.
