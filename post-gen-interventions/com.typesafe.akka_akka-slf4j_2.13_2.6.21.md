# Post-generation intervention report

Library: com.typesafe.akka:akka-slf4j_2.13:2.6.21
Stage: metadata_fix_failed

## Summary

The generated native test image builds, but the native test run fails. Five ActorSystem-based tests fail during Akka startup; the direct SLF4J diagnostic logger test succeeds.

The Gradle failure excerpt shows all five failing tests stop with:

```text
com.typesafe.config.ConfigException$Missing: No configuration setting found for key 'akka.version'
```

The Codex metadata-fix log confirms this is metadata-related, not a test bug or unsupported native-image feature. The native runtime initially could not load Akka's `reference.conf`/`version.conf` resources through the classloader, and after partial resource/reflection fixes Codex continued uncovering Akka config-driven reflective class-loading gaps.

## Root cause by failed test

- `systemLoggingAdapterPublishesMessagesToConfiguredSlf4jBackend()` failed while creating an `ActorSystem`; Akka could not read its default/version configuration in the native image.
- `diagnosticActorLoggingPublishesMappedDiagnosticContext()` failed for the same `ActorSystem` startup metadata gap.
- `actorLoggingIsRoutedThroughConfiguredSlf4jLogger()` failed for the same `ActorSystem` startup metadata gap.
- `configuredSlf4jLoggerHandlesErrorsWithoutStoppingTheActor()` failed for the same `ActorSystem` startup metadata gap.
- `loggingAdapterAcceptsSlf4jMarkersForMarkedEvents()` failed for the same `ActorSystem` startup metadata gap.

## Metadata still missing

Codex partially addressed the failure by adding Akka root config resources and several reflection entries for config-instantiated Akka classes, but it did not complete the iterative metadata-fix loop. The last unresolved failure in the Codex log is still dynamic-access metadata:

```text
akka.ConfigurationException: Type [akka.dispatch.BoundedControlAwareMessageQueueSemantics]
specified as akka.actor.mailbox.requirement [akka.actor.mailbox.bounded-control-aware-queue-based]
in config can't be loaded
Caused by: java.lang.ClassNotFoundException: akka.dispatch.BoundedControlAwareMessageQueueSemantics
```

This indicates that Akka's `reference.conf` mailbox requirements still reference classes/interfaces that need to be available for native-image dynamic class lookup. The log also shows a sequence of earlier missing config-driven Akka classes (`LightArrayRevolverScheduler`, `LocalActorRefProvider`, router pools, mailbox/logger/DNS classes, and `Props$EmptyActor`), so the remaining failure is part of the same incomplete metadata chain rather than an invalid generated test.

## Intervention decision

No generated tests were removed. The failures are metadata-related and should remain visible for a future metadata fix.

## Why preserve the generated support

The generated tests provide meaningful coverage for `akka-slf4j_2.13`: direct SLF4J logging, Akka `LoggingAdapter`, actor logging, diagnostic MDC logging, error logging, and SLF4J markers. The one non-ActorSystem test already passes, and the failing tests expose real native-image resource/reflection/class-lookup metadata gaps in Akka startup. Keeping the support preserves useful regression coverage once the remaining Akka configuration and dynamic-access metadata is completed.
