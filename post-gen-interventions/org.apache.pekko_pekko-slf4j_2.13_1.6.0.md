# Post-generation intervention report

Library: org.apache.pekko:pekko-slf4j_2.13:1.6.0

Stage: `metadata_fix_failed`

## Summary

The native test run still fails in the three generated tests that create a Pekko `ActorSystem`:

- `slf4jLoggingFilterMatchesUnderlyingSlf4jLoggerForPlainAndMarkedQueries()`
- `pekkoLoggingBusPublishesPlainAndMarkedEventsToConfiguredSlf4jLogger()`
- `actorCanUseSlf4jLoggingTrait()`

The Gradle output shows each failure occurs during `ActorSystem` startup with:

```text
com.typesafe.config.ConfigException$Missing: No configuration setting found for key 'pekko.logging-filter'
```

This is metadata-related, not a generated-test logic failure. The same tests passed on the JVM, but the native image does not see the Pekko default configuration needed by `ActorSystem.Settings.amendSlf4jConfig`.

## Metadata still missing or incomplete

Codex partially repaired the ActorSystem startup path by adding several reflection/resource entries, but it did not finish the metadata fix. The log shows a long sequence of native-only reachability gaps in Pekko actor bootstrap, including reflective construction of scheduler, actor provider, mailbox, routing, guardian, executor, dead-letter, and event-stream classes. The final logged unresolved gap was:

```text
java.lang.ClassNotFoundException: org.apache.pekko.serialization.SerializationExtension$
```

The later Gradle failure shows the remaining native configuration problem as the missing `pekko.logging-filter` setting. That points to incomplete resource/config reachability for Pekko's default `reference.conf` configuration in the native image, or an insufficient condition on the generated resource metadata. Because this is still part of native-image metadata/config reachability, the generated ActorSystem tests should not be removed.

## Why remaining generated support should be preserved

The generated tests cover valid `pekko-slf4j` behavior:

- direct SLF4J logger factory access,
- SLF4J marker wrapping,
- SLF4J logging filter behavior,
- publishing Pekko logging events to the configured SLF4J logger,
- use of the `SLF4JLogging` trait from an actor.

The non-ActorSystem tests already pass natively, and the failing ActorSystem tests expose real native reachability/configuration gaps rather than unsupported API use. Preserving the generated support keeps meaningful coverage for `pekko-slf4j` and documents the remaining metadata work needed to make the ActorSystem-backed scenarios pass.
