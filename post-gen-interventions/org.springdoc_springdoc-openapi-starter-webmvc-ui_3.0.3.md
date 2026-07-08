# Post-generation intervention report

Library: org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3
Stage: metadata_fix_failed

## Summary

All three native test methods fail during shared `@BeforeAll` Spring Boot startup, before any endpoint assertion runs. This is metadata-related: `SpringFactoriesLoader` dynamically loads Spring Boot factory implementations, and Native Image reports `ClassNotFoundException` for those factory classes. The supplied failure identifies `WebMvcWebApplicationTypeDeducer`; after Codex added startup registrations, the remaining run fails on `org.springframework.boot.cloud.CloudFoundryVcapEnvironmentPostProcessor`.

The generated test was not removed. It uses ordinary Spring Boot web application startup and HTTP requests; it does not depend on runtime bytecode generation, class definition/loading by the test, instrumentation, or another unsupported native-image behavior.

## Remaining metadata work

The metadata still needs the reflective factory registration for `org.springframework.boot.cloud.CloudFoundryVcapEnvironmentPostProcessor` (including its constructor), conditioned on a type reached before `SpringFactoriesLoader` performs the lookup. More Spring Boot `spring.factories` registrations may be discovered after that startup phase progresses.

Codex did not resolve the failure because it was iteratively adding one set of Spring Boot factory registrations at a time. Its final verification was still running after adding web-server context-factory entries, so it never reached or repaired the subsequent Cloud Foundry environment-post-processor lookup.

## Why the remaining generated support should be preserved

The test meaningfully exercises the starter's OpenAPI document, Swagger UI, Swagger configuration, legacy redirect, and a Web MVC endpoint. Those are native-image-compatible application paths and justify retaining the generated test and its support once the incomplete Spring Boot factory metadata is finished. No metadata files were modified by this intervention.
