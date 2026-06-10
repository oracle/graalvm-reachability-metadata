# Post-generation intervention report

Library: com.typesafe.play:play_3:2.9.7

Stage: `metadata_fix_failed`

## Summary

The generated native image compiled, but `:nativeTest` failed at runtime in one generated test:

- `com_typesafe_play.play_3.StatusHeaderTest > sendsResourceFromProvidedClassLoader()`

The test fails while consuming a Play `Result` body with an Akka `ActorSystem`. Akka dispatcher initialization throws `ExceptionInInitializerError`, caused by:

```text
java.lang.NoSuchFieldException: 0bitmap$1
  at java.lang.Class.getDeclaredField(...)
  at scala.runtime.LazyVals$.getOffset(LazyVals.scala:156)
  at akka.dispatch.MessageDispatcher.<clinit>(AbstractDispatcher.scala:66)
```

## Root cause

This is metadata-related, so the generated test was not removed.

The remaining missing metadata is reflection access for the Scala lazy-val bitmap field:

```text
akka.dispatch.MessageDispatcher#0bitmap$1
```

Codex advanced the metadata fix through several earlier Akka dynamic-access failures and added metadata for scheduler/provider/router/mailbox bootstrap classes. Its log shows the last isolated failure was also `akka.dispatch.MessageDispatcher` needing field `0bitmap$1`; Codex added a candidate entry but did not complete a final clean verification run. The Gradle failure output still reports the same missing field, which means the registration is still absent from the effective native-image metadata or is guarded by a condition that is not active for the failing `StatusHeaderTest` / Java `Results.ok().sendResource(...)` path.

Because execution stops at this missing field, there may be additional Akka dispatcher initialization metadata gaps behind it, but the only confirmed remaining failure is the `MessageDispatcher` lazy-val bitmap field registration.

## Intervention decision

No generated test files were removed. The failure is a native-image metadata gap rather than a test bug or unsupported platform feature: the test exercises normal Play/Akka result streaming behavior, and the native-image runtime fails specifically on reflective field lookup metadata.

## Why the remaining generated support should be preserved

The rest of the generated support is valid and already provides native coverage for Play reflection and resource-loading paths. In the failing run, 21 of 22 generated tests passed, including application loader instantiation, configured filters, handler invoker reflection, logger configuration, messages resources, method lookup helpers, module loading, bindables, resources, and Scala/Java result-resource checks that do not trigger the deeper Akka dispatcher path.

Preserving this support keeps the successfully validated Play metadata coverage while making the unresolved Akka `MessageDispatcher#0bitmap$1` metadata gap explicit for a follow-up metadata fix.
