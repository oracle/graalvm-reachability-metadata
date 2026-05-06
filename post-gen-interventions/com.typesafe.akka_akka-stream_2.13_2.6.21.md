# Post-generation intervention report

Library: com.typesafe.akka:akka-stream_2.13:2.6.21
Stage: metadata_fix_failed

## Summary

All 9 generated Akka Streams native tests failed while creating the shared `ActorSystem` in `startActorSystem()`. The Gradle failure shows the same exception for every test:

```text
com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'akka'
```

The failure happens before the individual stream operations run, so the root cause is common ActorSystem bootstrap rather than any one generated test method.

## Root cause

This is metadata-related. Akka loads its default configuration from bundled `reference.conf` resources and then uses that configuration to bootstrap actors, dispatchers, routers, mailboxes, serializers, DNS providers, and stream materializers. In the failing native image, the Akka configuration resources were not available at runtime, so `ActorSystem$Settings.amendSlf4jConfig` could not find the top-level `akka` config key.

The Codex metadata-fix log shows that Codex was still iterating through metadata gaps and did not reach a passing native test run. It added/identified resource metadata for `reference.conf` and `version.conf`, reflective access for `sun.misc.Unsafe.theUnsafe`, config-instantiated router constructors, `Props$EmptyActor`, Scala `MODULE$` singleton fields, DNS providers, and built-in serializers. After those partial fixes, the direct native run in the log still failed on another config-driven reflective class lookup:

```text
ClassNotFoundException: akka.dispatch.BoundedControlAwareMessageQueueSemantics
```

So Codex did not finish covering Akka's config-driven dynamic access. The Gradle excerpt's missing `akka` config indicates the remaining metadata problem includes resource availability/application for Akka's default HOCON configuration; the Codex log additionally shows unresolved reflective registration for mailbox requirement classes such as `akka.dispatch.BoundedControlAwareMessageQueueSemantics` and likely related `akka.dispatch.*MessageQueueSemantics` entries loaded from Akka's default config.

## Test disposition

No generated tests were removed. The failures are metadata failures, not native-image platform limitations, unsupported features, or test bugs.

## Why preserve the generated support

The generated test class exercises meaningful Akka Streams behavior: finite source/flow/sink materialization, GraphDSL broadcast/zip, source queues, supervision, `mapAsync`, framing, substreams, `prefixAndTail`, and kill switches. These tests all fail at the same shared ActorSystem initialization point, before their actual assertions execute. Once the missing resource and config-driven reflection metadata is completed, the existing generated tests should provide useful coverage for Akka Streams reachability metadata instead of being discarded.
