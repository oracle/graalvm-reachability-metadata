# Post-generation intervention report

Library: com.typesafe.akka:akka-http_2.13:10.5.3
Stage: metadata_fix_failed

## Summary

The native test binary builds, but 7 of the 9 generated tests fail at native runtime. All failures have the same root cause: the tests that create an `ActorSystem` cannot load Akka's default Typesafe Config and throw:

```text
com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'akka'
```

The two generated tests that only exercise Akka HTTP model types pass. The failing tests are:

- `encodesAndDecodesGzipPayloadsAndMessages()`
- `appliesRoutingDirectivesToRequestsWithoutBindingASocket()`
- `marshalsAndUnmarshalsTextAndFormEntities()`
- `exchangesStrictTextMessagesOverWebSockets()`
- `consumesChunkedStreamingEntities()`
- `sealsRouteRejectionsIntoHttpResponses()`
- `servesAndConsumesLoopbackRequestsWithTheHighLevelApi()`

## Root cause classification

This is metadata-related, so I did not remove the generated tests.

The failures occur before the generated test assertions run, while `ActorSystem(name)` initializes `akka.actor.ActorSystemImpl`. Akka expects its `reference.conf` resources to provide the root `akka` configuration. In the native image run, Typesafe Config reports that `reference.conf` cannot be found through `ClassLoader.getResources("reference.conf")`, leaving only system properties and causing the missing `akka` key.

The Codex metadata-fix log shows that Codex correctly identified this as a classpath resource/config discovery problem rather than a reflection failure. It tried resource metadata for `*.conf`, `application.conf`, `reference.conf`, `version.conf`, and `akka-http-version.conf`, and also experimented with a test `reference.conf`. However, the runtime trace still reported no `reference.conf` resources visible to the native class loader. Codex also generated an embedded-resource index during investigation and observed that the files were present in the image, but it did not find the metadata shape needed to make Typesafe Config's class-loader enumeration see them.

## Metadata still missing

The remaining missing support is resource metadata that makes Akka/Typesafe Config default configuration resources discoverable via `ClassLoader.getResources("reference.conf")` in native image. In particular, Akka needs its bundled `reference.conf` defaults, and possibly associated top-level config resources such as `version.conf` and `akka-http-version.conf`, to be visible through the same class-loader enumeration path used by `com.typesafe.config.impl.Parseable$ParseableResources`.

No metadata files were modified as part of this intervention.

## Why the generated support should be preserved

The generated test suite is valid and useful: it covers Akka HTTP model construction/parsing, entity marshalling and unmarshalling, gzip coding, chunked streams, routing directives/rejections, WebSockets, and loopback high-level HTTP requests. The current failure is shared infrastructure metadata for Akka configuration loading, not a defect in those generated test cases. Preserving the tests keeps coverage for the affected native-image behavior and gives the next metadata-fix pass a concrete reproduction for the missing Typesafe Config resource-discovery support.
