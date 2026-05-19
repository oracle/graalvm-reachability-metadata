# Post-generation intervention report

Library: org.springframework.boot:spring-boot-restclient:4.0.2

Stage: `metadata_fix_failed`

## Summary

The failures are metadata-related. No generated tests were removed.

The Gradle failure excerpt shows all 13 native tests failing during Spring Boot auto-configuration sorting because the native image cannot open the classpath resource:

`org/springframework/boot/restclient/autoconfigure/RestClientAutoConfiguration.class`

Spring Boot's `AutoConfigurationSorter` reads `.class` resources through `SimpleMetadataReader` to inspect auto-configuration annotations. In the native image those class resources were not included, so every test that constructs or initializes the Spring Boot test class fails before the individual feature assertions can run.

The Codex metadata-fix log confirms this is a reachability metadata problem. Codex identified missing resource metadata for Spring Boot auto-configuration class resources, then iterated through additional reflection/proxy/resource gaps. After partial fixes, the failure narrowed from 13 failures to one remaining native-only failure in `httpServiceClientAutoConfigurationBuildsRestClientBackedServiceProxy()`: `java.lang.IllegalArgumentException: URI with undefined scheme`. Codex suspected the remaining issue was still in Spring Boot HTTP service-client property binding or HTTP service group configuration metadata, but the log ends while starting another `runNativeTraceImage` pass and does not reach a final metadata fix.

## Root cause by failing test group

- Initial failures in all generated tests: missing native resource metadata for Spring Boot auto-configuration `.class` resources, specifically `RestClientAutoConfiguration.class`, required by `AutoConfigurationSorter` / `SimpleMetadataReader`.
- Remaining Codex-era failure in `httpServiceClientAutoConfigurationBuildsRestClientBackedServiceProxy()`: still metadata-related. The HTTP service proxy is created, but `spring.http.serviceclient.greeting.base-url` is not applied in native mode, causing a relative URI to reach the JDK HTTP client. The likely remaining gap is metadata for Spring Boot HTTP service-client property binding and/or service group configurer classes. Codex could not resolve it because it ran out while continuing the trace-driven metadata walk.

## Why the generated support should be preserved

The generated tests exercise real Spring Boot REST client behavior: auto-configuration discovery, `RestClient` and `RestTemplate` builder/configurer paths, SSL integration, observations, message converters, root URI handling, redirects, and HTTP service proxy support. The failures point to missing native-image metadata for those real code paths rather than unsupported platform behavior or a test-only bug.

Preserving the generated support is useful because the suite already exposes concrete reachability gaps and, according to the Codex log, most tests pass after partial metadata repair. Removing the tests would hide valid Spring Boot native-image metadata requirements instead of documenting the remaining missing metadata.
