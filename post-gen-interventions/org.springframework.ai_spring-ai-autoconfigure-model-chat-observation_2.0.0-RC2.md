# Post-generation intervention report

Library: org.springframework.ai:spring-ai-autoconfigure-model-chat-observation:2.0.0-RC2
Stage: metadata_fix_failed

## Summary

The native test executable starts, but all five generated JUnit tests fail while Spring Boot's `ApplicationContextRunner` is sorting/reading `ChatObservationAutoConfiguration`. Spring tries to open `org/springframework/ai/model/chat/observation/autoconfigure/ChatObservationAutoConfiguration.class` as a classpath resource and the native image does not contain that resource, producing `FileNotFoundException` wrapped by `IllegalStateException: Unable to read meta-data for class org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationAutoConfiguration`.

## Failure classification

This is metadata-related. The failing class exists in the Spring AI artifact, but native-image did not include the class-file resource and related Spring Boot runtime metadata needed by `SimpleMetadataReader`/`AutoConfigurationSorter`. The failures are not caused by unsupported native-image behavior such as runtime bytecode generation, class redefinition, Java agent self-attach, instrumentation, or Byte Buddy-backed mocking.

All failed test methods share the same root cause:

- `meterRegistryEnablesMeterHandlerAndDefaultPropertiesRemainDisabled()` — missing native-image resource metadata for `ChatObservationAutoConfiguration.class`.
- `loggingPropertiesWithoutTracerDoNotEnableTracingAwareHandlers()` — same missing auto-configuration class resource.
- `loggingPropertiesAndTracerEnableTracingAwareHandlers()` — same missing auto-configuration class resource.
- `userProvidedPromptHandlerMakesPromptAutoConfigurationBackOff()` — same missing auto-configuration class resource.
- The fifth test in the run fails through the same Spring Boot auto-configuration metadata path.

Because the failure is metadata-related, no generated tests were removed and no metadata files were modified by this intervention.

## Codex metadata-fix status

The Codex log shows that the metadata fix loop correctly recognized the initial failure as a missing native-image classpath resource rather than a test bug. It began adding Spring Boot-style resource, reflection, annotation proxy, and class-loading metadata, then continued iterating through additional missing Spring bootstrap accesses (`Class.forName` for the auto-configuration class, annotation proxies, Jakarta injection annotations, Spring infrastructure interfaces, and the `ChatModel` condition probe). The log ends while another trace build is still in progress, so Codex did not finish verification and did not converge on the complete required metadata set.

The remaining problem is therefore incomplete native-image metadata for Spring Boot auto-configuration analysis around this Spring AI module, not an unsupported test path.

## Why preserve the remaining generated support

The generated tests exercise real, library-specific behavior of `spring-ai-autoconfigure-model-chat-observation`: registration of chat observation properties, meter/logging/error handlers, tracer-aware behavior, and user bean back-off. These are valid native-image reachability scenarios for the artifact's Spring Boot auto-configuration. Preserving the tests is useful because they expose the exact metadata gap that still needs to be completed, and deleting them would hide missing support for normal Spring AI auto-configuration usage rather than removing an unsupported native-image feature.
