# Post-generation intervention report

Library: com.typesafe.akka:akka-stream_2.13:2.6.21
Stage: metadata_fix_failed

## Summary

All 12 generated `Akka_stream_2_13Test` native-image tests failed while creating an `ActorSystem`. The Gradle excerpt reports `com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'akka'`; the persisted native test XML also shows the generated `com_typesafe_akka/akka_stream_2_13/application.conf` resource was not available in the native image.

## Root cause

The failures are metadata-related, not test bugs or unsupported Akka Stream behavior. Akka requires its `application.conf`/merged `reference.conf` settings at runtime. The generated test support creates a merged config resource and loads it explicitly, but the native image still does not include that nested resource. The current generated metadata only registers `*.conf` conditionally, which does not preserve `com_typesafe_akka/akka_stream_2_13/application.conf` in the native image. As a result, every test reaches the same configuration-loading failure before exercising its stream scenario.

The requested Codex log path, `logs/com.typesafe.akka:akka-stream_2.13:2.6.21/metadata-fix/codex.log`, was not present in this checkout, so the determination is based on the Gradle failure output and the generated test/metadata artifacts. From those artifacts, Codex appears to have partially addressed the issue by generating an Akka config resource and a test-only native-image resource config, but the repository metadata still lacks the needed resource inclusion for the nested generated config path.

## Intervention decision

No generated tests were removed. The remaining failure is caused by incomplete resource metadata, so removing tests would hide a valid reachability metadata gap rather than eliminate an invalid test.

## Why preserve the generated support

The generated tests cover representative Akka Streams functionality: bounded transformations, graph DSL, supervision, framing, compression, queues, kill switches, substreams, async recovery, restart sources, stream converters, and file IO. These are useful coverage for `com.typesafe.akka:akka-stream_2.13:2.6.21` once the missing config resource metadata is fixed. The generated build support also documents the runtime configuration resource Akka needs in native images, so it should be preserved as evidence for the required metadata change.
