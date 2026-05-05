# Post-generation intervention report

Library: org.tpolecat:doobie-postgres_3:0.13.4
Stage: metadata_fix_failed

## Summary

The native test run still fails after the metadata-fix attempt. The failures are metadata-related, not generated-test bugs or unsupported platform behavior. The generated tests initialize Doobie/Doobie Postgres Scala 3 singleton objects whose lazy-val implementation calls `Class.getDeclaredField` for synthetic `0bitmap$*` fields. In a native image those fields must be registered for reflection; otherwise initialization fails with `NoSuchFieldException` and then cascades into `NoClassDefFoundError` for the same singleton objects.

## Root cause of the failures

The Gradle failure excerpt shows seven native-test failures in `Doobie_postgres_3Test`:

- `javaTimeAndPostgisMetaInstancesAreAvailable()`
- `postgresMetaInstancesAreAvailableForDriverSpecificTypes()`
- `postgresEnumHelpersConstructMetaMappingsForScalaAndJavaEnums()`
- `postgresSqlStateSyntaxRecoversOnlyMatchingSqlExceptions()`
- `copyInReturnsZeroForEmptyCollectionsWithoutOpeningCopyApi()`
- `postgresExplainSyntaxPrefixesQueriesAndUpdatesWithExplainStatements()`
- `postgresArrayMetaInstancesCoverBoxedUnboxedOptionalAndDecimalArrays()`

They all have the same underlying cause: Scala 3 lazy-val bitmap fields are missing from reflection metadata. Earlier failures were caused by missing `0bitmap$1`/`0bitmap$2` field registrations on Doobie package singleton classes such as `doobie.postgres.package$implicits$` and `doobie.package$implicits$`. Codex added several field registrations and moved the failure forward, but the last captured run still fails during initialization of `doobie.util.meta.SqlMeta$javasql$`:

```text
Caused by: java.lang.NoSuchFieldException: 0bitmap$1
  at java.lang.Class.getDeclaredField(...)
  at scala.runtime.LazyVals$.getOffset(LazyVals.scala:156)
  at doobie.util.meta.SqlMeta$javasql$.<clinit>(sqlmeta.scala:11)
```

The subsequent `NoClassDefFoundError: Could not initialize class doobie.postgres.package$implicits$` failures are cascading effects from that failed singleton initialization, not independent test defects.

## Metadata still missing

Codex did not finish the metadata-fix loop. The log ends while Codex is scanning `doobie.util.meta` classes after discovering that the failure moved to the Doobie meta layer. The remaining missing metadata includes at least the synthetic lazy-val bitmap field on:

- `doobie.util.meta.SqlMeta$javasql$` field `0bitmap$1`

A targeted scan of the same Doobie core jar also shows related `doobie.util.meta` singleton bitmap fields that are not present in the current generated metadata and may be reached by the generated coverage:

- `doobie.util.meta.LegacyMeta$legacy$` field `0bitmap$1`
- `doobie.util.meta.TimeMeta$javatime$` field `0bitmap$1`
- `doobie.util.meta.TimeMeta$javatimedrivernative$` field `0bitmap$2`

No metadata files were modified as part of this intervention.

## Intervention decision

No generated tests were removed. The failures are metadata-related because the native image lacks reflection access to Scala 3 synthetic lazy-val bitmap fields required by Doobie initialization. Removing the tests would hide real missing reachability metadata rather than removing an invalid generated scenario.

## Why the generated support should be preserved

The generated support exercises meaningful Doobie Postgres APIs: Postgres-specific `Meta` instances, array mappings, Java time/PostGIS mappings, enum helpers, SQL state helpers, `COPY` behavior, and explain syntax. These tests already exposed concrete missing native-image metadata and are useful coverage for completing support for `org.tpolecat:doobie-postgres_3:0.13.4`. The remaining failures are caused by incomplete metadata, so the generated test support should stay in place to verify the eventual metadata fix.
