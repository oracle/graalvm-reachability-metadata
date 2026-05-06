# Post-generation intervention report

Library: com.typesafe.akka:akka-slf4j_2.13:2.6.21
Stage: metadata_fix_failed

## Summary

The native test run failed in generated Akka SLF4J tests that start an `ActorSystem`. The failures are metadata-related, not test bugs or unsupported native-image platform limitations, so no generated tests were removed.

The Gradle excerpt shows four failing tests:

- `configuredSlf4jLoggerConsumesAkkaLogEventsWithMarkersMdcAndCauses()`
- `slf4jLoggerExposesMdcAttributeNamesAndUtcTimestampFormatting()`
- `slf4jLoggerAcknowledgesInitializationRequests()`
- `slf4jLoggingFilterCombinesEventStreamLevelWithBackendAndMarkerChecks()`

All four failures share the same root path: Akka actor-system bootstrap cannot see or dynamically instantiate classes/resources that Akka normally discovers through configuration and reflection.

## Root cause

The failure is metadata-related.

The provided Gradle excerpt shows `ConfigException$Missing` for keys such as `akka`, `akka.loggers`, and `akka.logging-filter`. The Codex metadata-fix log confirms this came from Akka default configuration not being visible to Typesafe Config in the native image. The relevant defaults are `reference.conf` and `version.conf` from the transitive `akka-actor_2.13` runtime.

After Codex added partial metadata, the failure advanced through a sequence of additional Akka bootstrap metadata gaps: reflective construction of config-named Akka components, scheduler initialization requiring reflective access to `sun.misc.Unsafe.theUnsafe`, DNS/router classes loaded from Akka `reference.conf`, mailbox requirements, guardian actors, and finally the latest recorded native failure:

```text
java.lang.IllegalArgumentException: no matching constructor found on class akka.event.DeadLetterListener for arguments []
```

This indicates the remaining unresolved metadata is a reflection registration for Akka's default dead-letter listener constructor, most likely in transitive `akka-actor_2.13` metadata rather than in the `akka-slf4j_2.13` test itself.

## Why Codex could not fully resolve it

Codex made iterative progress and kept uncovering new Akka actor-system bootstrap dynamic-access requirements. Each native rerun moved to a later missing config/reflection entry, but the metadata-fix process stopped before reaching a clean run. The remaining failure is therefore partial metadata coverage, not evidence that the generated tests are invalid.

No metadata files were modified during this intervention.

## Why generated support should be preserved

The generated support exercises real `akka-slf4j_2.13` behavior:

- SLF4J logger factory and marker wrappers already pass in native image.
- The failing tests cover normal Akka SLF4J integration paths that require an `ActorSystem`, logging filter, logger actor initialization, MDC formatting, and log-event handling.
- The failures expose missing native-image metadata for Akka's standard configuration and reflective bootstrap paths. Removing these tests would hide valid reachability metadata gaps in transitive Akka runtime support.

The generated tests should therefore be preserved, and the remaining work should continue by completing the missing Akka metadata rather than deleting the tests.
