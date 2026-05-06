# Post-generation intervention report

Library: org.apache.pekko:pekko-stream_2.13:1.1.5
Stage: metadata_fix_failed

## Summary

All nine generated `Pekko_stream_2_13Test` methods fail before their stream-specific assertions run. The native executable reaches `Pekko_stream_2_13Test.setUp`, but `ActorSystem("pekko-stream-test")` cannot complete Pekko actor-system bootstrap.

The Gradle excerpt shows the first shared failure mode:

- `com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'pekko'`

The suppressed `NullPointerException` in `tearDown` is secondary: setup failed before `system` was initialized.

## Root cause classification

This is metadata-related. The tests are not failing because of unsupported stream behavior or a test-only logic bug. They fail because the native image does not yet preserve all runtime resources and dynamically loaded Pekko classes needed by the Pekko actor/stream bootstrap path.

The Codex metadata-fix log shows a partial metadata repair sequence:

1. Pekko `reference.conf` / `version.conf` resources were missing or not visible to `ClassLoader.getResources("reference.conf")` in the native image. Conditional resource metadata embedded the files but did not make them visible to Typesafe Config; unconditional resource registration made the config visible.
2. After config loading was fixed, Pekko progressed to config-driven reflective bootstrapping and exposed additional missing metadata for classes named from `reference.conf`, including startup/logging/provider classes such as `org.apache.pekko.event.DefaultLoggingFilter`, `org.apache.pekko.event.Logging$DefaultLogger`, `org.apache.pekko.serialization.SerializationExtension$`, `org.apache.pekko.actor.LightArrayRevolverScheduler`, `org.apache.pekko.actor.LocalActorRefProvider`, `org.apache.pekko.actor.DefaultSupervisorStrategy`, and `org.apache.pekko.event.LoggerMailboxType`.
3. The next failure moved into `org.apache.pekko.util.Unsafe`, which required reflective access to `sun.misc.Unsafe.theUnsafe`.
4. The latest native test result shows the remaining unresolved gap is still config-driven class loading through `org.apache.pekko.actor.Deployer`: `org.apache.pekko.routing.RoundRobinPool` is missing from the image while parsing `/IO-DNS/async-dns`. Codex had already fixed the adjacent `org.apache.pekko.routing.ConsistentHashingPool` lookup, but stopped before finishing the remaining router/DNS bootstrap metadata.

## Remaining missing metadata

Codex did not complete the metadata-fix loop. The remaining known missing entry is for `org.apache.pekko.routing.RoundRobinPool`, loaded by name from Pekko actor `reference.conf` and instantiated by `org.apache.pekko.actor.Deployer` with a `com.typesafe.config.Config` constructor.

The Codex log also notes likely adjacent config-driven DNS provider lookups after the router registrations, including `org.apache.pekko.io.InetAddressDnsProvider` and `org.apache.pekko.io.AsyncDnsProvider`, but the run ended before those could be validated. These should be handled as metadata follow-up, not by deleting tests.

## Intervention decision

No generated tests were removed.

The failures are shared actor-system initialization failures caused by incomplete reachability metadata. Removing individual test methods would only hide the metadata problem; every generated stream test depends on the same Pekko bootstrap path and therefore exposes the same missing native-image support.

## Why the generated support should be preserved

The generated support exercises real Pekko Stream usage: finite sources, queue materialization, GraphDSL, `mapAsync`, recovery, `prefixAndTail`, batching, substreams, and kill switches. These are meaningful coverage points for `org.apache.pekko:pekko-stream_2.13:1.1.5` once actor-system bootstrap metadata is complete. Keeping the tests preserves useful runtime coverage and provides a concrete verification target for the remaining Pekko resource/reflection metadata fixes.
