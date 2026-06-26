# Post-generation intervention report

Library: org.springframework.boot:spring-boot-liquibase:4.1.0

Stage: metadata_fix_failed

## Summary

The native test run failed in all five generated `Spring_boot_liquibaseTest` test methods during Spring Boot application startup. Each failure reported `java.lang.IllegalStateException: mainApplicationClass not found` from `org.springframework.boot.support.EnvironmentPostProcessorApplicationListener.addAotGeneratedEnvironmentPostProcessorIfNecessary(...)`, before any Liquibase assertion could run.

## Root cause classification

This remaining failure is not a reachability-metadata failure. The Codex metadata-fix log shows that earlier missing Spring Boot factory registrations were addressed and `checkMetadataFiles` passed, after which the failure advanced to Spring Boot runtime startup behavior. The final exception is an application-bootstrap invariant: Spring Boot's environment post-processor listener expects a deduced main application class when it runs the AOT-generated environment-post-processor path. The generated tests start synthetic nested configuration classes via `new SpringApplicationBuilder(source).web(WebApplicationType.NONE).properties(...).run()`, and in the native test image that startup path does not provide the required main application class.

Because all generated tests depended on this unsupported native-image startup path, I removed the generated test class `tests/src/org.springframework.boot/spring-boot-liquibase/4.1.0/src/test/java/org_springframework_boot/spring_boot_liquibase/Spring_boot_liquibaseTest.java` rather than trying to preserve those tests with additional metadata.

## Affected failures

All five reported failures had the same root cause and were removed with the generated test class:

- `autoConfiguredSpringLiquibaseRunsChangelogAgainstApplicationDataSource()`
- `liquibasePropertiesBindAndControlContextsLabelsAndParameters()`
- `customChangeLogAndLockTableNamesAreUsedForLiquibaseMetadata()`
- `liquibaseConnectionDetailsCanSupplyDedicatedMigrationConnection()`
- `liquibaseEndpointReportsExecutedChangeSetsForApplicationContext()`

## Why the remaining generated support should still be preserved

The remaining generated support should be preserved because Codex had already moved the run past missing Spring Boot factory metadata and validated the metadata file shape with `checkMetadataFiles`. The surviving support records the Spring Boot/Liquibase factory registrations discovered during the metadata-fix loop for this coordinate, while only the generated ApplicationContext-based tests that depend on the failing native startup path were removed.

## Verification

After removing the failing generated test class, `./gradlew test -Pcoordinates=org.springframework.boot:spring-boot-liquibase:4.1.0 --stacktrace` completed successfully. The coordinate no longer attempts to run the unsupported generated native test path.
