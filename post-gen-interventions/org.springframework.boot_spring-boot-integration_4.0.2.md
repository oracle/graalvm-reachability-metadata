# Post-generation intervention report

Library: org.springframework.boot:spring-boot-integration:4.0.2
Stage: metadata_fix_failed

## Summary

The native test run failed during Spring Boot application-context startup, before the generated Spring Integration assertions could execute. Six generated tests failed with the same metadata-related error: `SpringFactoriesLoader` attempted to instantiate `org.springframework.boot.autoconfigure.SharedMetadataReaderFactoryContextInitializer`, but that class was not available to the native image and `Class.forName` raised `ClassNotFoundException`.

This is a reachability metadata failure, not a test bug, native-image limitation, or unsupported platform feature. No generated tests were removed.

## Root cause by failing test

All six failures share the same root cause: Spring Boot factory loading/auto-configuration infrastructure needs additional reachability metadata.

- `autoConfigurationProvidesTaskSchedulerForIntegrationInfrastructure()` failed because `org.springframework.boot.autoconfigure.SharedMetadataReaderFactoryContextInitializer` could not be loaded by `SpringFactoriesLoader` in the native image.
- `defaultPollerMetadataUsesConfiguredTriggerTimeoutAndOrderedCustomizers()` failed for the same missing Spring Boot factory initializer class.
- `autoConfigurationBindsBootPropertiesAndMapsThemToSpringIntegrationGlobalProperties()` failed for the same missing Spring Boot factory initializer class.
- `autoConfigurationScansMessagingGateways()` failed for the same missing Spring Boot factory initializer class.
- `integrationFlowBeanIsDiscoveredAndProcessesMessages()` failed for the same missing Spring Boot factory initializer class.
- The remaining generated context-startup test in the run follows the same metadata path; Codex later exposed another metadata condition issue for `org.springframework.boot.autoconfigure.condition.OnClassCondition` whose reflection entry existed but was inactive because it was conditioned on `org.springframework.core.io.support.SpringFactoriesLoader` being reached too late for the narrower `ApplicationContextRunner`/`AutoConfigurations.of(...)` path.

## Codex metadata-fix outcome

The Codex log shows an incomplete metadata-fix loop. Codex repeatedly identified the failures as Spring Boot auto-configuration/import metadata problems, attempted to add or adjust metadata for Boot internals, and then narrowed the generated test harness away from global `@EnableAutoConfiguration` toward direct `IntegrationAutoConfiguration` loading. The final logged state still showed a metadata miss: reflective construction of `OnClassCondition` required metadata with a less restrictive condition, or an earlier-satisfied condition. The last Gradle rerun was started but the log does not show a successful completion.

Metadata still missing or incorrect includes at least:

- reachability support for loading/instantiating `org.springframework.boot.autoconfigure.SharedMetadataReaderFactoryContextInitializer` from Spring Boot factories; and
- corrected reflection metadata for `org.springframework.boot.autoconfigure.condition.OnClassCondition`, because the existing metadata was inactive under the observed native-image execution path.

## Why the generated support should be preserved

The generated support exercises valid `spring-boot-integration` behavior: binding `IntegrationProperties`, mapping Boot integration settings into Spring Integration global properties, default poller customization, task-scheduler provisioning, integration-flow message processing, and messaging-gateway scanning. The failures occur before those scenarios can run because Spring Boot infrastructure metadata is incomplete. Preserving the tests is useful because they are the regression coverage needed to verify the remaining reachability metadata once it is completed.
