# Post-generation intervention report

Library: org.springframework.boot:spring-boot-liquibase:4.1.0
Stage: metadata_fix_failed

## Summary

The native test run failed for two different reasons:

1. Three generated tests parsed Liquibase XML changelogs whose schema location points at `http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd`. In native image, the `http` URL protocol is not enabled by default, so Liquibase/Xerces failed while trying to resolve that remote schema URL. This is not a reachability-metadata miss; it is a generated-test/native-image configuration issue.
2. Two generated Spring Boot auto-configuration tests still failed because Spring did not create a `SpringLiquibase` bean in native mode. The Codex log shows a sequence of metadata-related Spring Boot configuration-processing gaps around `LiquibaseAutoConfiguration` nested condition/configuration classes. Codex narrowed those gaps but did not finish resolving the metadata needed for the auto-configuration path.

## Failure classification

| Test | Root cause | Action |
| --- | --- | --- |
| `dataSourceClosingSpringLiquibaseRunsMigrationAndClosesDataSourceAfterMigration()` | Non-metadata native-image URL protocol limitation: Liquibase XML schema resolution attempted to access `http://www.liquibase.org/.../dbchangelog-latest.xsd`, but `http` was not enabled in the native image. | Removed. |
| `dataSourceClosingSpringLiquibaseCanDeferCloseUntilDestroy()` | Same non-metadata Liquibase XML schema-resolution failure through disabled native-image `http` URL protocol. | Removed. |
| `liquibaseEndpointReportsExecutedChangeSetsFromCurrentAndParentContexts()` | Same non-metadata Liquibase XML schema-resolution failure through disabled native-image `http` URL protocol before endpoint assertions could be reached. | Removed. |
| `liquibaseAutoConfigurationUsesDedicatedJdbcUrlAndChangeLogParameters()` | Metadata-related Spring Boot auto-configuration failure. Codex saw missing/insufficient native visibility for Spring Boot condition/configuration processing and ended with no `SpringLiquibase` bean in native mode. | Preserved. |
| `liquibaseAutoConfigurationPrefersLiquibaseDataSourceBeanOverApplicationDataSource()` | Same metadata-related auto-configuration failure; Spring Boot did not produce a `SpringLiquibase` bean after Codex's partial metadata fixes. | Preserved. |

## Intervention

Removed the generated tests that only exercised the non-metadata Liquibase XML schema-resolution path:

- `dataSourceClosingSpringLiquibaseRunsMigrationAndClosesDataSourceAfterMigration()`
- `dataSourceClosingSpringLiquibaseCanDeferCloseUntilDestroy()`
- `liquibaseEndpointReportsExecutedChangeSetsFromCurrentAndParentContexts()`

These failures were rooted in native-image URL protocol handling (`--enable-url-protocols=http`), not in missing reachability metadata, so preserving those tests would keep a non-metadata blocker in the generated support.

No metadata files were changed as part of this intervention.

## Metadata still missing

Codex did not finish the metadata fix for the Spring Boot auto-configuration path. The log shows it iteratively handled several native-access gaps, including reflective or class-resource access related to:

- `LiquibaseAutoConfiguration$LiquibaseDataSourceCondition` and its nested condition classes;
- Spring Boot condition evaluator classes such as `OnClassCondition` and `OnBeanCondition`;
- imported SQL initialization classes such as `DatabaseInitializationDependencyConfigurer` and nested helper types;
- reflective invocation of `LiquibaseAutoConfiguration` factory methods.

After those partial fixes, the remaining native run still reported that no `liquibase.integration.spring.SpringLiquibase` bean was available for the auto-configuration tests. That indicates the Spring Boot configuration/condition metadata remains incomplete or too narrow, but the final failure no longer supplied a single concrete suggested metadata entry that Codex could safely add.

## Why the remaining generated support should be preserved

The remaining tests exercise useful library behavior without relying solely on the removed URL-protocol-sensitive path:

- binding and default behavior of `LiquibaseProperties`;
- `LiquibaseConnectionDetails` behavior;
- creation of `SpringLiquibase` from `LiquibaseAutoConfiguration` with a dedicated Liquibase JDBC URL;
- selection of a bean qualified with `@LiquibaseDataSource`;
- public endpoint/configuration descriptor APIs.

Those auto-configuration tests expose real native metadata coverage gaps in `org.springframework.boot:spring-boot-liquibase:4.1.0`, so they should remain for the next metadata-fix pass.

## Verification

After removing the non-metadata tests, `./gradlew compileTestJava -Pcoordinates=org.springframework.boot:spring-boot-liquibase:4.1.0 --stacktrace` completed successfully.
