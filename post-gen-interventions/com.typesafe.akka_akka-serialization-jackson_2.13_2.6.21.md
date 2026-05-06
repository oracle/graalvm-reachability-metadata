# Post-generation intervention report

Library: com.typesafe.akka:akka-serialization-jackson_2.13:2.6.21

Stage: `metadata_fix_failed`

## Summary

The native test executable builds, but all three generated native tests fail immediately while creating an Akka `ActorSystem`. The failure is metadata-related: `ConfigFactory.load()` cannot see the Akka reference configuration that defines `akka.loggers`, so Akka aborts during actor-system startup before the serializer-specific assertions run.

## Failure root causes

- `Akka_serialization_jackson_2_13Test.cborSerializerRoundTripsAkkaModuleTypes()` fails with `ConfigException$Missing: No configuration setting found for key 'akka.loggers'` during `ActorSystem` initialization. This indicates missing native-image resource metadata for Akka configuration resources such as `reference.conf`, not a bad CBOR serialization test.
- `Akka_serialization_jackson_2_13Test.jsonSerializerAppliesConfiguredMigrationForOlderManifest()` fails for the same shared `ActorSystem` startup reason before the Jackson migration path is exercised.
- `JacksonTypeInManifestOffTest.jsonSerializerUsesConfiguredDeserializationTypeWhenManifestIsDisabled()` also fails for the same missing Akka configuration resource during `ActorSystem` startup before the manifest-disabled serializer path is exercised.

## Codex metadata-fix result

The Codex metadata-fix log shows a partial iterative metadata repair. Earlier reruns exposed cascading native-image metadata gaps in Akka startup, including reflective access to Akka routers, mailbox semantics, actor-provider guardians, event logging actors, and event-stream support. Codex kept adding metadata and re-running the targeted test, but the log ends while another `./gradlew test -Pcoordinates=com.typesafe.akka:akka-serialization-jackson_2.13:2.6.21 --stacktrace` invocation was still in progress, so it did not reach a verified passing state.

The Gradle failure excerpt shows the remaining failure as missing Akka configuration (`akka.loggers`). That means the test image still lacks the required Akka reference configuration resource at runtime. Because this is a reachability/resource metadata gap rather than a native-image limitation, unsupported platform feature, or generated-test bug, no generated tests were removed.

## Why preserve the generated support

The generated tests exercise meaningful support for `akka-serialization-jackson_2.13`: Akka module type round-tripping with CBOR, configured Jackson migration handling for older manifests, and `type-in-manifest = off` JSON deserialization with compression. All failures occur before those library behaviors are reached, at shared Akka `ActorSystem` bootstrap. Preserving the tests keeps coverage for the intended serialization features and provides concrete validation once the remaining Akka configuration/resource metadata is completed.
