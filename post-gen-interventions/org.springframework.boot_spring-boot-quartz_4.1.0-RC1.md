# Post-generation intervention report

Library: org.springframework.boot:spring-boot-quartz:4.1.0-RC1
Stage: metadata_fix_failed

## Summary

The native test run failed before the Quartz assertions could execute. All six reported JUnit failures share the same root cause: Spring Boot's auto-configuration sorting/metadata-reading path attempted to open the classpath resource `org/springframework/boot/quartz/autoconfigure/QuartzAutoConfiguration.class`, but that `.class` resource was not available in the effective native image.

## Failure classification

This is metadata-related. The failing path is Spring Framework's `SimpleMetadataReaderFactory` / `ClassPathResource` reading annotation metadata from `QuartzAutoConfiguration.class`. The Gradle excerpt shows `FileNotFoundException: class path resource [org/springframework/boot/quartz/autoconfigure/QuartzAutoConfiguration.class] cannot be opened because it does not exist`, which means the native image is missing a resource registration for the auto-configuration class bytes used by Spring's metadata reader.

The individual test methods are not independent native-image limitations:

- `quartzPropertiesExposeDefaultsAndSetters()`
- `autoConfigurationCreatesInMemorySchedulerAndBindsSettings()`
- `autoConfigurationRegistersCalendarBeansWithScheduler()`
- `autoConfigurationRegistersJobDetailsAndTriggersWithScheduler()`
- `autoConfiguredSchedulerExecutesJobsWithSpringBeanPropertyInjection()`
- `schedulerFactoryBeanCustomizerCanModifyCreatedScheduler()`

They fail because the shared `ApplicationContextRunner` / `AutoConfigurations.of(QuartzAutoConfiguration.class)` setup cannot read the auto-configuration class metadata in the native executable.

## Codex metadata-fix outcome

The Codex log shows that the metadata-fix loop identified and addressed earlier native closed-world gaps, including `org.springframework.transaction.PlatformTransactionManager` being treated as absent by `@ConditionalOnClass` and Quartz runtime lookup of `org.springframework.scheduling.quartz.ResourceLoaderClassLoadHelper`. After that, Codex started cleanup/final verification but the logged final verification did not complete with a successful terminal result. The remaining Gradle failure is the effective absence of the `QuartzAutoConfiguration.class` resource in the native image, not a generated-test dependency on unsupported behavior.

## Intervention decision

No generated tests were removed. The failure should be fixed by reachability metadata, not by deleting coverage. I did not modify metadata files.

## Why the remaining generated support should be preserved

The generated support exercises normal Spring Boot Quartz functionality: binding `QuartzProperties`, creating an in-memory `Scheduler`, registering `JobDetail`, `Trigger`, and `Calendar` beans, invoking a `SchedulerFactoryBeanCustomizer`, and verifying Spring bean injection into a Quartz job. These paths represent the intended library behavior under native image and do not rely on unsupported runtime bytecode generation, class redefinition, Java-agent self-attach, instrumentation, or Byte Buddy-style mocking. Preserving the tests keeps coverage for the Spring Boot Quartz auto-configuration metadata that native-image users need.
