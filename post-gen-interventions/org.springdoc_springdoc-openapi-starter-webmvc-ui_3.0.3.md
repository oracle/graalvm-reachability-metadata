# Post-generation intervention report

Library: org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3
Stage: metadata_fix_failed

## Summary

The native test binary builds, but all three generated native tests fail at runtime before any HTTP assertions are reached. The Gradle failure excerpt shows the same startup error for each test: Spring's `SpringFactoriesLoader` tries to instantiate `org.springframework.boot.webmvc.WebMvcWebApplicationTypeDeducer` as a `org.springframework.boot.WebApplicationType$Deducer`, but the class is absent from the native image and `Class.forName` throws `ClassNotFoundException`.

## Failure classification

These failures are metadata-related. No generated test was removed.

- `generatedOpenApiAndSwaggerUiAreServedByWebMvcApplication()` fails during Spring Boot startup because `org.springframework.boot.webmvc.WebMvcWebApplicationTypeDeducer` is not reachable in the image.
- `groupedOpenApiDefinitionsAreExposedSeparatelyAndAddedToSwaggerUiConfig()` fails for the same startup metadata gap before the grouped OpenAPI endpoints are exercised.
- `customApiDocsAndSwaggerUiPathsAreHonored()` fails for the same startup metadata gap before the custom springdoc paths are exercised.

The Codex metadata-fix log confirms this is a chain of missing native-image registrations/resources rather than unsupported native-image behavior. Codex first moved past the `WebMvcWebApplicationTypeDeducer` failure, then repeatedly exposed additional Spring Boot and springdoc metadata gaps, including `SharedMetadataReaderFactoryContextInitializer`, `EventPublishingRunListener`, web-server `ApplicationContextFactory` implementations, parser-instantiated Spring filters, `AutoConfigurationPackages$Registrar.class`, `org.springdoc.scalar.ScalarDisableAutoConfiguration`, springdoc auto-configuration `.class` resources, and finally Spring Boot auto-configuration `.class` resources such as `MessageSourceAutoConfiguration.class`. Codex did not finish the full metadata closure for Spring Boot 4 plus springdoc startup.

## Remaining missing metadata

The remaining support needs additional reachability metadata for Spring Boot/springdoc startup paths loaded through `SpringFactoriesLoader`, Spring's configuration parser, and deferred auto-configuration sorting. The immediate excerpted failure requires making `org.springframework.boot.webmvc.WebMvcWebApplicationTypeDeducer` reachable with the constructor/access needed by `SpringFactoriesLoader`; the log shows subsequent fixes must also cover the later factory classes and `.class` resources encountered during auto-configuration parsing.

## Why preserve the generated support

The generated tests exercise meaningful springdoc behavior: serving generated OpenAPI JSON, Swagger UI assets and initializer configuration, grouped OpenAPI documents, and customized `springdoc.api-docs.path` / `springdoc.swagger-ui.path` settings from an embedded WebMVC application. The failures occur before those assertions because framework startup metadata is incomplete. Preserving the tests keeps useful coverage for the library once the missing metadata closure is completed.
