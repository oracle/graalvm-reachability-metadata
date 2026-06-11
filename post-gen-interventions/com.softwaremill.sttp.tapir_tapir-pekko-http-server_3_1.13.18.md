# Post-generation intervention report

Library: com.softwaremill.sttp.tapir:tapir-pekko-http-server_3:1.13.18
Stage: metadata_fix_failed

## Summary

All five generated native tests fail while constructing the Apache Pekko `ActorSystem` used by the Tapir Pekko HTTP server test harness. The Gradle excerpt shows the first shared failure as `com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'pekko'`, which happens before any endpoint-specific assertions run.

## Root cause

The failures are metadata-related, not test bugs or unsupported platform features. Pekko loads its default configuration and several bootstrap classes dynamically during `ActorSystem` startup; without the required Native Image resources and dynamic-access metadata, the native executable cannot see Pekko's `reference.conf` configuration entries and later cannot resolve dynamically loaded Pekko classes.

The Codex metadata-fix log confirms this was only partially repaired. Codex added Pekko configuration resources and several reflection entries, but the subsequent native test still failed in the same shared startup path with `java.lang.RuntimeException: While trying to load extension [org.apache.pekko.serialization.SerializationExtension$]`, caused by `java.lang.ClassNotFoundException: org.apache.pekko.serialization.SerializationExtension$` from Pekko `ReflectiveDynamicAccess`. That remaining class-loading failure indicates missing dynamic-access metadata for Pekko's serialization extension and possibly adjacent Pekko extension bootstrap classes.

## Test disposition

No generated test was removed. Each failing test exercises real Tapir Pekko HTTP server behavior through `PekkoHttpServerInterpreter` and fails at the common native-image metadata boundary before the generated endpoint logic can complete.

## Why preserve the generated support

The generated support should be preserved because it already provides meaningful coverage for GET routing with path/query/header handling, POST request-body routing, error outputs, bearer-token security logic, and dispatch among multiple Tapir server endpoints. These tests are valuable once the remaining Pekko bootstrap metadata is completed, and removing them would hide a real reachability gap in a dependency path required by this library.
