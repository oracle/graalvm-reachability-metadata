# Post-generation intervention report

Library: com.typesafe.play:play-ws-standalone_2.13:2.2.14
Stage: metadata_fix_failed

## Summary

The native-image build completed, but `nativeTest` failed at runtime. Five generated tests failed:

- `standaloneClientExecutesLocalGetAndReadsResponseMetadata()`
- `followsRedirectResponsesWhenConfigured()`
- `defaultBodyWritableEncodesFormDataForPutRequests()`
- `requestFilterAndImmutableBuilderSettingsApplyToExecutedPost()`
- `basicAuthenticationRespondsToServerChallenge()`

All five failures occur while constructing the Play/AHC WS client fixture, before the test-specific HTTP assertions run. Akka `ActorSystem` startup fails with:

```text
com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'akka'
```

## Root cause classification

This is metadata-related. Akka is starting from a native image that does not have the required Akka configuration resources active, so `ActorSystem$Settings.amendSlf4jConfig` cannot read the `akka.*` settings normally supplied by Akka `reference.conf` / related config resources.

The Codex metadata-fix log shows that Codex was following the correct metadata-repair path but did not converge. It repeatedly exposed additional transitive Play WS runtime metadata gaps after each patch, including Akka config/resource activation, Akka reflective class loads, stream serialization/materializer classes, shaded AHC `ahc-default.properties`, and shaded Netty/JCTools reflective field/method access. The remaining failure is therefore not a test bug or unsupported native-image behavior; it is incomplete transitive metadata for the Play WS standalone client stack.

## Action taken

No generated tests were removed. No metadata files were modified by this intervention.

## Why the generated support should be preserved

The generated tests exercise real, supported Play WS standalone behavior: creating a `StandaloneAhcWSClient`, executing local HTTP requests, reading response metadata and bodies, handling basic authentication, following redirects, applying request filters, and encoding form/body writables. One non-client helper/API test already passes, and the failing tests are blocked at common client bootstrap by missing metadata rather than by invalid assertions. Preserving these tests keeps useful coverage for the metadata that still needs to be completed.
