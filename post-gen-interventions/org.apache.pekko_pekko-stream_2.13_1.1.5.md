# Post-generation intervention report

Library: org.apache.pekko:pekko-stream_2.13:1.1.5
Stage: metadata_fix_failed

## Summary

The generated native test support was not removed. The Gradle failure is metadata-related: all generated `Pekko_stream_2_13Test` native tests fail while constructing a `ActorSystem`, before the individual stream assertions run.

The failure excerpt shows `com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'pekko'` from `ActorSystem$Settings$.amendSlf4jConfig`. That means the native image does not make Pekko's `reference.conf` configuration visible early enough for Typesafe Config to build the default `pekko` configuration subtree.

## Root cause classification

This is a missing reachability metadata failure, not a bad test and not an unsupported native-image feature.

Every failing test uses the same helper that creates `ActorSystem(name)`. The native executable fails during that common actor-system bootstrap path, so the stream operations under test are never reached. The Codex metadata-fix log confirms the failure chain is reachability metadata work: Codex first investigated `reference.conf`/`version.conf` resource visibility, then progressed through additional config-driven reflective loads such as `DefaultLoggingFilter`, `LightArrayRevolverScheduler`, `LocalActorRefProvider`, DNS/router classes, `Props$EmptyActor`, and finally Pekko dispatch/mailbox semantics and mailbox classes referenced from `reference.conf`.

Codex could not resolve the issue because Pekko actor-system startup exposes a long sequence of default-config-driven metadata requirements. The remaining missing metadata is still in that startup cluster, especially the dispatch/mailbox classes referenced by Pekko's default `reference.conf`; no successful final verification run appears after Codex began batching those entries.

## Why the generated support should be preserved

The generated test suite covers meaningful `pekko-stream_2.13` behavior: finite `Source`/`Flow`/`Sink` materialization, GraphDSL broadcast wiring, framing, queue sources, resource unfolding, stream converters, gzip compression, asynchronous stages, recovery, and `prefixAndTail`. JVM compilation and JVM test execution had already succeeded, and the native failures occur uniformly at shared `ActorSystem` initialization.

Because the failures identify missing native-image metadata for Pekko configuration and reflective actor-system bootstrap classes, removing the generated tests would discard valid coverage instead of fixing the metadata gap. The remaining generated support should be kept so the metadata can be completed and verified against real Pekko stream usage.
