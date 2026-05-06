# Post-generation intervention report

Library: com.typesafe.akka:akka-http_2.13:10.5.3
Stage: metadata_fix_failed

## Summary

The generated Akka HTTP test support should be preserved. The native test failure is metadata-related, not a generated-test bug or an unsupported native-image feature.

The supplied Gradle output shows the native image builds successfully, but the JUnit native executable fails while initializing `Akka_http_2_13Test` because `ActorSystem("akka-http-reachability-test")` cannot load Akka's default configuration:

```text
com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'akka'
```

This indicates that the Akka `reference.conf` resources needed by Typesafe Config are still not available to the native executable. Akka expects its `reference.conf` entries to provide the root `akka` configuration during `ActorSystem` startup.

The Codex metadata-fix log also shows that this library exposes a sequence of native metadata misses during Akka bootstrap. Codex fixed or attempted several reflection registrations, but the run did not converge. The log reaches additional metadata-related failures such as reflective construction of Akka router/provider classes and `akka.actor.Props$EmptyActor` constructor discovery.

## Root cause of failures

- `Akka_http_2_13Test` class/container initialization fails before the individual test methods run because constructing the shared `ActorSystem` requires Akka configuration resources.
- The immediate Gradle excerpt failure is missing resource metadata for Akka configuration, especially `reference.conf` resources that contain the `akka` root configuration.
- The Codex log confirms the failure is part of an incomplete metadata-fix loop rather than a bad test: after each metadata fix, native execution progressed to the next Akka bootstrap metadata requirement.

No generated tests were removed because the failures are metadata-related.

## Why the generated support should be preserved

The generated test exercises real `akka-http_2.13` functionality: URI parsing, HTTP entity marshalling/unmarshalling, multipart form data, server-sent events, header parsing, and a local Akka HTTP route/client interaction. These are meaningful coverage areas for reachability metadata.

The JVM test path compiled and ran far enough for native-image to build the executable; the remaining failures occur only under native execution when Akka runtime configuration and reflective bootstrap paths are needed. Preserving the generated support keeps valuable coverage for the metadata that still needs to be completed.
