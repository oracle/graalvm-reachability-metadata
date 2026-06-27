# Post-generation intervention report

Library: org.springframework.ai:spring-ai-autoconfigure-model-image-observation:2.0.0-RC2

Stage: `metadata_fix_failed`

## Summary

The generated native tests still fail after the Codex metadata-fix attempt. The failures are metadata-related, so no generated tests or generated test-only files were removed.

The Gradle failure shows Spring Boot trying to sort/load `ImageObservationAutoConfiguration` and failing to read its class metadata in the native image:

```text
java.lang.IllegalStateException: Unable to read meta-data for class org.springframework.ai.model.image.observation.autoconfigure.ImageObservationAutoConfiguration
Caused by: java.io.FileNotFoundException: class path resource [org/springframework/ai/model/image/observation/autoconfigure/ImageObservationAutoConfiguration.class] cannot be opened because it does not exist
```

This affects the generated tests that start an `ApplicationContextRunner` with `AutoConfigurations.of(ImageObservationAutoConfiguration.class)`, because Spring Boot reads auto-configuration class bytes as resources when evaluating ordering and conditions.

## Root cause

The remaining failure is missing native-image metadata, not a test bug or unsupported platform feature.

Codex made progress through several missing metadata iterations, including Spring/Spring AI bootstrap reflection and service/resource entries such as:

- `META-INF/services/org.apache.commons.logging.LogFactory`
- `commons-logging.properties`
- `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`
- `jakarta.inject.Provider`
- `jakarta.annotation.PostConstruct`, `jakarta.annotation.PreDestroy`, and `jakarta.annotation.Resource`
- `jakarta.persistence.EntityManagerFactory`
- `jakarta.inject.Qualifier`, `jakarta.inject.Named`, and `jakarta.inject.Inject`
- `org.springframework.ai.image.ImageModel` and related image observation handler/context types

However, the Codex log ends while another trace run is still in progress, before Codex completed the reproduce/fix/verify loop or ran a final successful `./gradlew test -Pcoordinates=org.springframework.ai:spring-ai-autoconfigure-model-image-observation:2.0.0-RC2` verification.

The metadata is still incomplete. At minimum, the native image still cannot access the class resource:

```text
org/springframework/ai/model/image/observation/autoconfigure/ImageObservationAutoConfiguration.class
```

That resource is required by Spring Boot's metadata reader. The later trace outputs also indicate the fix loop had not fully converged and was still uncovering Spring Boot bootstrap reflection, including `org.springframework.boot.context.properties.EnableConfigurationPropertiesRegistrar`.

## Action taken

No generated tests were removed, and no metadata files were modified during this intervention.

## Why the generated support should be preserved

The generated test support exercises real behavior of this artifact: Spring Boot auto-configuration loading, `ImageObservationProperties` binding, prompt logging handler registration, tracer-aware handler selection, and conditional backoff behavior. These tests pass on the JVM and expose legitimate native-image metadata requirements for the Spring AI image observation auto-configuration path.

Because the failures are caused by missing native-image resource/reflection metadata rather than invalid test expectations, preserving the generated tests is important: they remain the coverage needed to finish the metadata fix in a later iteration.
