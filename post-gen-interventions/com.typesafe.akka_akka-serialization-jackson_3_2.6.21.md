# Post-generation intervention report

Library: com.typesafe.akka:akka-serialization-jackson_3:2.6.21

Stage: metadata_fix_failed

## Summary

The generated Scala test suite still fails only in the native-image test run. All six JUnit tests fail while constructing the shared Akka `ActorSystem`; the first failure is an `ExceptionInInitializerError` caused by `java.lang.NoSuchFieldException: 0bitmap$1` during Scala lazy-val initialization in `akka.actor.ActorSystemImpl`, and the remaining tests then fail with `NoClassDefFoundError: Could not initialize class akka.actor.ActorSystemImpl`.

This is a metadata-related failure, not a test bug or unsupported feature. No generated tests were removed.

## Root cause by failing test

- `jsonSerializerRestoresScalaCaseObjectSingletonsFromManifest()` triggers the first `ActorSystem` initialization and fails because the native image does not expose the Scala-generated lazy-val bitmap field `akka.actor.ActorSystemImpl.0bitmap$1` for reflective access.
- `jsonSerializerRoundTripsClassicActorRefsWithAkkaModule()` fails for the same root cause after `ActorSystemImpl` has already failed class initialization.
- `jsonSerializerRoundTripsScalaMessagesAndCompressedPayloads()` fails for the same root cause after `ActorSystemImpl` has already failed class initialization.
- `cborSerializerCanDeserializeWithConfiguredTypeInsteadOfManifestClassName()` fails for the same root cause after `ActorSystemImpl` has already failed class initialization.
- `objectMapperProviderCachesMappersAndInstallsAkkaAndScalaModules()` fails for the same root cause after `ActorSystemImpl` has already failed class initialization.
- `jacksonMigrationTransformsOlderJsonSchemaBeforeDeserialization()` fails for the same root cause after `ActorSystemImpl` has already failed class initialization.

## Codex metadata-fix findings

The Codex log shows a partial metadata repair loop. It first found Akka HOCON resource loading gaps (`reference.conf` and `version.conf`), then progressed through several Akka actor bootstrap metadata gaps, including Scala lazy-val bitmap fields on `akka.event.EventStream` and `akka.actor.Address`, reflective loading of `akka.event.DefaultLoggingFilter` and `akka.event.Logging$DefaultLogger`, and reflective construction of `akka.actor.LightArrayRevolverScheduler`.

Codex did not converge to a passing native test. The remaining failure is still in Akka actor-system bootstrap metadata: the native image needs additional reflection metadata for Scala-generated lazy-val state, specifically the `0bitmap$1` field on `akka.actor.ActorSystemImpl` as reported by the Gradle failure output. Because the failure is still a concrete missing dynamic-access/reflective-access issue, the generated tests should be preserved rather than deleted.

## Why preserve the generated support

The remaining generated tests exercise meaningful `akka-serialization-jackson_3` behavior: Jackson object mapper module installation and caching, JSON and CBOR serializer round trips, compressed payloads, classic actor-ref serialization, Jackson migrations, and Scala case-object singleton restoration. These paths are legitimate library usage and expose real native-image reachability requirements in Akka's transitive actor bootstrap. Removing the tests would hide the metadata gaps instead of documenting and preserving coverage for the generated support that already identifies the missing metadata chain.
