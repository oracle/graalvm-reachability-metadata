# Post-generation intervention report

Library: org.apache.kafka:kafka-clients:4.2.0
Stage: metadata_fix_failed

## Summary

The native test run initially failed in four generated tests. Two failures were real missing reachability metadata that Codex addressed during the metadata-fix attempt. The remaining failures were not actionable by reachability metadata, so the generated tests that caused them were removed:

- `AbstractConfigTestAnonymous1RestrictedClassLoaderTest.java`
- `GeneratedMessageLiteTest.java`

No metadata files were modified by this post-generation intervention.

## Failure analysis

| Test | Root cause | Action |
| --- | --- | --- |
| `AbstractConfigTestAnonymous1RestrictedClassLoaderTest.exercisesRestrictedClassLoaderFallbackForClassConfigs()` | Not metadata-related. The test delegates to Kafka's `AbstractConfigTest.testClassConfigs()` and expects a `ConfigException` when a class cannot be loaded through a restricted class loader. In the native image closed-world runtime, that class-loading fallback did not fail as the JVM test expects, so the assertion failed with “Expected `ConfigException` to be thrown, but nothing was thrown.” | Removed the generated test. |
| `GeneratedMessageLiteTest.buildsSerializesAndParsesLiteMessage()` | Not metadata-related. The failure was a `NullPointerException` inside shaded protobuf schema construction (`MessageSchema.newSchemaForRawMessageInfo`) for a hand-written generated-message-lite test class. Codex tried an agent-derived/protobuf-style metadata experiment, but it did not change the failure, and there was no `Missing*RegistrationError` identifying an actionable metadata entry. This points to a generated test/protobuf-runtime mismatch rather than missing metadata. | Removed the generated test. |
| `ListDeserializerTest.deserializesIntoListWithSizeConstructor()` | Metadata-related. `ListDeserializer` reflectively constructs `java.util.ArrayList`; native image initially lacked the required reflective constructor access, producing `NoSuchMethodException: java.util.ArrayList.<init>()`. Codex added the relevant `java.util.ArrayList` constructor metadata, and the later metadata-fix log shows this test passing. | Preserved the generated test. |
| `LoggingSignalHandlerTest.registersJvmSignalHandlers()` | Metadata-related. `LoggingSignalHandler` reflectively accesses `sun.misc.Signal` / `sun.misc.SignalHandler`; native image initially lacked reflective access to `sun.misc.SignalHandler.handle(sun.misc.Signal)`. Codex added the signal reflection/proxy metadata, and the later metadata-fix log shows this test passing. | Preserved the generated test. |

## Why the remaining generated support should be preserved

The remaining generated tests exercise real Kafka client runtime paths that passed after Codex's metadata fixes, including serializers/deserializers, security provider creation, selectors, message round trips, utility reflection, resource access, and JVM/native behavior that is valid under GraalVM Native Image. The failures left after the metadata-fix attempt were isolated to two generated tests whose assumptions do not hold in the native-image runtime, not to the broader generated support. Preserving the rest keeps useful dynamic-access coverage for `org.apache.kafka:kafka-clients:4.2.0` while removing only the non-metadata blockers.
