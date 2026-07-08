# Post-generation intervention

Library: org.springframework.cloud:spring-cloud-stream-binder-kafka:5.0.1

Stage: `metadata_fix_failed`

## Failure summary

All three generated tests fail while starting the embedded Kafka broker or loading Spring factory initializers in the native image. These are metadata-related failures, not unsupported native-image behavior:

- `DefaultStatePersister` is loaded reflectively by Kafka's `BrokerServer`; the Codex run added its constructor registration and the subsequent run showed the broker could start.
- `PollableSourceInitializer` was subsequently loaded and constructed by `SpringFactoriesLoader`; Codex corrected its condition to the active loader and added its zero-argument constructor.
- The remaining failure is `ClassNotFoundException` for `org.springframework.cloud.function.context.config.ContextFunctionCatalogInitializer`, loaded through the same `SpringFactoriesLoader` factory path. It still needs a reflection entry, conditioned on `org.springframework.core.io.support.SpringFactoriesLoader`, with its zero-argument constructor.

Codex identified the remaining class and constructor but could not write the registration: its patch attempts ended with malformed-patch errors. No generated tests were removed, and no metadata files were changed by this intervention.

## Preserve generated support

The three tests exercise distinct Kafka binder behavior: function binding message delivery, rebalance-listener assignment, and dynamic destination publishing. Their failures occur before those assertions because required runtime classes are absent from the native image. They do not depend on runtime bytecode generation, class redefinition, self-attach, instrumentation, substitutions, or Byte Buddy mocking; therefore the generated support should be preserved while the remaining Spring factory reflection metadata is completed.
