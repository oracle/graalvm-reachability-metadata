# Post-generation intervention report

Library: org.springframework.boot:spring-boot-http-codec:4.0.2
Stage: metadata_fix_failed

## Summary

The generated native test build completed, but `nativeTest` failed at runtime. Four generated tests that register `CodecsAutoConfiguration` failed while Spring was evaluating configuration conditions. The direct Gradle failure is metadata-related: the native image could not resolve `org.springframework.boot.autoconfigure.condition.OnClassCondition` when `ConditionEvaluator` attempted to instantiate the condition referenced by Boot's `@ConditionalOnClass` metadata.

No generated tests were removed because the failures are caused by missing or incomplete reachability metadata rather than a test bug, unsupported platform feature, or native-image limitation.

## Failure root cause by test

- `autoConfigurationBacksOffJacksonCustomizerWhenJsonMapperBeanIsAbsent()` failed with `IllegalArgumentException: Could not find class [org.springframework.boot.autoconfigure.condition.OnClassCondition]`, caused by `ClassNotFoundException` during Spring condition evaluation. This indicates missing reflection/reachability metadata for Boot's condition implementation path.
- `userCodecCustomizerCanOverrideAutoConfiguredDefaultCodecSettings()` failed for the same `OnClassCondition` resolution failure while processing `CodecsAutoConfiguration`.
- `autoConfigurationCreatesOrderedCustomizersFromPropertiesAndJsonMapper()` failed for the same `OnClassCondition` resolution failure while processing `CodecsAutoConfiguration`.
- `jacksonCodecCustomizerUsesApplicationJsonMapperForJsonPayloads()` failed for the same `OnClassCondition` resolution failure while processing `CodecsAutoConfiguration`.

The remaining generated test, `httpCodecsPropertiesExposeDefaultsAndUpdates()`, passed because it exercises `HttpCodecsProperties` directly and does not drive Spring's auto-configuration condition evaluation.

## Metadata still missing or incomplete

Codex started addressing the explicit missing class by adding metadata for Boot condition classes such as `OnClassCondition`, `OnBeanCondition`, and `OnPropertyCondition`. After those attempts, the failure changed in the Codex log from the explicit `OnClassCondition` class-resolution error to Spring context failures such as missing `HttpCodecsProperties` and `jacksonCodecCustomizer` beans. That later failure means `CodecsAutoConfiguration` is present but is still not being fully recognized and processed as a configuration candidate in the native image.

The unresolved metadata therefore appears to be in Spring Boot 4's auto-configuration annotation processing path, including the `@AutoConfiguration` meta-annotation chain, condition annotation/proxy synthesis, and nested `CodecsAutoConfiguration` configuration classes and bean methods. Codex could not finish the fix because the later failures did not produce a concrete `Missing*RegistrationError` with a suggested JSON entry; instead, Spring silently skipped or under-processed the configuration, leaving no direct metadata hint beyond the missing beans.

## Why the generated support should be preserved

The tests are meaningful coverage for `spring-boot-http-codec`: they validate `HttpCodecsProperties`, property binding, codec customizer ordering, Jackson mapper integration, conditional Jackson backoff, and user customizer override behavior. The failures expose real missing native-image reachability support for Spring Boot's HTTP codec auto-configuration path. Preserving the generated test project and tests keeps that coverage available for a future metadata fix; removing them would hide a metadata gap rather than remove invalid generated support.
