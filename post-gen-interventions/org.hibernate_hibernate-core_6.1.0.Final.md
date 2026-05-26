# Post-generation intervention report

Library: org.hibernate:hibernate-core:6.1.0.Final
Stage: metadata_fix_failed

## Summary

The metadata-fix run reached native image execution, but the generated suite still failed. The failures fall into two categories:

- Non-metadata failures from generated tests that directly exercise Java object serialization with `ObjectOutputStream`/`ObjectInputStream`. GraalVM Native Image reports `java.lang.IllegalStateException: Object serialization is currently not supported` for these paths, so these tests were removed.
- Metadata-related failures where runtime access is real but metadata is still missing or conditioned too narrowly. Those tests were preserved.

## Removed non-metadata tests

The following generated tests were removed because their root cause is a Native Image/JDK serialization limitation rather than missing Hibernate reachability metadata:

- `tests/src/org.hibernate/hibernate-core/6.1.0.Final/src/test/java/org_hibernate/hibernate_core/BoundedConcurrentHashMapTest.java`
  - Failing methods: `deserializesEmptyMap`, `serializesEntriesAndEndOfStreamMarker`.
  - Root cause: direct Java object serialization of `BoundedConcurrentHashMap` fails with `Object serialization is currently not supported`.
- `tests/src/org.hibernate/hibernate-core/6.1.0.Final/src/test/java/org_hibernate/hibernate_core/ConcurrentReferenceHashMapTest.java`
  - Failing method: `serializesEntriesAndRestoresThem`.
  - Root cause: direct Java object serialization of `ConcurrentReferenceHashMap` fails with the same Native Image serialization limitation.
- `tests/src/org.hibernate/hibernate-core/6.1.0.Final/src/test/java/org_hibernate/hibernate_core/ImmutableEntityEntryTest.java`
  - Failing method: `serializeAndDeserializePersistenceContextWithImmutableEntityEntry`.
  - Root cause: the test serializes a Hibernate persistence context through `ObjectOutputStream`, which fails before a metadata-specific missing-registration error can be reported.
- `tests/src/org.hibernate/hibernate-core/6.1.0.Final/src/test/java/org_hibernate/hibernate_core/StatefulPersistenceContextTest.java`
  - Failing method: `serializeAndDeserializePersistenceContextWithTrackedState`.
  - Root cause: the test serializes a populated `StatefulPersistenceContext` and hits the same unsupported object serialization path.

I also removed the generated test-only `native-image.properties` workaround at `tests/src/org.hibernate/hibernate-core/6.1.0.Final/src/test/resources/META-INF/native-image/native-image.properties`. It only attempted to work around the serialization-induced JUnit `TestIdentifier` initialization failure and is not needed once the serialization tests are removed.

## Metadata-related failures preserved

The remaining failing generated tests should not be removed because they expose real reachability metadata gaps:

- `JaxbXmlFormatMapperTest` failed with `MissingReflectionRegistrationError` for a JAXB dynamic proxy inheriting `jakarta.xml.bind.annotation.XmlAnyElement` and `org.glassfish.jaxb.core.v2.model.annotation.Locatable`. This is a missing/insufficient proxy registration for the JAXB annotation-reading path reached by `org.hibernate.type.JaxbXmlFormatMapper`.
- `PostgreSQLPGObjectJdbcTypeAnonymous1Test` failed while reflectively invoking `public org.postgresql.util.PGobject()`. Metadata for this constructor exists elsewhere, but it was inactive because it was conditioned on `org.postgresql.Driver` while Hibernate reaches it from `org.hibernate.dialect.PostgreSQLPGObjectJdbcType$1`.
- The Codex log also showed earlier ordering/condition issues for logging resources such as `META-INF/services/org.jboss.logging.LoggerProvider`, where metadata existed but was inactive because the `org.hibernate.jpa.HibernatePersistenceProvider` condition had not been reached yet during `StrategySelectorBuilder` bootstrap.

Codex could not fully resolve these because after partially addressing metadata conditions, the run became dominated by the unsupported Java serialization/JUnit initialization path. That non-metadata failure blocked a clean final verification cycle for the still-valid metadata gaps.

## Why remaining support should be preserved

Most generated Hibernate support exercises ordinary Hibernate bootstrapping, dialect binding, JAXB XML mapping, scanning, validation integration, identifier generation, and JDBC-related dynamic access. These are representative native-image reachability paths for `hibernate-core` and are independent of the removed Java serialization tests. Preserving the remaining tests keeps useful coverage for the metadata that Codex already added and for the still-actionable metadata gaps described above, while dropping only the generated coverage that depends on unsupported object serialization behavior.
