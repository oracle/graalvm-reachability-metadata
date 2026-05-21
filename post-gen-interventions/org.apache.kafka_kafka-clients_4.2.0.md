# Post-generation intervention report

Library: org.apache.kafka:kafka-clients:4.2.0
Stage: metadata_fix_failed

## Summary

The failing Gradle run had one native-only test failure:

- `OrgApacheKafkaShadedComGoogleProtobufDescriptorMessageInfoFactoryTest.generatedMessageV3SchemaInspectsDescriptorBackedMessageShape()`

The failure was a `java.lang.NullPointerException` from Kafka's shaded protobuf runtime at `UnsafeUtil.objectFieldOffset`, reached through `MessageSchema.storeFieldData` while building a schema for a descriptor-backed `GeneratedMessageV3` test message.

## Root cause

This is metadata-related, not a bad generated test or an unsupported native-image platform feature. The failing path reflectively inspects generated protobuf message fields and methods. The Codex metadata-fix log shows the fix loop reduced the run to this single failure, then identified that the metadata used during the failing verification was still incomplete for the descriptor-backed protobuf schema path.

The remaining missing or incomplete metadata was for the test protobuf message shapes inspected by `DescriptorMessageInfoFactory`, specifically reflective access to:

- `OrgApacheKafkaShadedComGoogleProtobufDescriptorMessageInfoFactoryTest$DescriptorBackedMessage` fields `entries_`, `kindCase_`, and `kind_`, with methods `getDefaultInstance()`, `getChild()`, and `getEntries(int)`.
- `OrgApacheKafkaShadedComGoogleProtobufDescriptorMessageInfoFactoryTest$ChildMessage` field `name_`, with method `getDefaultInstance()`.

Codex initially tried a broader `allDeclaredFields` registration for `DescriptorBackedMessage`, but the native run still failed. The log then shows Codex comparing the collected test metadata against the repository metadata and concluding that explicit field registrations for both `DescriptorBackedMessage` and nested `ChildMessage` were needed. The Codex session ended after a clean `runNativeTraceImage` reproduction and before the final forced full `./gradlew test -Pcoordinates=org.apache.kafka:kafka-clients:4.2.0 --rerun-tasks` closeout, so the workflow still reported the earlier failed full Gradle verification.

## Intervention decision

No generated test was removed. The failure is a partial metadata-fix failure rather than evidence that the test is invalid.

## Why the remaining generated support should be preserved

The generated Kafka clients support exercises real dynamic-access surfaces that already pass in the native run: serializers/deserializers/Serdes, consumer partition assignors, SASL/SCRAM and OAuth bearer defaults, list deserialization constructors, networking selector cleanup paths, Kafka message JSON conversion, signal handler access, and shaded protobuf serialization helpers. The Gradle excerpt shows `60/61` tests passing, so removing the support would discard broad valid coverage. The lone failing protobuf descriptor-schema test exposes a concrete metadata gap in shaded protobuf reflective field/method access and should be preserved to validate the required metadata once the fix is completed.
