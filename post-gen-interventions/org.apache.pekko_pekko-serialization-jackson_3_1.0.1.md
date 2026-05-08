# Post-generation intervention report

Library: org.apache.pekko:pekko-serialization-jackson_3:1.0.1

Stage: `metadata_fix_failed`

## Summary

The native test run failed before any Pekko Jackson serialization assertion could execute. All eight generated tests create an `ActorSystem`, and ActorSystem startup failed with:

`com.typesafe.config.ConfigException$Missing: No configuration setting found for key 'pekko.loggers'`

The missing `pekko.loggers` key is normally supplied by Pekko's `reference.conf`. In the native image, that resource was not available to Typesafe Config, so `ConfigFactory.load()` could not merge Pekko's defaults into the inline test configuration.

## Root cause

This is metadata-related, not a bad generated test or unsupported platform feature. The generated tests are valid coverage for `pekko-serialization-jackson_3`, but Pekko requires native-image resource metadata for its configuration files before the `ActorSystem` can start.

The Codex metadata-fix log confirms that Codex identified the first blocker as missing resource metadata for Pekko config. After adding resource metadata for `reference.conf` and `version.conf`, the run progressed past `pekko.loggers`, which shows the original failure was a metadata/resource issue rather than a test logic issue.

Codex then exposed additional runtime metadata gaps during Pekko bootstrap and did not complete the full fix/verify loop. The latest inspected native result still fails during `ActorSystem` initialization because Pekko reflectively loads router classes from config; specifically, `org.apache.pekko.routing.RoundRobinPool` is not reachable in the native image when resolving `/IO-DNS/async-dns` deployment configuration. Earlier iterations in the log also show required reflective construction for Pekko bootstrap types such as `org.apache.pekko.actor.LocalActorRefProvider` and router config classes.

## Decision

No generated tests were removed.

The failures are caused by incomplete reachability metadata for Pekko configuration resources and reflective Pekko bootstrap/router classes, not by individual test behavior. Removing any one test would not address the underlying problem because all tests fail at the shared `ActorSystem` startup path.

## Why the generated support should be preserved

The generated support exercises meaningful public behavior of `pekko-serialization-jackson_3`: JSON and CBOR serializers, compression settings, manifest/deserialization configuration, Jackson migration, `JacksonObjectMapperProvider`, Scala/Pekko data types, and classic actor references. Once the remaining metadata gaps are completed, these tests should provide useful native-image coverage for the library. Keeping the tests preserves that coverage and avoids hiding a real metadata requirement.
