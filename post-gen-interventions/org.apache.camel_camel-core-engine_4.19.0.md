# Post-generation intervention report

Library: org.apache.camel:camel-core-engine:4.19.0
Stage: metadata_fix_failed

## Summary

The native-image test failures are metadata-related. The provided Gradle excerpt shows Camel service discovery failing inside the native executable: the `simple` language cannot be resolved and the `modelyaml-dumper` service is not visible. The Codex metadata-fix log shows Codex partially addressed those gaps by adding Camel language/YAML dependencies and reachability entries for language services, YAML dumper construction, processor factories, type converters, and service descriptors. After that partial fix, five of the six native tests passed, but `dumpRoutesStrategyWritesYamlModelForConfiguredRoutes(Path)` still failed because the YAML dump output was `[]` instead of the expected route/template model.

No generated test was removed because the observed failures are not test bugs or unsupported platform features; they expose missing native-image metadata for Camel's dynamic service/resource lookup paths.

## Failure root causes

- `propertiesComponentAndCoreTypeConvertersAreAvailable()` — metadata-related. Camel initializes property functions and resolves the `simple` language through `DefaultLanguageResolver`; the native image was missing Camel language service/resources and/or reflective construction metadata.
- `contentBasedRouterSendsMessagesToMatchingBranch()` — metadata-related. Route creation resolves Camel expression languages such as `simple`, `header`, and `constant` dynamically; the needed service descriptors and language implementation constructors were not fully reachable in the native image.
- `routeTemplatesMaterializeRunnableRoutesBeforeStartup()` — metadata-related. Route-template materialization triggers the same Camel language/service resolution path, which failed with `NoSuchLanguageException` in the native executable.
- `routesMessagesThroughDefaultCamelContextWithCustomComponent()` — metadata-related. Starting a default Camel context exercises core engine bootstrap and language/property initialization; the failure was caused by missing Camel language metadata rather than the in-memory test component.
- `onExceptionHandlerRecoversFailedExchange()` — metadata-related. Error-handler route creation still relies on Camel dynamic language/service resolution, and the failure matched the missing language metadata pattern.
- `dumpRoutesStrategyWritesYamlModelForConfiguredRoutes(Path)` — metadata-related. The Gradle excerpt first reported `NoSuchServiceException` for `modelyaml-dumper`, showing that `camel-yaml-io` service discovery metadata was missing. Codex added the service descriptor and `LwModelToYAMLDumper` reflection entry, but the final Codex run still produced an empty YAML model (`[]`). That points to additional missing resource metadata for Camel's YAML/model catalog schema resources used by `org.apache.camel.yaml.io.ModelJSonSchemaResolver` and `YamlWriter`, such as `META-INF/org/apache/camel/model/**` entries for route, route-template, processor, and language model JSON schemas.

## Metadata still missing

Codex did not finish the metadata repair. The remaining gap is not reported as a `Missing*RegistrationError`; Camel degrades to an empty YAML dump, so Codex had no direct suggested JSON block to copy. The likely missing metadata is resource inclusion for Camel model catalog schema JSON files under `META-INF/org/apache/camel/model/**` from `camel-core-model` (for example `routeTemplates.json`, `routeTemplate.json`, `routes.json`, `route.json`, `from.json`, `process.json`, and related language/processor schema files). These resources are used by the YAML dumper path after the `modelyaml-dumper` service itself becomes reachable.

## Why the generated support should be preserved

The generated tests exercise real `camel-core-engine` behavior: default `CamelContext` startup, custom component routing, content-based routing, route templates, properties/type conversion, exception handling, and route-model dumping. The Codex log demonstrates that once part of the metadata was supplied, five tests became successful in the native executable. The remaining failing dump test continues to identify a genuine metadata gap in Camel's YAML/model-dump resource discovery, so preserving the generated support keeps useful coverage for the library instead of hiding incomplete reachability metadata.
