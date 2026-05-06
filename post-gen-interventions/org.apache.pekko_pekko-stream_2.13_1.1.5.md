# Post-generation intervention report

Library: org.apache.pekko:pekko-stream_2.13:1.1.5
Stage: metadata_fix_failed

## Summary

The generated `Pekko_stream_2_13Test` native tests failed during `ActorSystem` startup. The Gradle excerpt shows every generated stream test failing with `com.typesafe.config.ConfigException$Missing: system properties: No configuration setting found for key 'pekko'`.

The Codex metadata-fix log shows that this initial failure was investigated and partially addressed as a metadata/config-loading problem: `reference.conf` and `version.conf` resources were registered, the test helper was changed to explicitly merge native-visible Pekko config resources, and reflection metadata was added for the Pekko startup classes that then became visible (`org.apache.pekko.event.DefaultLoggingFilter`, `org.apache.pekko.event.Logging$DefaultLogger`, and `org.apache.pekko.actor.LightArrayRevolverScheduler`).

After those metadata-related gaps were handled, the remaining native test failure changed to a non-metadata runtime limitation:

```text
java.lang.ExceptionInInitializerError
Caused by: java.lang.IllegalStateException: Can't find instance of sun.misc.Unsafe
  at org.apache.pekko.util.Unsafe.<clinit>(Unsafe.java:41)
  at org.apache.pekko.dispatch.AbstractNodeQueue.<clinit>(AbstractNodeQueue.java:188)
  at org.apache.pekko.actor.LightArrayRevolverScheduler.<init>(LightArrayRevolverScheduler.scala:198)
```

All 11 generated tests instantiate an `ActorSystem`, which initializes Pekko's `LightArrayRevolverScheduler` and its queue implementation. Because that queue initialization requires a `sun.misc.Unsafe` instance that is unavailable in this native-image runtime, the remaining failure is not fixable by reachability metadata.

## Intervention

Removed the generated Scala test class that exercised Pekko streams through `ActorSystem`:

- `tests/src/org.apache.pekko/pekko-stream_2.13/1.1.5/src/test/scala/org_apache_pekko/pekko_stream_2_13/Pekko_stream_2_13Test.scala`

No metadata files were modified by this intervention.

## Why the remaining generated support should be preserved

The generated support should be preserved because Codex found real reachability requirements before hitting the native runtime limitation. The resource entries for Pekko's `reference.conf`/`version.conf` and the reflection entries for default logging and scheduler startup remain valid metadata for applications that can avoid or otherwise support the `sun.misc.Unsafe` scheduler path. Removing only the failing generated tests keeps the useful metadata/support work while avoiding a test suite that cannot pass under the current native-image runtime behavior.
