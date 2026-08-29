# Post-generation intervention report

Library: org.hibernate:hibernate-core:6.1.0.Final

## Summary

The native test run failed in three serialization/deserialization tests:

- `BoundedConcurrentHashMapTest.preservesAnEmptyMapAcrossSerialization`
- `ConcurrentReferenceHashMapTest.preservesStrongEntriesAcrossSerialization`
- `SerializationHelperInnerCustomObjectInputStreamTest.resolvesApplicationClassesWhileDeserializing`

All three fail with `MissingReflectionRegistrationError` when `ObjectInputStream` reflectively invokes `java.lang.Object()` while deserializing. This is a metadata-related failure, not an unsupported Native Image behavior, so no generated tests or support files were removed.

## Missing metadata and fix status

The effective Native Image configuration still lacks reflection registration for:

```text
java.lang.Object.<init>()
```

The metadata-fix agent log confirms that it re-ran the coordinate with the required GraalVM but the same serialization-constructor registration error remained; consequently, its metadata changes could not be verified. The failure occurs in each test's normal `SerializationHelper` deserialization path, so the attempted metadata repair did not add or expose the required registration to the native test image.

## Preserved support

The three tests exercise ordinary Hibernate serialization behavior through `SerializationHelper`, and the other 127 native tests pass. They provide meaningful coverage of library behavior and surface a real unresolved metadata gap. Preserving the tests keeps the missing registration detectable for a later metadata-only repair without weakening the generated coverage.
