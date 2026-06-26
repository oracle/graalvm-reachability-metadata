# Post-generation intervention report

Library: org.springframework.boot:spring-boot-webclient:4.1.0-RC1
Stage: metadata_fix_failed

## Summary

The native test run failed in generated Spring Boot WebClient tests. The Gradle excerpt shows all seven tests failing while Spring Boot tries to read annotation metadata for `org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration` and cannot open the corresponding classpath resource. This is a metadata/resource-registration problem, not unsupported native-image behavior.

The Codex metadata-fix log shows that Codex partially repaired earlier native-image gaps: it added class-resource, reflection, and proxy-related coverage and got six of the seven tests passing in a later run. The remaining failure in the log is `reactiveHttpServiceClientAutoConfigurationAppliesPropertiesAndCustomizers()`, where the generated HTTP service client is created but the configured `base-url` is not applied, producing `java.lang.IllegalArgumentException: URI with undefined scheme`.

## Root cause classification

- `autoConfigurationsAreAdvertisedForSpringBootDiscovery()` — metadata-related. Spring Boot discovery requires `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and the referenced auto-configuration class resources in the native image.
- `webClientAutoConfigurationCreatesPrototypeBuilderWithConnectorAndCustomizers()` — metadata-related in the provided Gradle excerpt because the shared `ApplicationContextRunner` auto-configuration path cannot read `WebClientAutoConfiguration.class` as a resource.
- `webClientCodecCustomizerAppliesBootCodecCustomizersToClientBuilder()` — no unsupported native behavior was identified; Codex later got this support passing.
- `observationWebClientCustomizerRecordsClientObservationsWithConfiguredConvention()` — metadata-related in the provided Gradle excerpt through Spring Boot auto-configuration metadata/resource loading; Codex later got this support passing.
- `webClientObservationAutoConfigurationCreatesCustomizerWhenObservationRegistryIsAvailable()` — metadata-related; Spring Boot cannot inspect the WebClient auto-configuration class resource in the failing native image.
- `reactiveHttpServiceClientAutoConfigurationAppliesPropertiesAndCustomizers()` — still unresolved after Codex's partial metadata repair. The log shows earlier missing metadata for `ImportHttpServiceRegistrar`, the HTTP service proxy, and related Spring HTTP-service classes was addressed, but the final native run still did not apply `spring.http.serviceclient.catalog.base-url`. This points to remaining Spring Boot HTTP-service-client metadata around native resource/reflection/conditional processing or property binding, not to bytecode generation, instrumentation, inline/static mocking, self-attach, or another unsupported native-image feature.
- `webClientSslAppliesNamedSslBundleToWebClientBuilder()` — metadata-related in the provided Gradle excerpt because context startup reaches Spring Boot auto-configuration metadata reading and fails on the missing `WebClientAutoConfiguration.class` resource.

## Metadata still missing or incomplete

The immediate Gradle excerpt indicates missing native-image resource metadata for Spring Boot auto-configuration class resources, specifically `org/springframework/boot/webclient/autoconfigure/WebClientAutoConfiguration.class`. Codex's later attempts also showed missing resource coverage for `org/springframework/web/service/registry/ImportHttpServiceRegistrar.class` and missing proxy/reflection metadata for the generated HTTP service client path before those were partially addressed.

Codex did not finish resolving the final HTTP-service-client property binding/configurer path. After the explicit proxy failure was gone, the remaining `URI with undefined scheme` failure means the `CatalogClient` request was issued against `/items` without the configured server `base-url`. The log ended while Codex was still investigating whether Spring's `@ConditionalOnBean(HttpServiceProxyRegistry.class)` and `HttpServiceClientProperties` binding needed additional native metadata.

## Intervention decision

No generated tests were removed. The observed failures are metadata-related or caused by incomplete metadata repair, and none depend on unsupported native-image behavior such as runtime bytecode generation, class redefinition, Java-agent self-attach, instrumentation, or Byte Buddy-backed mocking.

## Why the remaining generated support should be preserved

The generated tests exercise real Spring Boot WebClient functionality: auto-configuration discovery, prototype `WebClient.Builder` creation, codec customization, Micrometer observation customization, SSL bundle application, and HTTP service client integration. Codex's log demonstrates that most of this support can run successfully in native image once the missing metadata is supplied. Preserving the tests keeps coverage for valid reachability paths and documents the specific remaining metadata gap instead of deleting useful generated support.
