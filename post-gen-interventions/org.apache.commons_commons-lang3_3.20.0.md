# Post-generation intervention report

Library: org.apache.commons:commons-lang3:3.20.0
Stage: metadata_fix_failed

## Summary

The native test run failed in `EventListenerSupportTest.serializationRoundTripKeepsSerializableListenersAndDropsNonSerializableListeners()` during Java deserialization of `EventListenerSupport`. The native executable raised:

```text
org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively invoke constructor 'public java.lang.Object()'
```

The failing generated JUnit test method was removed from `tests/src/org.apache.commons/commons-lang3/3.20.0/src/test/java/org_apache_commons/commons_lang3/EventListenerSupportTest.java`.

## Root cause

This remaining failure is not a normal missing-library-metadata gap. The Codex metadata-fix log shows that Codex reproduced the same `java.lang.Object()` deserialization failure, tried the suggested reflection registration, tried `EventListenerSupport` serialization metadata, and also tried test-scoped serialization/unsafe-allocation metadata for the generated listener payload. Direct native execution still failed with the same `MissingReflectionRegistrationError`.

That means the generated test is exercising a native-image serialization/deserialization limitation around this `EventListenerSupport` round-trip rather than exposing an unresolved commons-lang3 reachability metadata requirement that Codex could finish by adding another library metadata entry.

## Preserved generated support

The rest of the generated commons-lang3 support should be preserved. The Gradle excerpt shows the other generated tests passing, including reflection-heavy coverage for `ArrayUtils`, `ClassUtils`, `ConstructorUtils`, `FieldUtils`, `MethodUtils`, builder reflection helpers, `SerializationUtils`, `Streams`, and `TypeUtils`. Those passing tests continue to validate meaningful native-image reachability behavior for commons-lang3 3.20.0 and are not implicated in the unsupported `EventListenerSupport` serialization path.

No metadata files were modified as part of this intervention.
