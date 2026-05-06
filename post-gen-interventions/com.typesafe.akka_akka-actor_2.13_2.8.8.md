# Post-generation intervention report

Library: com.typesafe.akka:akka-actor_2.13:2.8.8

Stage: `metadata_fix_failed`

## Summary

The native test run failed after metadata generation/fix work. The failures are metadata-related, not generated-test bugs or unsupported platform-feature tests, so no generated tests were removed.

The Gradle excerpt shows Akka `ActorSystem` startup failing before tests can exercise their target behavior because `reference.conf`-provided defaults such as `akka.loggers`/`akka` are absent from the native image. It also shows `LineNumbersTest` returning `NoSourceInfo`, which indicates class/lambda bytecode resources needed by `akka.util.LineNumbers` are not available in the image.

The Codex metadata-fix log confirms this was an incomplete metadata backfill from an initially empty `reachability-metadata.json`. Codex made several partial metadata iterations, moving the failures from missing Akka config resources to later missing reflective classes, but it did not reach a passing verification run.

## Root cause by failing area

- `DnsSettingsTest`, `JavaSerializerTest`, `ManifestInfoTest`, `TypedActorExtensionTest`, and `TypedActorInnerSerializedMethodCallTest` in the provided Gradle excerpt fail at `ActorSystemImpl.<init>` with `ConfigException$Missing` for `akka`/`akka.loggers`. This is missing native resource metadata for Akka configuration resources, especially `reference.conf` and included Akka config defaults. These are metadata gaps, so the tests should be preserved.
- `LineNumbersTest` fails with `NoSourceInfo` instead of `LineNumbers.SourceFileLines`. This is missing native resource/reflection support for class bytecode resources and the serializable-lambda path used by `akka.util.LineNumbers`, not a test bug. This is also metadata-related.
- Later Codex reruns in `codex.log` show the config resource issue was partially fixed and exposed subsequent metadata gaps: reflective class loading for `akka.event.DefaultLoggingFilter`, `akka.actor.LightArrayRevolverScheduler`, `akka.actor.LocalActorRefProvider`, and Akka's `sun.misc.Unsafe` bootstrap, plus Java serialization metadata for the object/string/method-call paths. These are all dynamic-access or resource/serialization metadata gaps.

## Why Codex could not resolve it

Codex was still iterating through a chain of missing metadata from an empty Akka metadata file. Each patch exposed the next config-driven or reflection-driven Akka startup requirement. The log ends with the workflow still in progress after adding metadata for `akka.actor.LocalActorRefProvider`; there is no final successful `./gradlew test -Pcoordinates=com.typesafe.akka:akka-actor_2.13:2.8.8` run.

## Intervention decision

No tests were removed. The failures are metadata-related and should be fixed by completing the metadata backfill rather than by deleting coverage.

## Why the remaining generated support should be preserved

The generated support exercises real Akka native-image reachability behavior: config resource loading, reflective actor-system startup classes, Java serialization, typed-actor proxying, DNS fallback discovery, manifest scanning, direct-buffer cleanup, and line-number source lookup. These are valuable coverage areas for `akka-actor_2.13`; preserving them keeps the metadata requirements visible and prevents accepting incomplete native support.