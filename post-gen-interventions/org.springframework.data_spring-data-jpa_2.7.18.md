# Post-generation intervention report

Library: org.springframework.data:spring-data-jpa:2.7.18
Stage: metadata_fix_failed

## Summary

The native test executable builds, but `nativeTest` fails at runtime for three generated tests. All three failures have the same root cause: GraalVM reports a missing reflection registration for reflective array instantiation of `org.springframework.core.annotation.AnnotationAttributes[]` while Spring is creating the `AnnotationConfigApplicationContext` for the JPA repository tests.

This is metadata-related, not a test bug or unsupported native-image/platform feature. No generated tests were removed.

## Failed tests and root cause

- `repositoryProxySupportsCrudQueriesSpecificationsExamplesPagingAndProjections()` fails with `MissingReflectionRegistrationError: Cannot reflectively instantiate the array class 'org.springframework.core.annotation.AnnotationAttributes[]'`.
- `persistableEntitiesUseApplicationProvidedNewStateWithAssignedIdentifiers()` fails with the same missing reflection registration for `org.springframework.core.annotation.AnnotationAttributes[]`.
- `nativeSqlQueryMethodsMapScalarResults()` fails with the same missing reflection registration for `org.springframework.core.annotation.AnnotationAttributes[]`.

The stack trace goes through Spring annotation processing, including `org.springframework.core.annotation.TypeMappedAnnotation.asMap`, `StandardAnnotationMetadata.getAnnotationAttributes`, and `AnnotationConfigApplicationContext` registration. The native-image error suggests the missing `reflection` metadata element:

```json
{
  "type": "org.springframework.core.annotation.AnnotationAttributes[]"
}
```

## Codex metadata-fix result

The Codex metadata-fix log shows that Codex reproduced the failure and identified the missing `AnnotationAttributes[]` reflection registration. It attempted a partial metadata fix by adding a conditional `reflection` entry guarded by `org.springframework.core.annotation.TypeMappedAnnotation`, then reran the test. The final Gradle failure still reports the same missing registration, so the metadata fix did not fully resolve the runtime requirement.

Codex could not complete the repair because the remaining failure is still the same missing reflection registration during Spring annotation metadata handling. The existing attempted conditional entry is insufficient for the native runtime path exercised by these tests, or the required registration needs a different condition/scope. Metadata files were not modified during this intervention.

## Why the generated support should be preserved

The generated tests exercise meaningful public `spring-data-jpa` behavior: repository proxy creation, CRUD and derived queries, specifications, examples, paging, projections, native SQL scalar mapping, `Persistable` assigned identifiers, sorting helpers, and Java time converters. One test already succeeds in native mode, and the failing tests all stop at a shared Spring annotation metadata reflection gap before reaching their JPA assertions.

Because the failures are metadata-related and identify concrete missing reachability metadata, the generated support remains useful coverage for future metadata completion and should be preserved rather than removed.
