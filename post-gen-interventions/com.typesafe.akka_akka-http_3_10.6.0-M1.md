# Post-generation intervention report

Library: com.typesafe.akka:akka-http_3:10.6.0-M1

Stage: `metadata_fix_failed`

## Summary

The native test executable was generated successfully, but all five generated `AkkaHttp3Test` methods failed at runtime while constructing the shared Akka `ActorSystem`. The Gradle excerpt shows the same failure for every test method:

- `basicAuthenticationDirectiveAcceptsCredentialsAndRejectsUnauthorizedRequests`
- `boundServerAndClientExchangeStrictHttpResponses`
- `modelBuildersUriRenderingAndEntityMarshallersRoundTrip`
- `routesHandleParametersEntitiesHeadersAndValidationRejections`
- `multipartFormDataRouteParsesFieldsAndFileMetadata`

The reported failure was `com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'akka'`, reached from `akka.actor.ActorSystem$Settings$.amendSlf4jConfig` during `ActorSystemImpl` initialization.

## Root cause

This is metadata-related, not a generated-test bug. Akka HTTP relies on Akka's Typesafe Config defaults and several Akka classes named from configuration strings. In native image those resources/classes must be made reachable explicitly.

The Codex metadata-fix log confirms this by showing a cascade of native-only reachability failures after the initial missing Akka configuration problem:

- Akka config resources such as `reference.conf`, `version.conf`, and `akka-http-version.conf` were needed to provide the `akka` configuration tree.
- After the resource issue was partially addressed, the native run progressed to reflective class-loading failures such as `akka.event.DefaultLoggingFilter`, `akka.actor.LightArrayRevolverScheduler`, `akka.routing.ConsistentHashingPool`, `akka.actor.Props$EmptyActor`, and `akka.dispatch.BoundedControlAwareMessageQueueSemantics`.

All five test failures share this same `ActorSystem` startup path, so none of the individual test bodies is the root cause.

## Intervention decision

No generated test was removed. The remaining failure is missing/partial reachability metadata for Akka's configuration resources and reflectively loaded Akka runtime classes. Removing the tests would hide valid Akka HTTP coverage rather than address the native-image reachability gap.

## Why preserve the generated support

The generated tests exercise meaningful Akka HTTP functionality: model/entity marshalling, route directives, multipart unmarshalling, basic authentication, and a real bound server/client exchange. The failures occur before that functionality runs, during common Akka `ActorSystem` initialization. Once the missing resource and reflection metadata is completed, the same tests should provide useful coverage for the generated library support.
