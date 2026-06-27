# Post-generation intervention report

Library: org.springframework.cloud:spring-cloud-stream-binder-kafka:5.0.1
Stage: metadata_fix_failed

## Summary

The failures are metadata-related native-image runtime failures, not unsupported native-image behavior that should be handled by deleting generated tests. I did not remove any generated tests and did not modify metadata files.

The generated test suite exercises both direct Kafka binder APIs and Spring Boot/Spring Cloud startup paths. Codex made progress through several missing registrations, but the final metadata-fix log stops with another Spring factory class that is still absent from the native image.

## Failure root causes

1. `topicProvisionerAppliesPerBindingTopicConfiguration()`
   - Failure: `EmbeddedKafkaKraftBroker` failed to start because Kafka tried to load `org.apache.kafka.server.share.persister.DefaultStatePersister` and the native image raised `ClassNotFoundException`.
   - Classification: metadata-related. This is a Kafka server class selected by configuration during embedded broker startup. Codex later advanced past this class-loading failure, and the same test became successful in the metadata-fix log, which indicates the failure was caused by incomplete reachability metadata rather than an invalid test.

2. `expressionConverterHeadersAndBatchFailureHelpersUseKafkaSpecificTypes()`
   - Failure: SpEL could not resolve `payload` on `org.springframework.messaging.support.GenericMessage` while `KafkaExpressionEvaluatingInterceptor` evaluated the expression.
   - Classification: metadata-related. The Codex log explicitly identifies this as missing reflective access to `GenericMessage` getters used by the interceptor/SpEL path. After Codex added the getter metadata, this test became successful.

3. `embeddedKafkaBinderProcessesFunctionAndProvisionerCreatesTopics()`
   - Failure: the first reported failure was the same embedded Kafka startup `ClassNotFoundException` for `DefaultStatePersister`. After Codex fixed successive metadata gaps, this test continued to expose Spring factory-loading failures.
   - Remaining failure: the final Codex log shows `ClassNotFoundException: org.springframework.cloud.stream.binder.kafka.common.KafkaBinderEnvironmentPostProcessor` while `SpringFactoriesLoader` instantiates an `org.springframework.boot.EnvironmentPostProcessor`.
   - Classification: metadata-related. The missing class is present in `org.springframework.cloud:spring-cloud-stream-binder-kafka-core:5.0.1` and has a public no-arg constructor, but there is no existing metadata artifact for `spring-cloud-stream-binder-kafka-core` in the repository. Codex located the owning jar and constructor but stopped before adding the required owning metadata entry/artifact.

## Metadata still missing

The remaining known missing metadata is a Spring factories constructor registration for:

```text
org.springframework.cloud.stream.binder.kafka.common.KafkaBinderEnvironmentPostProcessor
```

It should be owned by `org.springframework.cloud:spring-cloud-stream-binder-kafka-core:5.0.1`, because that jar contains the class. The access is triggered by Spring Boot's `SpringFactoriesLoader` while loading `EnvironmentPostProcessor` implementations during the generated Spring application test.

## Why the remaining generated support should be preserved

The passing generated tests cover valuable native-image support for Kafka binder configuration objects, binder header mapping, Kafka-specific null conversion, batch-failure helper behavior, runtime hints, binding property mappings, binder accessors, and topic-provisioner behavior. The remaining failure is another incomplete metadata registration in the Spring/Kafka startup chain, not a scaffold-only or unsupported-platform test. Keeping the generated support preserves coverage for the binder APIs that already run successfully and retains the integration test that revealed the still-missing metadata.
