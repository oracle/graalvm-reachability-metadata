# Post-generation intervention report

Library: org.apache.pekko:pekko-slf4j_3:1.0.1
Stage: metadata_fix_failed

## Summary

The native image is built successfully, but the generated native tests that start a Pekko `ActorSystem` fail at runtime. The provided Gradle output shows Pekko configuration fallback data missing from the native image (`pekko.version` and `pekko.loggers`). The Codex metadata-fix log confirms this is a metadata problem and not a bad generated test: Codex first identified missing `reference.conf` / `version.conf` resources, then repeatedly exposed additional Pekko `ReflectiveDynamicAccess` / constructor-registration gaps while walking the same ActorSystem bootstrap path.

No generated tests were removed because the failures are metadata-related.

## Failed tests and root cause

- `actorSystemRoutesPekkoLogEventsThroughConfiguredSlf4jLogger()`
  - Provided failure: `ConfigException$Missing` for `pekko.version`.
  - Root cause: missing native-image resource metadata for Pekko's transitive configuration resources, especially `reference.conf` and `version.conf`, which are normally loaded from `pekko-actor_3` on the JVM.

- `slf4jLoggerExposesMdcAttributeNamesAndUtcTimestampFormatter()`
  - Provided failure: `ConfigException$Missing` for `pekko.loggers`.
  - Root cause: same missing Pekko config-resource metadata. In the latest local native test artifact produced during Codex's loop, this test had progressed to a reflective constructor-registration failure for `org.apache.pekko.actor.LocalActorRefProvider$Guardian(SupervisorStrategy)`, which is also metadata-related.

- `slf4jLoggerRepliesToPekkoLoggerInitializationProtocol()`
  - Provided failure: `ConfigException$Missing` for `pekko.loggers`.
  - Root cause: same missing Pekko config-resource metadata. The Codex log shows subsequent required metadata in this startup chain includes reflective registrations for SLF4J logging classes and actor-provider internals.

- `loggingFilterHonorsEventStreamLevelAndSlf4jMarkerOverloads()`
  - Provided failure: `ConfigException$Missing` for `pekko.loggers`.
  - Root cause: same missing Pekko config-resource metadata. The later Codex loop indicates this remains part of the same metadata issue around config-driven ActorSystem startup.

## Metadata still missing or unresolved

The initial unresolved metadata from the Gradle excerpt is resource metadata for Pekko's built-in configuration files:

- `reference.conf`
- `version.conf`

Codex partially addressed that and then uncovered further config-driven reflective metadata requirements, including registrations for Pekko logging filters/logger classes, scheduler and mailbox classes, actor-provider internals such as `LocalActorRefProvider$Guardian` / `SystemGuardian`, serializers, router/provider classes, and selected field access used by `org.apache.pekko.util.Unsafe`.

The Codex log ends while a verification run is still in progress after adding the `Guardian` / `SystemGuardian` constructor registrations. It never reached a clean passing run, so the metadata fix was incomplete. This is why the generated tests should not be interpreted as invalid or removed.

## Why preserve the generated support

The generated support exercises real public `pekko-slf4j_3` behavior: SLF4J logger creation, `SLF4JLogging`, marker wrapping, `Slf4jLoggingFilter`, `Slf4jLogger`, and Pekko event-stream integration. Three tests already pass in the native run without starting the full `ActorSystem`, and the remaining failures are all on valid ActorSystem startup paths blocked by missing native-image metadata. Keeping the tests preserves useful coverage for the metadata once the resource and reflective registrations are completed.
