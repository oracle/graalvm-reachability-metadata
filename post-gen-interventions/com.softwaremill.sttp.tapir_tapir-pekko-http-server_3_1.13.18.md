# Post-generation intervention report

Library: com.softwaremill.sttp.tapir:tapir-pekko-http-server_3:1.13.18
Stage: metadata_fix_failed

## Summary

All seven generated native tests fail during Pekko `ActorSystem` startup. The Gradle excerpt shows the first shared failure as `com.typesafe.config.ConfigException$Missing: No configuration setting found for key 'pekko'`, which points to missing Pekko configuration resources in the native image rather than a bad Tapir test scenario.

The Codex metadata-fix log confirms this is metadata-related. Codex progressed past the initial missing `pekko` configuration by adding resource and reflection support, then hit additional config-driven Pekko startup reflection edges. The final recorded rerun fails all seven tests with a shared native-image reflection/class-initialization problem:

- first failure: `java.lang.ExceptionInInitializerError` in `org.apache.pekko.actor.ActorCell$`
- root cause: `java.lang.IllegalArgumentException: no matching constructor found on class org.apache.pekko.actor.Props$EmptyActor for arguments []`
- subsequent failures: `NoClassDefFoundError: Could not initialize class org.apache.pekko.actor.ActorCell$`

Before that final failure, Codex also observed and partially addressed missing reflective access to Pekko config-selected classes such as `org.apache.pekko.actor.LocalActorRefProvider`, `org.apache.pekko.actor.DefaultSupervisorStrategy`, `org.apache.pekko.routing.ConsistentHashingPool`, and `org.apache.pekko.routing.RoundRobinPool`.

## Root cause classification

This is a metadata-related failure, not a generated-test bug or unsupported platform feature. Each generated test creates a Pekko HTTP route/server and therefore exercises the same valid Pekko `ActorSystem` startup path. Native-image execution lacks complete reachability metadata for Pekko's configuration-loaded resources and reflectively-created actor/router classes.

No generated tests were removed.

## Metadata still missing

The remaining known missing metadata is reflective constructor access for `org.apache.pekko.actor.Props$EmptyActor`, reached while `ActorCell$` initializes. The metadata-fix attempt did not finish because each rerun exposed the next Pekko startup edge in sequence; after Codex fixed the initial resource/configuration problem and several reflectively loaded classes, the final log ended on the new `Props$EmptyActor` constructor failure before it could add and verify the next metadata entry.

Additional Pekko startup metadata may still be uncovered after `Props$EmptyActor` is registered, because the test has not yet completed a successful native run.

## Why the remaining generated support should be preserved

The generated tests cover meaningful `tapir-pekko-http-server_3` behavior: route decoding, request/response bodies and headers, streaming, security logic, multiple endpoints, server-sent events, and WebSocket handling. All failures share one metadata-dependent Pekko startup path and do not indicate that these scenarios are invalid. Preserving the generated support keeps a useful regression target for completing the missing reachability metadata without discarding valid coverage.
