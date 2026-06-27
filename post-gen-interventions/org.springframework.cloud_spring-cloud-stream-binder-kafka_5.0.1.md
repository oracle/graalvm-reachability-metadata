# Post-generation intervention report

Library: org.springframework.cloud:spring-cloud-stream-binder-kafka:5.0.1
Stage: metadata_fix_failed

## Summary

The final native test run failed before executing any test methods because the generated
`@BeforeAll` setup started an embedded Kafka KRaft broker. Kafka broker startup attempted to load
`org.apache.kafka.server.share.persister.DefaultStatePersister` dynamically through Kafka's runtime
class-loading path and the native executable reported `ClassNotFoundException`, causing
`EmbeddedKafkaKraftBroker.start()` to fail.

## Root cause and intervention

This failure is not a Spring Cloud Stream binder metadata gap to preserve with more generated
metadata. The failing generated coverage depended on an embedded Kafka server path that performs
runtime class loading during broker startup. Per the post-generation rules, runtime class loading is
unsupported native-image behavior for generated tests, so the Kafka-cluster-dependent tests were
removed instead of adding metadata.

Removed generated coverage:

- `streamBridgeProducesRecordsThroughKafkaBinder`
- `functionConsumerReceivesRecordsThroughKafkaBinder`
- `failingFunctionConsumerRoutesRecordsToKafkaDeadLetterTopic`
- the embedded Kafka setup/helper code and the `spring-kafka-test` test dependency that were only
  needed by those tests

The Codex metadata-fix log shows earlier real metadata gaps were found and partially addressed,
including Spring Boot `spring.factories` classes such as
`org.springframework.boot.context.event.EventPublishingRunListener` and Spring Messaging
`GenericMessage#getPayload()`/`getHeaders()` access for the Kafka expression interceptor path. The
remaining Gradle failure, however, was the embedded Kafka broker's dynamic class-loading path, not
another missing binder metadata entry.

## Why the remaining support should be preserved

The remaining generated tests still exercise useful Spring Cloud Stream Kafka binder support without
starting a Kafka broker or relying on unsupported runtime class loading:

- `KafkaNullConverter` tombstone conversion
- `KafkaExpressionEvaluatingInterceptor` message-key evaluation
- `KafkaBinderRuntimeHints` and Kafka extended binding mapping exposure

These tests keep lightweight binder API, expression, and AOT/runtime-hint coverage that is relevant
to native-image reachability while avoiding the unsupported embedded-broker path that caused the
post-generation failure.
