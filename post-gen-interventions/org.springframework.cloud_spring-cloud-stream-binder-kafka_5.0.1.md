# Post-generation Intervention Report

Library: org.springframework.cloud:spring-cloud-stream-binder-kafka:5.0.1
Stage: metadata_fix_failed

## Summary

The remaining failure is metadata-related. `nativeTest` built the native image successfully, but the native JUnit run failed in `binderMapsExternalConfigurationToKafkaProperties()` with:

`org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively invoke constructor 'public org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties()'.`

The generated metadata contains a reflection entry for `KafkaBindingProperties`, but that entry is inactive at the point Spring Boot's `JavaBeanBinder` tries to instantiate the class reflectively.

## Root cause

The failed test exercises Spring Boot property binding into Spring Cloud Stream Kafka binder configuration objects. That is a valid native-image metadata scenario, not a native-image platform limitation or a test bug.

The missing/incorrect metadata is the reflective no-argument constructor access for:

- `org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties`

The failure output shows that metadata for this access was found, but the runtime condition was not satisfied:

```json
{
  "typeReached": "org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties"
}
```

This is effectively self-conditioned for the first reflective construction of `KafkaBindingProperties`: the type has not been reached before Spring Boot reflectively invokes the constructor, so the constructor registration remains inactive. The metadata therefore needs an appropriate condition that is reached before this reflective bind path, or an otherwise valid registration that is active for this access. I did not change metadata files, per instruction.

## Codex metadata-fix outcome

The Codex metadata-fix log shows that Codex initially tried Spring Boot `Binder`-based conditions, but `checkMetadataFiles` rejected those because this artifact's allowed package scope only permits `typeReached` classes under `org.springframework.cloud.stream.binder.kafka`. Codex then replaced those conditions with binder-kafka package types so validation passed. That made the metadata file syntactically/structurally acceptable, but it left at least the `KafkaBindingProperties` constructor entry conditioned on the same type that is being reflectively instantiated, so the runtime access is still not active in the failing test path.

## Action taken

No generated test was removed. The observed failure is metadata-related and points to incomplete/incorrect conditional reflection metadata rather than unsupported runtime behavior.

## Why the remaining generated support should be preserved

The generated test suite has broad useful coverage for this library: nine native tests passed, covering Kafka binder configuration objects, header mapping, tombstone conversion, provisioning utilities, binding utilities, topic/DLQ helpers, and SpEL-based message key handling. The one failing test is also valuable because it exposes a real Spring Boot configuration-binding metadata gap for `KafkaBindingProperties`. Removing it would hide an actionable reachability metadata issue instead of documenting the remaining metadata work needed.
