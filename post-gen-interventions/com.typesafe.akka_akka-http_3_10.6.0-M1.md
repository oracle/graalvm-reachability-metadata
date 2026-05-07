# Post-generation intervention report

Library: com.typesafe.akka:akka-http_3:10.6.0-M1
Stage: metadata_fix_failed

## Summary

The generated native test suite still fails during `:nativeTest` after the Codex metadata-fix attempt. Five tests that create an Akka `ActorSystem` fail at native runtime while `modelParsesUrisHeadersAndBuildsRequests()` passes. The remaining failure is metadata-related: Akka loads router configuration for `/IO-DNS/inet-address` and tries to resolve `akka.routing.ConsistentHashingPool` reflectively, but that class is not registered/reachable in the native image.

## Root cause by failing test

- `marshalsAndUnmarshalsMultipartFormData()` fails because Akka actor-system startup reflectively loads `akka.routing.ConsistentHashingPool` from the DNS deployment config and native-image reports `ClassNotFoundException`.
- `routesRequestsWithDirectivesAndHandlesRejections()` fails for the same metadata-related Akka actor-system startup path.
- `marshalsUnmarshalsEntitiesAndFormData()` fails for the same metadata-related Akka actor-system startup path.
- `localServerAndClientRoundTripStrictEntities()` fails for the same metadata-related Akka actor-system startup path.
- `decodesGzipRequestAndEncodesGzipResponse()` fails for the same metadata-related Akka actor-system startup path.

This is not a test bug or unsupported platform feature in the generated tests. The failure is a continuing reachability-metadata gap for Akka actor-core classes selected from Akka configuration.

## Codex metadata-fix findings

The Codex log shows the metadata-fix loop made progress through several missing pieces before stopping:

- initial config resource failures involving `reference.conf` and `akka-http-version.conf`;
- missing reflective startup classes including `akka.event.DefaultLoggingFilter`, `akka.actor.LightArrayRevolverScheduler`, and `akka.actor.LocalActorRefProvider`;
- the current remaining blocker, `akka.routing.ConsistentHashingPool`, loaded from Akka's internal DNS deployment configuration.

Codex stopped because the fix was broadening from narrow `akka-http_3` metadata into a larger `akka-actor_3` config-driven startup graph. The next missing metadata is at least reflective access/reachability for `akka.routing.ConsistentHashingPool`, and additional Akka DNS/router provider classes may be exposed after that registration is added.

## Intervention decision

No generated tests were removed. The remaining failures are metadata-related, so removing the tests would hide valid coverage of Akka HTTP routes, marshalling, server/client round-trips, and encoding/decoding paths.

## Why the generated support should be preserved

The generated support should be preserved because it exercises real Akka HTTP behavior and already contains one passing model-level native test. The failing tests expose legitimate missing reachability metadata in Akka's actor-system startup path rather than invalid generated code. Keeping the tests preserves useful coverage for a follow-up metadata fix, likely involving broader Akka actor-core metadata rather than deleting Akka HTTP coverage.
