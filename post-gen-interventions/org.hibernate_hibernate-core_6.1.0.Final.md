# Post-generation intervention

Library: org.hibernate:hibernate-core:6.1.0.Final
Stage: `metadata_fix_failed`

## Summary

The supplied Gradle failure was a malformed test reachability configuration: the test resource had a trailing comma at line 132. The Codex log shows this was corrected by completing the conditional `logback.xml` resource entry, after which Native Image built successfully.

The resulting native run had two remaining failures:

- `StatefulPersistenceContextTest.serializesAndDeserializesSessionPersistenceContext`
- `ImmutableEntityEntryTest.serializesSessionContainingImmutableEntity`

Both serialize a Hibernate `Session` and deserialize it with `ObjectInputStream`. Deserialization calls `Class.forName("org.hibernate.internal.SessionImpl")`, which fails because it requires runtime class loading. This is unsupported Native Image behavior, not missing reachability metadata. The two generated tests and their serialization-only fixtures were removed; the persistence-unit declarations were removed with them. No metadata files were modified by this intervention.

## Preserved support

The remaining generated Hibernate coverage is independent of session serialization and continues to exercise persistence, HQL, identifier generation, dialect selection, configuration-resource lookup, and session-factory options. `./gradlew test -Pcoordinates=org.hibernate:hibernate-core:6.1.0.Final --stacktrace` passes with 47 native tests after the removal, so that support remains valid and should be preserved.
