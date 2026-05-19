# Post-generation intervention report

Library: org.springframework.boot:spring-boot-transaction:4.0.2

Stage: `metadata_fix_failed`

## Summary

The native test run failed in four generated tests. All failures are metadata-related: Spring's native execution path is trying to load Spring Boot auto-configuration condition classes and read Spring Boot auto-configuration `.class` resources that are not available in the native image.

No generated tests were removed, and no metadata files were modified by this intervention.

## Failure root causes

- `autoConfiguredTransactionTemplateRollsBackRollbackOnlyTransactions()` failed with `ClassNotFoundException: org.springframework.boot.autoconfigure.condition.OnClassCondition` while `ConditionEvaluator` resolved Spring Boot condition classes. This is missing reflection/reachability metadata for Spring Boot's condition implementation.
- `autoConfigurationBindsPropertiesCreatesCustomizersAndTransactionTemplate()` failed with the same missing `org.springframework.boot.autoconfigure.condition.OnClassCondition` root cause.
- `transactionAutoConfigurationEnablesDeclarativeTransactionManagementWithJdkProxies()` failed with `FileNotFoundException` for `org/springframework/boot/autoconfigure/ImportAutoConfigurationImportSelector.class` while Spring's configuration class parser read annotation metadata. This is missing resource metadata for Spring Boot's auto-configuration class files.
- `transactionAutoConfigurationCreatesTransactionTemplateForSingleTransactionManager()` failed with the same missing `ImportAutoConfigurationImportSelector.class` resource root cause.

## Codex metadata-fix status

The Codex metadata-fix log shows an iterative metadata repair attempt. It identified Spring Boot auto-configuration metadata gaps and began adding entries for condition classes, auto-configuration import selector resources, condition annotation defaults, and later Spring Boot helper methods. The log ends while another Gradle verification command is still in progress, so Codex did not complete a successful reproduce-fix-verify loop.

The remaining problem is therefore unresolved/partially resolved native-image reachability metadata for Spring Boot's auto-configuration infrastructure, not a generated test bug or unsupported native-image platform feature.

## Why generated support should be preserved

The generated tests exercise meaningful `spring-boot-transaction` behavior: transaction property binding, transaction manager customization, `TransactionTemplate` auto-configuration, rollback behavior, and declarative transaction management through JDK proxies. Two tests already pass, and the four failures point to missing Spring Boot native metadata rather than invalid test expectations. Preserving the generated support keeps useful coverage in place for the next metadata-fix iteration.
