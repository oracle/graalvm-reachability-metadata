# Post-generation intervention report

Library: com.baomidou:mybatis-plus-boot-starter:3.5.3.1
Stage: metadata_fix_failed

## Summary

The generated native test support should be preserved. The failing native-image run is blocked by unresolved reachability metadata for Spring Boot/Spring Framework configuration processing and MyBatis logging, not by a test bug or an unsupported native-image platform feature. No generated tests were removed.

## Failure root causes

- `autoConfigurationCreatesTemplateAndMybatisPlusMapper()`
  - Metadata-related.
  - The provided Gradle excerpt shows Spring failing to instantiate `org.springframework.context.annotation.ConfigurationClassPostProcessor` reflectively: `NoSuchMethodException: org.springframework.context.annotation.ConfigurationClassPostProcessor.<init>()`. That constructor exists on the classpath, so this is a missing reflection registration in the native image rather than invalid test code.
  - In the Codex metadata-fix log, after partial fixes, this same Spring application-context path advanced to `ClassNotFoundException: org.springframework.boot.autoconfigure.condition.OnClassCondition` during `ConditionEvaluator` / auto-configuration import processing. That is another native reachability gap: Spring reads condition metadata and then loads the condition class at runtime, but the class was not reachable in the image.

- `interceptorBeanIsAppliedToPaginatedMapperQueries()`
  - Metadata-related.
  - It exercises the same Spring Boot auto-configuration path as the first test, and the failure is the same missing native reachability for Spring configuration processing (`ConfigurationClassPostProcessor` in the excerpt; later `OnClassCondition` in the Codex rerun).

- `sqlSessionFactoryBeanCustomizerInstallsCustomObjectFactory()`
  - Metadata-related.
  - It also starts a Spring Boot application context with the generated MyBatis Plus auto-configuration support. The failure is the same unresolved Spring configuration/condition metadata path, not the customizer test logic.

- `propertiesResolveMapperLocationsAndCanBeCustomized()`
  - Metadata-related.
  - The failure is `org.apache.ibatis.logging.LogException` caused by a `NullPointerException` in `org.apache.ibatis.logging.LogFactory.getLog`. MyBatis initializes its logging implementation by reflectively locating `String` constructors on logging adapters such as `org.apache.ibatis.logging.slf4j.Slf4jImpl`. In the native image, those reflective constructor lookups are not fully reachable, leaving `LogFactory` without a selected constructor and causing the NPE when `DefaultSqlInjector` is initialized.

## What metadata is still missing

Codex only partially fixed the native reachability surface. The remaining gaps include:

- Spring Boot condition/configuration reachability, at least `org.springframework.boot.autoconfigure.condition.OnClassCondition` being available to `Class.forName` during auto-configuration condition evaluation.
- Spring configuration processor reflection for `org.springframework.context.annotation.ConfigurationClassPostProcessor.<init>()` in the failing excerpt, or equivalent corrected conditional metadata that is active for this test path.
- MyBatis logging reflection, at least constructor reachability for the logging implementation selected by MyBatis, such as `org.apache.ibatis.logging.slf4j.Slf4jImpl(String)`, and potentially the fallback MyBatis logging adapters.

Codex did not finish resolving these because the failures moved through several metadata layers: first Spring factory/resource discovery, then auto-configuration class resources, then Boot condition class reachability, while the MyBatis logging constructor failure remained. The log ends while Codex is still investigating the `OnClassCondition` blocker and before it can add and verify the remaining metadata entries.

## Why the generated support should be preserved

The generated tests passed on the JVM and exercise meaningful MyBatis Plus boot-starter behavior: Spring Boot auto-configuration, mapper wiring, pagination interceptor integration, `SqlSessionFactoryBeanCustomizer`, properties/resource resolution, encryption property processing, and DDL runner delegation. The two passing native tests already cover useful native-compatible library functionality, and the failing tests expose real missing reachability metadata. Removing them would hide actionable metadata gaps rather than eliminating invalid or unsupported test scenarios.
