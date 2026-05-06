# Post-generation intervention report

Library: io.getquill:quill-jdbc_2.13:4.8.5
Stage: metadata_fix_failed

## Summary

The generated native test fixture builds and starts, but all seven JUnit tests fail at native runtime before their JDBC assertions can execute. The common failure is Scala runtime reflection inside Quill failing to resolve Scala/JDK symbols in the native image, initially:

```text
scala.reflect.internal.MissingRequirementError: class scala.Array ... not found
```

The Codex metadata-fix log shows this is metadata-related rather than a bad generated test: adding Scala class resources and reflective registration moved the failure from `scala.Array` to `scala.Unit`, then to `scala.annotation.compileTimeOnly`, then into JDK symbols such as `java.lang.CloneNotSupportedException` and finally `java.io.UnsupportedEncodingException`.

## Root cause by failing test

Each generated test fails for the same root cause: Quill's quoted-query path initializes Scala runtime reflection through `io.getquill.quat.TypeTaggedQuatMaking`, and the native image does not yet contain enough reflection/resource metadata for the Scala runtime mirror to load its required symbols.

- `quotedCrudQueriesRunAgainstJdbcAndDecodeOptionalValues()` fails while resolving Scala runtime reflection metadata, not because the CRUD/JDBC logic is invalid.
- `customEncodersAndDecodersAreAppliedForUserDefinedValueTypes()` fails while resolving Scala runtime reflection metadata, not because the custom encoder/decoder test is invalid.
- `translateAndProbeExposeGeneratedSqlAndDatabaseValidation()` fails while resolving Scala runtime reflection metadata, not because translate/probe is invalid.
- `jdbcEncodersAndDecodersRoundTripCommonScalarTypes()` fails while resolving Scala runtime reflection metadata, not because scalar JDBC decoding is invalid.
- `quotedJoinsComposeQueriesAcrossRelatedTables()` fails while resolving Scala runtime reflection metadata, not because joins are invalid.
- `updateDeleteAndTransactionsPreserveJdbcSemantics()` fails while resolving Scala runtime reflection metadata, not because update/delete/transaction semantics are invalid.
- `returningGeneratedReadsDatabaseAssignedKeysFromJdbcInserts()` is part of the same generated fixture and depends on the same Quill quote/runtime-reflection path; it should be preserved for coverage even though the shared metadata gap prevents the native fixture from reaching its JDBC assertions.

## Remaining missing metadata

Codex could not complete the fix because the missing metadata is not a single Quill class or method. The runtime reflection graph expands across broad Scala standard-library and JDK symbol sets:

- Scala class resources were needed for `scala/**/*.class`.
- Reflective lookup was needed for Scala root/runtime symbols such as `scala.Array`, `scala.Unit`, and `scala.annotation.compileTimeOnly`.
- After broad Scala registration, the next failures moved to JDK symbols used while Scala reflection copies/unpickles annotations, including `java.lang.CloneNotSupportedException` and `java.io.UnsupportedEncodingException`.

This indicates that additional reflection/resource metadata is still required for Scala runtime reflection and JDK classes reached by that reflection path. Codex stopped before producing a validated, minimal metadata set because each attempted addition exposed another symbol family, eventually crossing from Scala into broad JDK packages.

## Intervention decision

No generated test was removed. The failures are metadata-related: the tests exercise legitimate `quill-jdbc_2.13` functionality, and the observed stack traces consistently point to missing native-image reachability metadata for Scala runtime reflection rather than to a test bug, unsupported SQL feature, or platform limitation in the generated assertions.

## Why the generated support should be preserved

The remaining generated support covers core Quill JDBC behavior that is valuable once the Scala reflection metadata gap is resolved: quoted CRUD, updates/deletes, transactions, scalar encoders/decoders, custom value-type encoders/decoders, joins, SQL translation/probing, and generated-key inserts. Removing the fixture would discard meaningful coverage for `io.getquill:quill-jdbc_2.13:4.8.5`; preserving it keeps the failing native-image metadata gap visible for a future targeted metadata fix.
